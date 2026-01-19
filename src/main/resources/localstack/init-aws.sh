#!/usr/bin/env bash
set -e

echo "Starting Core LocalStack initialization..."

# Create S3 bucket
echo "Creating S3 bucket..."
awslocal s3 mb s3://test-core-bucket || echo "Bucket may already exist"

# Copy core data to S3
echo "Copying core data to S3..."
awslocal s3 cp /s3/core/ s3://test-core-bucket/ --recursive

# Create KMS key with custom ID and custom key material
# Using _custom_id_ and _custom_key_material_ tags per https://docs.localstack.cloud/aws/services/kms/
# The key material is the RSA private key (PKCS#8 DER format, base64 encoded)
echo "Creating KMS key with custom ID and key material..."
PRIVATE_KEY_B64="MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCa/AHjWojkV7jU8Ntepxfm469K98qHyX1BXQ7cz42wHiUqpAQ/S3WF+iJdOk6ArPUCtjEexDYt5eJ9fi7ARtgkWWlUz63JCRNZCME7Dp+wtgrZeThfbKU1dRR/vHdIOI5XHK9OHc5lb2SqsME30nFKito0vJ/DSGFbRIel+zr31J6GBtDtBZ6n+BWUpEsjPRcBdpk3Dbizv05FxCWsITpgPQ+BCakj90rnEwwvzafrLepxOXLCUZpTs4Ygx0P4JNcDcFw6SBd6plNc1pfW7qMJNrWW8BzO6fpq+nlVnhMWK4+j7LisncZT7XhzJPk1yQRxpMpK93zpR3Arkh322XdtAgMBAAECggEAC+C3Hv7X8Z1szkUMoEXGEIKanfA9AV3Gel/wvP4wfwg7E6LbqyN+r58/9aJ7qbjs/iGLGi8yHR+6f/ZPtu9hrpzQ9G2w2ptrdC4Llm8Z0Kfi+k/Oq4w0DSjFQr+QP2S2OU7lezh656M7NSm0D9x8+kLcqPYGeHzvmS24slZ9anODymADxcicF2V1LHrl1I4CpUJarAO19tX+OXq86bB28fAdC1++33r1ERC2uZrTGIyjMN6t2DMX98MYg4QHfNBArPP3rwoOvtSa9fssnqOVGhqGysDrVcfycmdfj2PuGisPBMU0Wk85lRzyjMbzFS9q8BdVwtjGH9htHT28MMWugQKBgQDYaoqH/dm59qOtNvbwNlPYEiHMdjQpoLFBwrxHQD9hYjXdu9leRjEdR78s0kC23zDQzsQ4rpIj1glO9LwZUSUlWtRRkLZ/8d1DvJERUQGFlHBLpgB8ikapSnijo4zT1Jw6s348YSEqyh1McTsno+zL2Fra8vvI4YwsIUphUtKhiQKBgQC3VP0GYQzxzvLbqSzufc6UMTM7Vk4kluWPORxWnk4kKv8owgW0LHHhtiOQjRxMakLFW2nxfI9oWIoOmoRAbJFSQFQKglX4IExVbHI+3s5Gas3X+AS5ANoUdMBBrUSvAkyamv8LTfRsj8ztVGgXw51JAHhS/uVuaLbeFdLpOsyhxQKBgQCYIrWGCi8f6sF/SA9qKFbio0R9Tm83AE77sqDW2dR0ai0B1kdlXaSzN7euE5QIune/oksQqa/0X0el6Ke+iGu7idGOEVQqN2Xbc1jrum1+cS5MD8NxyWcJJWAPcS7TzzeQkJPicEl3oiPclBEIudUCK/MazguwWNZIQ5LdPfLyOQKBgF+GZSDByODmGBzklYje/Jiy2iL84VKnXY23EFEBw22NCc7O6fHrhps5MGbNYAVhCNGUxCsT4BVarPTXBjobV80nv6KKLwlOqveHvi+MIKcIV6FElhFfpEIsY1DVW4hlBk04ndPiFo3Kj9jJtkNLpdS37fow3pMc9MvbSz5DaQSRAoGATiUvoT5mQB98RtJT3Up475/7DTBJVpzXPHbxF0BCSYgutKv6aXXgEFO680Lu7TVNKDbBJIJXPIas2y4uYdNJLcaqO3kx1JhTHTxRokTBVH3vyiFWKMGXZ0UYXBpeQoWNezxLJea8Cp3sEgbw+jaBuRm76Xvsh0JZ5MDgy26hFVs="

awslocal kms create-key \
    --key-usage SIGN_VERIFY \
    --key-spec RSA_2048 \
    --description "RSA key for JWT signing" \
    --tags "[{\"TagKey\":\"_custom_id_\",\"TagValue\":\"ff275b92-0def-4dfc-b0f6-87c96b26c6c7\"},{\"TagKey\":\"_custom_key_material_\",\"TagValue\":\"${PRIVATE_KEY_B64}\"}]"

echo "Verifying KMS key..."
awslocal kms describe-key --key-id ff275b92-0def-4dfc-b0f6-87c96b26c6c7

echo "Core LocalStack initialization complete."
