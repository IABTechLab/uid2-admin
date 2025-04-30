
# Salt Compare Tool

A lightweight Python script to compare encrypted and unencrypted salt files stored in an S3 bucket.

## Description

This script fetches two S3 objects (e.g. encrypted and unencrypted salts) and compares them. It validates basic input rules like prefix formatting and key pattern before proceeding.

## Usage

### Run the script:

Login to AWS account
`pip install requirements.txt` 
`python script.py <encrypted_file> <bucket> <region_name> [prefix]` 

-   `encrypted_file` – Required. Must start with `salt`. Example: `salts/encrypted/12_private/salts.txt.1745532777048` (To query multiple files you can use `salts/encrypted/12_private/*`)
-   `bucket` – Required. Name of the S3 bucket. 
-   `region_name` – Required. AWS region of the S3 bucket (e.g. `us-east-1`) 
-   `prefix` – Optional. S3 path prefix. If provided, it **must end with `/`**.

## For Other Decryption Comparisons

You can use the **same logic** for other types of decryption and comparison. The only change is in how the **unencrypted file name** is generated in salt_compare.
