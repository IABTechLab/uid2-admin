#!/usr/bin/env bash
set -e

# Create S3 bucket and copy core data
awslocal s3 mb s3://test-core-bucket || true
awslocal s3 cp /s3/core/ s3://test-core-bucket/ --recursive

# Create KMS RSA key for JWT signing with custom ID
# See: https://docs.localstack.cloud/aws/services/kms/
awslocal kms create-key \
    --key-usage SIGN_VERIFY \
    --key-spec RSA_2048 \
    --tags '[{"TagKey":"_custom_id_","TagValue":"ff275b92-0def-4dfc-b0f6-87c96b26c6c7"}]'

echo "Core LocalStack initialization complete."
