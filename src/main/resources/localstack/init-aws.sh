#!/bin/bash

aws s3 --endpoint-url http://localhost:4566 mb s3://test-core-bucket
aws s3 --endpoint-url http://localhost:4566 cp /s3/ s3://test-core-bucket/ --recursive