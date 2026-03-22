# Sprint 4 Operator Setup

These steps are run once by the developer on a local machine. Do not commit real passwords or secret values.

## 1. Allocate Elastic IP and attach to EC2

```bash
aws ec2 allocate-address --domain vpc --region ap-south-1
aws ec2 associate-address --instance-id i-0761b14159f2e2a3f \
  --allocation-id <eipalloc-id> --region ap-south-1
```

## 2. Create SSM parameter for ops password

```bash
aws ssm put-parameter \
  --name /argus/ops/control_password \
  --value 'your-strong-password-here' \
  --type SecureString \
  --region ap-south-1
```

## 3. Run deploy script

```bash
bash scripts/deploy-ops-control.sh <elastic-ip>
```

## 4. Set API Gateway URL in ops.html

Replace `REPLACE_WITH_API_GATEWAY_URL` in `src/main/resources/static/ops.html` with the URL printed by `deploy-ops-control.sh`.

Then upload the single source of truth to S3:

```bash
aws s3 cp src/main/resources/static/ops.html \
  s3://argus-ops-ui/ops.html \
  --content-type text/html \
  --region ap-south-1
```

## 5. Set dashboard credentials on EC2

SSH into EC2 and add these to `/etc/environment` or the systemd service:

```bash
ARGUS_DASHBOARD_USER=argus
ARGUS_DASHBOARD_PASS=<strong-password>
```

## 6. Open dashboard port after auth is confirmed

```bash
bash scripts/open-dashboard-port.sh
```

Run this only after confirming Spring Security HTTP Basic auth works on port `8080`.

## 7. Bookmark the phone control page

Bookmark:

```text
http://argus-ops-ui.s3-website.ap-south-1.amazonaws.com/ops.html
```

This page remains available even when EC2 is stopped.

## Phone flow after setup

1. Open `ops.html` bookmark on phone.
2. Confirm EC2 shows `Stopped`.
3. Enter ops password and tap `Start ARGUS`.
4. Wait for `Healthy`.
5. Tap `Open Dashboard`.
6. Enter dashboard username/password once when prompted by the browser.
7. After the demo, return to `ops.html` and tap `Stop ARGUS`.
