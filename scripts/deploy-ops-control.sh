#!/bin/bash
set -e

REGION="ap-south-1"
ACCOUNT="061039801536"
INSTANCE_ID="i-0761b14159f2e2a3f"
LAMBDA_NAME="argus-ops-control"
ROLE_NAME="argus-ops-lambda-role"
BUCKET="argus-ops-ui"
ELASTIC_IP="${1:?Usage: ./deploy-ops-control.sh <elastic-ip>}"

echo "=== Step 1: Create IAM role if not exists ==="
ROLE_ARN="arn:aws:iam::${ACCOUNT}:role/${ROLE_NAME}"
aws iam create-role \
  --role-name "$ROLE_NAME" \
  --assume-role-policy-document '{
    "Version":"2012-10-17",
    "Statement":[{
      "Effect":"Allow",
      "Principal":{"Service":"lambda.amazonaws.com"},
      "Action":"sts:AssumeRole"
    }]
  }' --region "$REGION" 2>/dev/null || echo "Role exists, skipping."

aws iam put-role-policy \
  --role-name "$ROLE_NAME" \
  --policy-name argus-ops-policy \
  --policy-document "{
    \"Version\":\"2012-10-17\",
    \"Statement\":[
      {\"Effect\":\"Allow\",
       \"Action\":[\"ec2:StartInstances\",\"ec2:StopInstances\",\"ec2:DescribeInstances\"],
       \"Resource\":\"arn:aws:ec2:${REGION}:${ACCOUNT}:instance/${INSTANCE_ID}\"},
      {\"Effect\":\"Allow\",
       \"Action\":\"ec2:DescribeInstances\",
       \"Resource\":\"*\"},
      {\"Effect\":\"Allow\",
       \"Action\":\"ssm:GetParameter\",
       \"Resource\":\"arn:aws:ssm:${REGION}:${ACCOUNT}:parameter/argus/ops/control_password\"},
      {\"Effect\":\"Allow\",
       \"Action\":[\"logs:CreateLogGroup\",\"logs:CreateLogStream\",\"logs:PutLogEvents\"],
       \"Resource\":\"arn:aws:logs:${REGION}:${ACCOUNT}:*\"}
    ]
  }" --region "$REGION"

echo "=== Step 2: Package Lambda ==="
cd lambda/ops_control
zip -r ../../ops_control.zip handler.py
cd ../..

echo "=== Step 3: Deploy Lambda ==="
EXISTING=$(aws lambda get-function --function-name "$LAMBDA_NAME" \
  --region "$REGION" 2>&1 || true)

if echo "$EXISTING" | grep -q 'ResourceNotFoundException'; then
  aws lambda create-function \
    --function-name "$LAMBDA_NAME" \
    --runtime python3.12 \
    --role "$ROLE_ARN" \
    --handler handler.lambda_handler \
    --zip-file fileb://ops_control.zip \
    --environment "Variables={INSTANCE_ID=${INSTANCE_ID},ELASTIC_IP=${ELASTIC_IP}}" \
    --timeout 15 \
    --region "$REGION"
else
  aws lambda update-function-code \
    --function-name "$LAMBDA_NAME" \
    --zip-file fileb://ops_control.zip \
    --region "$REGION"
  aws lambda update-function-configuration \
    --function-name "$LAMBDA_NAME" \
    --environment "Variables={INSTANCE_ID=${INSTANCE_ID},ELASTIC_IP=${ELASTIC_IP}}" \
    --region "$REGION"
fi

echo "=== Step 4: Create API Gateway HTTP API ==="
API_ID=$(aws apigatewayv2 create-api \
  --name argus-ops-api \
  --protocol-type HTTP \
  --cors-configuration \
    AllowOrigins='["*"]',AllowMethods='["GET","POST","OPTIONS"]',\
AllowHeaders='["Content-Type","x-ops-password"]' \
  --region "$REGION" \
  --query 'ApiId' --output text 2>/dev/null || \
  aws apigatewayv2 get-apis --region "$REGION" \
    --query 'Items[?Name==`argus-ops-api`].ApiId' --output text)

LAMBDA_ARN="arn:aws:lambda:${REGION}:${ACCOUNT}:function:${LAMBDA_NAME}"

INTEGRATION_ID=$(aws apigatewayv2 create-integration \
  --api-id "$API_ID" \
  --integration-type AWS_PROXY \
  --integration-uri "$LAMBDA_ARN" \
  --payload-format-version 2.0 \
  --region "$REGION" \
  --query 'IntegrationId' --output text)

for ROUTE in "GET /ops/status" "POST /ops/start" "POST /ops/stop"; do
  aws apigatewayv2 create-route \
    --api-id "$API_ID" \
    --route-key "$ROUTE" \
    --target "integrations/${INTEGRATION_ID}" \
    --region "$REGION" 2>/dev/null || true
done

aws apigatewayv2 create-stage \
  --api-id "$API_ID" \
  --stage-name '$default' \
  --auto-deploy \
  --region "$REGION" 2>/dev/null || true

aws lambda add-permission \
  --function-name "$LAMBDA_NAME" \
  --statement-id argus-apigw-invoke \
  --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:${REGION}:${ACCOUNT}:${API_ID}/*" \
  --region "$REGION" 2>/dev/null || true

API_URL="https://${API_ID}.execute-api.${REGION}.amazonaws.com"
echo ""
echo "=== API Gateway URL ==="
echo "$API_URL"
echo ""
echo "=== Step 5: Create S3 bucket for ops.html ==="
aws s3 mb "s3://${BUCKET}" --region "$REGION" 2>/dev/null || true
aws s3api put-bucket-ownership-controls \
  --bucket "$BUCKET" \
  --ownership-controls 'Rules=[{ObjectOwnership=BucketOwnerPreferred}]' \
  --region "$REGION" 2>/dev/null || true
aws s3api put-public-access-block \
  --bucket "$BUCKET" \
  --public-access-block-configuration \
    'BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false' \
  --region "$REGION"
aws s3api put-bucket-policy \
  --bucket "$BUCKET" \
  --policy "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",
    \"Principal\":\"*\",\"Action\":\"s3:GetObject\",
    \"Resource\":\"arn:aws:s3:::${BUCKET}/*\"}]}" \
  --region "$REGION"
aws s3 website "s3://${BUCKET}" --index-document ops.html --region "$REGION"

echo "=== Step 6: Upload mobile web app assets ==="
TMP_OPS_HTML="$(mktemp)"
sed "s|${API_PLACEHOLDER:-https://REPLACE_WITH_API_GATEWAY_URL}|${API_URL}|g" \
  src/main/resources/static/ops.html > "$TMP_OPS_HTML"
aws s3 cp "$TMP_OPS_HTML" "s3://${BUCKET}/ops.html" \
  --content-type text/html --region "$REGION"
aws s3 cp src/main/resources/static/ops.webmanifest \
  "s3://${BUCKET}/ops.webmanifest" \
  --content-type application/manifest+json --region "$REGION"
aws s3 cp src/main/resources/static/argus-ops-icon.svg \
  "s3://${BUCKET}/argus-ops-icon.svg" \
  --content-type image/svg+xml --region "$REGION"
rm -f "$TMP_OPS_HTML"

echo ""
echo "=== DONE ==="
echo "API URL:      $API_URL"
echo "ops.html URL: http://${BUCKET}.s3-website.${REGION}.amazonaws.com/ops.html"
echo ""
echo "The mobile web app has been uploaded with the backend URL injected."
echo "Android and iPhone can both use the same backend from the same S3 page."
echo ""
echo "IMPORTANT: Create the SSM parameter before deploying if not done:"
echo "  aws ssm put-parameter --name /argus/ops/control_password \\"
echo "    --value 'your-strong-password' --type SecureString --region $REGION"
