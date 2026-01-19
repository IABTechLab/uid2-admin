#!/usr/bin/env bash
set -e

echo "Starting Core LocalStack initialization..."

# Create S3 bucket
echo "Creating S3 bucket..."
awslocal s3 mb s3://test-core-bucket || echo "Bucket may already exist"

# Copy core data to S3
echo "Copying core data to S3..."
awslocal s3 cp /s3/core/ s3://test-core-bucket/ --recursive

# Create KMS key with custom ID for JWT signing
# Using _custom_id_ tag as per https://docs.localstack.cloud/aws/services/kms/
echo "Creating KMS key with custom ID for JWT signing..."
awslocal kms create-key \
    --key-usage SIGN_VERIFY \
    --key-spec RSA_2048 \
    --description "RSA key for JWT signing" \
    --tags '[{"TagKey":"_custom_id_","TagValue":"ff275b92-0def-4dfc-b0f6-87c96b26c6c7"}]'

echo "Verifying KMS key..."
awslocal kms describe-key --key-id ff275b92-0def-4dfc-b0f6-87c96b26c6c7

echo "Core LocalStack initialization complete."
