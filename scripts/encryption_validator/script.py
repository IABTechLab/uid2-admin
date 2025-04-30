import json
import base64
from typing import IO
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend
import boto3
import sys

class AesGcm:
    @staticmethod
    def decrypt(encrypted_data: bytes, nonce: bytes, key: bytes):
        if len(nonce) != 12:
            raise ValueError("Nonce must be 12 bytes for AES-GCM")
        cipher = Cipher(algorithms.AES(key), modes.GCM(nonce), backend=default_backend())
        decryptor = cipher.decryptor()
        try:
            return decryptor.update(encrypted_data) + decryptor.finalize()
        except Exception:
            raise ValueError("Invalid GCM tag during decryption")
        
def _get_encryption_secret(key_id, bucket, prefix, region_name):
    print("Fetching secret key for ", key_id)
    s3 = boto3.client('s3', region_name=region_name)
    response = s3.get_object(Bucket=bucket, Key=f"{prefix}cloud_encryption_keys/cloud_encryption_keys.json")
    data = json.load(response['Body'])
    _map = {item['id']: item for item in data}
    return _map.get(key_id).get('secret')

def _decrypt_input_stream(input_stream: IO[bytes], bucket, prefix, region_name) -> str:
    try:
        data = json.load(input_stream)
    except json.JSONDecodeError as e:
        raise ValueError(f"Failed to parse JSON: {e}")
    key_id = data.get("key_id")
    encrypted_payload_b64 = data.get("encrypted_payload")
    if key_id is None or encrypted_payload_b64 is None:
        raise ValueError("Failed to parse JSON")
    
    decryption_key = _get_encryption_secret(key_id, bucket, prefix, region_name)
    try:
        secret_bytes = base64.b64decode(decryption_key)
        encrypted_bytes = base64.b64decode(encrypted_payload_b64)
        nonce = encrypted_bytes[:12]
        ciphertext = encrypted_bytes[12:-16]
        auth_tag = encrypted_bytes[-16:]
        cipher = Cipher(algorithms.AES(secret_bytes), modes.GCM(nonce, auth_tag), backend=default_backend())
        decryptor = cipher.decryptor()
        decrypted_bytes = decryptor.update(ciphertext) + decryptor.finalize()
        return decrypted_bytes.decode("utf-8")
    except Exception as e:
        raise ValueError(f"An error occurred during decryption: {e}")
    
def salt_compare(key, prefix, bucket, region_name):
    s3 = boto3.client('s3', region_name=region_name)
    key = f"{prefix}{key}"
    print("Key is ", key)
    base_path = '/'.join(key.split('/')[:-3])
    file_name = key.split('/')[-1:][0]
    unencrypted = f'{base_path}/{file_name}'
    print(f"Comparing {key} with {unencrypted}")
    response = s3.get_object(Bucket=bucket, Key=key)
    encrypted = _decrypt_input_stream(response['Body'], bucket=bucket, prefix=prefix, region_name=region_name)
    response = s3.get_object(Bucket=bucket, Key=unencrypted)
    unencrypted = response['Body'].read().decode('utf-8')
    return (encrypted==unencrypted)

def _get_most_recent_files(bucket, prefix, key):
    s3 = boto3.client("s3")
    paginator = s3.get_paginator("list_objects_v2")
    page_iterator = paginator.paginate(Bucket=bucket, Prefix=f"{prefix}{key[:-2]}/")
    n, all_files = 5 , []
    for i, page in enumerate(page_iterator):
        if i >= n:
            break
        all_files.extend(page.get("Contents", []))
    recent_files = sorted(all_files, key=lambda x: x["LastModified"], reverse=True)
    recent_files = list(map(lambda x: x['Key'], recent_files))
    recent_files = list(filter(lambda x: "metadata" not in x, recent_files))
    return recent_files[:10]

if __name__ == '__main__':
    encrypted_file = sys.argv[1]
    bucket = sys.argv[2]
    region_name = sys.argv[3]
    prefix = sys.argv[4] if len(sys.argv) > 4 else ''
    if prefix != '' and prefix[-1]!='/':
        raise "prefix should terminate with /"
    if not encrypted_file.startswith("salt"):
        raise "only salts supported"
    if encrypted_file[-2:] == '/*':
        for recent in _get_most_recent_files(bucket=bucket, prefix=prefix, key=encrypted_file):
            print(salt_compare(key=recent, prefix=prefix, bucket=bucket, region_name=region_name))
    else:
        print(salt_compare(key=encrypted_file, prefix=prefix, bucket=bucket, region_name=region_name))
