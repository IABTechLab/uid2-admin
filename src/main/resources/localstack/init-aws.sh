#!/usr/bin/env bash

aws s3 --endpoint-url http://localhost:5001 mb s3://test-core-bucket
aws s3 --endpoint-url http://localhost:5001 cp /s3/ s3://test-core-bucket/ --recursive