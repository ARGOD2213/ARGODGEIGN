#!/bin/bash
# Run once from laptop to create EC2 instance
# After this, use GitHub Actions from mobile to start/stop/deploy

set -euo pipefail

AWS_REGION="ap-south-1"
INSTANCE_NAME="argodreign-iot-server"
SECURITY_GROUP_NAME="argodreign-sg"
KEY_PAIR_NAME="argodreign-ec2-key"
KEY_FILE="${HOME}/.ssh/${KEY_PAIR_NAME}.pem"

echo "Creating EC2 t3.micro instance..."

# Get default VPC
VPC_ID=$(aws ec2 describe-vpcs \
  --filters "Name=isDefault,Values=true" \
  --query 'Vpcs[0].VpcId' --output text --region "$AWS_REGION")

if [[ "$VPC_ID" == "None" || -z "$VPC_ID" ]]; then
  echo "ERROR: Default VPC not found in region $AWS_REGION"
  exit 1
fi

# Create security group if missing
SG_ID=$(aws ec2 describe-security-groups \
  --filters "Name=vpc-id,Values=${VPC_ID}" "Name=group-name,Values=${SECURITY_GROUP_NAME}" \
  --query 'SecurityGroups[0].GroupId' --output text --region "$AWS_REGION")

if [[ "$SG_ID" == "None" || -z "$SG_ID" ]]; then
  SG_ID=$(aws ec2 create-security-group \
    --group-name "$SECURITY_GROUP_NAME" \
    --description "ARGODREIGN IoT Engine" \
    --vpc-id "$VPC_ID" \
    --region "$AWS_REGION" \
    --query 'GroupId' --output text)
fi

# Open port 8080 for API and dashboard
aws ec2 authorize-security-group-ingress \
  --group-id "$SG_ID" --protocol tcp --port 8080 \
  --cidr 0.0.0.0/0 --region "$AWS_REGION" >/dev/null 2>&1 || true

# Open port 22 for SSH (optional, can remove for security)
aws ec2 authorize-security-group-ingress \
  --group-id "$SG_ID" --protocol tcp --port 22 \
  --cidr 0.0.0.0/0 --region "$AWS_REGION" >/dev/null 2>&1 || true

# Create key pair once (needed for GitHub SSH deploy action)
if ! aws ec2 describe-key-pairs --key-names "$KEY_PAIR_NAME" --region "$AWS_REGION" >/dev/null 2>&1; then
  mkdir -p "${HOME}/.ssh"
  aws ec2 create-key-pair \
    --key-name "$KEY_PAIR_NAME" \
    --region "$AWS_REGION" \
    --query 'KeyMaterial' --output text > "$KEY_FILE"
  chmod 400 "$KEY_FILE"
  echo "Created key pair and saved private key to: $KEY_FILE"
else
  echo "Key pair $KEY_PAIR_NAME already exists."
  if [[ ! -f "$KEY_FILE" ]]; then
    echo "WARNING: $KEY_FILE not found. Use your existing .pem content for GitHub secret EC2_SSH_KEY."
  fi
fi

# Get latest Amazon Linux 2023 AMI
AMI_ID=$(aws ec2 describe-images \
  --owners amazon \
  --filters "Name=name,Values=al2023-ami-*-x86_64" \
            "Name=state,Values=available" \
  --query 'sort_by(Images, &CreationDate)[-1].ImageId' \
  --output text --region "$AWS_REGION")

# Create EC2 instance (STOPPED initially — only pay when running)
INSTANCE_ID=$(aws ec2 run-instances \
  --image-id "$AMI_ID" \
  --instance-type t3.micro \
  --key-name "$KEY_PAIR_NAME" \
  --security-group-ids "$SG_ID" \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${INSTANCE_NAME}}]" \
  --user-data '#!/bin/bash
    yum update -y
    yum install -y docker git
    systemctl start docker
    systemctl enable docker
    usermod -aG docker ec2-user
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
      -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose' \
  --region "$AWS_REGION" \
  --query 'Instances[0].InstanceId' --output text)

echo "EC2 Instance ID: $INSTANCE_ID"
echo "Stopping instance immediately (start from GitHub Actions when needed)"

# Stop immediately — developer starts from phone when needed
aws ec2 stop-instances --instance-ids "$INSTANCE_ID" --region "$AWS_REGION" >/dev/null

echo ""
echo "IMPORTANT: Add this to GitHub Secrets:"
echo "  EC2_INSTANCE_ID = $INSTANCE_ID"
echo "  EC2_SSH_KEY     = contents of $KEY_FILE"
echo "  AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY"
echo "  WEATHER_API_KEY / GEMINI_API_KEY"
echo ""
echo "Then from your phone: GitHub -> Actions -> START IoT Server"
