#!/usr/bin/env bash
set -e

echo "Starting Core LocalStack initialization..."

# Create S3 bucket
echo "Creating S3 bucket..."
awslocal s3 mb s3://test-core-bucket || echo "Bucket may already exist"

# Copy core data to S3
echo "Copying core data to S3..."
awslocal s3 cp /s3/core/ s3://test-core-bucket/ --recursive

# Create KMS RSA key with custom ID for JWT signing
# Using _custom_id_ tag per https://docs.localstack.cloud/aws/services/kms/
# Note: _custom_key_material_ tag only works for symmetric keys, not RSA keys.
# LocalStack will generate its own RSA key material. The e2e tests dynamically
# fetch the public key from KMS using GetPublicKey API for JWT validation.
echo "Creating KMS RSA key with custom ID..."

awslocal kms create-key \
    --key-usage SIGN_VERIFY \
    --key-spec RSA_2048 \
    --description "RSA key for JWT signing" \
    --tags '[{"TagKey":"_custom_id_","TagValue":"ff275b92-0def-4dfc-b0f6-87c96b26c6c7"}]'

echo "Verifying KMS key..."
awslocal kms describe-key --key-id ff275b92-0def-4dfc-b0f6-87c96b26c6c7

# Test that Sign operation works
echo "Testing KMS Sign operation..."
echo -n "test" | base64 > /tmp/test_message.b64
awslocal kms sign \
    --key-id ff275b92-0def-4dfc-b0f6-87c96b26c6c7 \
    --message fileb:///tmp/test_message.b64 \
    --message-type RAW \
    --signing-algorithm RSASSA_PKCS1_V1_5_SHA_256 && echo "KMS Sign test successful!" || echo "KMS Sign test FAILED!"

echo "Core LocalStack initialization complete."
