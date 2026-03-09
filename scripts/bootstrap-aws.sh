#!/usr/bin/env bash
set -euo pipefail

AWS_REGION="${AWS_REGION:-ap-south-1}"
DDB_TABLE="${DDB_TABLE:-iot-sensor-events}"
SNS_TOPIC_NAME="${SNS_TOPIC_NAME:-iot-alerts}"
SQS_QUEUE_NAME="${SQS_QUEUE_NAME:-iot-events.fifo}"
SQS_DLQ_NAME="${SQS_DLQ_NAME:-iot-events-dlq.fifo}"

echo "Creating DynamoDB table: ${DDB_TABLE}"
aws dynamodb create-table \
  --table-name "${DDB_TABLE}" \
  --attribute-definitions AttributeName=deviceId,AttributeType=S AttributeName=timestamp,AttributeType=S \
  --key-schema AttributeName=deviceId,KeyType=HASH AttributeName=timestamp,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --region "${AWS_REGION}" || true

echo "Creating SNS topic: ${SNS_TOPIC_NAME}"
aws sns create-topic --name "${SNS_TOPIC_NAME}" --region "${AWS_REGION}" >/dev/null

echo "Creating SQS DLQ (FIFO): ${SQS_DLQ_NAME}"
aws sqs create-queue \
  --queue-name "${SQS_DLQ_NAME}" \
  --attributes FifoQueue=true,ContentBasedDeduplication=true \
  --region "${AWS_REGION}" >/dev/null

DLQ_ARN=$(aws sqs get-queue-attributes --queue-url "$(aws sqs get-queue-url --queue-name "${SQS_DLQ_NAME}" --region "${AWS_REGION}" --output text --query QueueUrl)" --attribute-names QueueArn --region "${AWS_REGION}" --output text --query 'Attributes.QueueArn')

echo "Creating SQS queue (FIFO): ${SQS_QUEUE_NAME}"
aws sqs create-queue \
  --queue-name "${SQS_QUEUE_NAME}" \
  --attributes FifoQueue=true,ContentBasedDeduplication=true,RedrivePolicy=\"{\\\"maxReceiveCount\\\":\"5\\\",\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\"}\" \
  --region "${AWS_REGION}" >/dev/null

echo "Bootstrap complete."
