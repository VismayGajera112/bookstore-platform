#!/usr/bin/env bash
# Runs inside LocalStack once the emulator is ready (mounted at /etc/localstack/init/ready.d/).
# Creates the S3 bucket (+ lifecycle), DynamoDB tables (+ TTL), SNS topic, SQS DLQ, and prints ARNs.
set -euo pipefail

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
ENDPOINT=http://localhost:4566
awslocal() { aws --endpoint-url="$ENDPOINT" "$@"; }

echo "[localstack-init] Creating bookstore-covers bucket..."
awslocal s3 mb s3://bookstore-covers 2>/dev/null || true

# Allow anonymous GET so the Angular app can load covers in <img src> without signed URLs.
awslocal s3api put-bucket-acl --bucket bookstore-covers --acl public-read 2>/dev/null || true
awslocal s3api put-public-access-block \
  --bucket bookstore-covers \
  --public-access-block-configuration \
  "BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false" \
  2>/dev/null || true
awslocal s3api put-bucket-policy --bucket bookstore-covers --policy '{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "PublicReadCovers",
    "Effect": "Allow",
    "Principal": "*",
    "Action": ["s3:GetObject"],
    "Resource": ["arn:aws:s3:::bookstore-covers/*"]
  }]
}' 2>/dev/null || true

# Cost optimization: expire noncurrent / incomplete multipart uploads; transition nothing locally.
# In real AWS this same lifecycle rule drops old cover versions after 90 days.
awslocal s3api put-bucket-lifecycle-configuration \
  --bucket bookstore-covers \
  --lifecycle-configuration '{
    "Rules": [
      {
        "ID": "expire-old-covers",
        "Status": "Enabled",
        "Filter": { "Prefix": "covers/" },
        "Expiration": { "Days": 90 },
        "AbortIncompleteMultipartUpload": { "DaysAfterInitiation": 7 }
      }
    ]
  }'

echo "[localstack-init] Creating CoverMetadata table..."
awslocal dynamodb create-table \
  --table-name CoverMetadata \
  --attribute-definitions AttributeName=bookId,AttributeType=N \
  --key-schema AttributeName=bookId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST 2>/dev/null || true

echo "[localstack-init] Creating UserBrowsingHistory table..."
awslocal dynamodb create-table \
  --table-name UserBrowsingHistory \
  --attribute-definitions \
      AttributeName=userId,AttributeType=S \
      AttributeName=viewedAt,AttributeType=S \
  --key-schema \
      AttributeName=userId,KeyType=HASH \
      AttributeName=viewedAt,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST 2>/dev/null || true

# TTL auto-deletes history older than ~30 days (attribute written by book-service).
awslocal dynamodb update-time-to-live \
  --table-name UserBrowsingHistory \
  --time-to-live-specification "Enabled=true, AttributeName=expiresAt" 2>/dev/null || true

echo "[localstack-init] Creating SNS topic + email subscription stub..."
TOPIC_ARN=$(awslocal sns create-topic --name cover-processed --query TopicArn --output text)
# Email subscriptions stay PendingConfirmation in LocalStack; useful to inspect publish calls.
awslocal sns subscribe \
  --topic-arn "$TOPIC_ARN" \
  --protocol email \
  --notification-endpoint admin@bookstore.local >/dev/null || true

echo "[localstack-init] Creating Lambda DLQ (SQS)..."
awslocal sqs create-queue --queue-name cover-processor-dlq 2>/dev/null || true
DLQ_URL=$(awslocal sqs get-queue-url --queue-name cover-processor-dlq --query QueueUrl --output text)
DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn \
  --query 'Attributes.QueueArn' --output text)

echo "[localstack-init] Done."
echo "  COVER_BUCKET=bookstore-covers"
echo "  COVER_METADATA_TABLE=CoverMetadata"
echo "  BROWSING_HISTORY_TABLE=UserBrowsingHistory"
echo "  COVER_PROCESSED_TOPIC_ARN=$TOPIC_ARN"
echo "  COVER_PROCESSOR_DLQ_ARN=$DLQ_ARN"
echo "  (Deploy cover-processor jar + S3 notification separately; see docs/step-9-file-processing.md)"
