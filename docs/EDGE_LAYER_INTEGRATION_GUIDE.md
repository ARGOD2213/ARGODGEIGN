# Edge Layer Integration Guide — ARGUS Platform

**Target Audience:** Field Engineers, IT/OT Integration Teams  
**Prerequisites:** Ubuntu 22.04 LTS knowledge, networking basics  
**Duration:** 4-6 hours (per facility)  

---

## Overview

The ARGUS Edge Layer is a distributed gateway that:
1. Caches safety rules locally
2. Evaluates critical thresholds in real-time (<100ms latency)
3. Buffers events if cloud connectivity is lost
4. Communicates securely with cloud (TLS 1.3 + mTLS)
5. Provides offline autonomy for emergency shutdown

Typical deployment: **One per facility**, running on Raspberry Pi 4 or industrial PC.

---

## Hardware Requirements

### Minimum Configuration

| Component | Specification | Reason |
|-----------|---------------|--------|
| **CPU** | ARM v7+ or x86-64 | Node.js 20 LTS compatibility |
| **RAM** | 4GB minimum, **8GB recommended** | Rule cache + SQLite buffer |
| **Storage** | 64GB SSD | 48-hour event buffer + OS |
| **Network** | Gigabit Ethernet + WiFi | Primary + failover connectivity |
| **Power** | 24VDC UPS backup recommended | Graceful shutdown on failure |

### Recommended: Raspberry Pi 4 + Case

```
Raspberry Pi 4 (8GB variant)         $75
Industrial Case + 24VDC PSU          $120
Ethernet + WiFi combo dongle         $30
64GB microSD card (Samsung Evo)      $10
Passive heatsink + fan               $15
Total                                ~$250
```

### Alternative: Industrial PC

```
Advantech EPC-RP214:
  - Fanless x86-64 PC
  - 8GB RAM, 128GB SSD standard
  - 2× GbE, 2× USB 3.0
  - -20°C to +60°C operating
  - Cost: ~$400-600
```

---

## Software Setup

### Step 1: OS Installation & Hardening

```bash
# 1. Download Ubuntu 22.04 LTS (Raspberry Pi or amd64)
#    https://ubuntu.com/download/raspberry-pi
#    or
#    https://ubuntu.com/download/desktop

# 2. Write to USB/microSD using Balena Etcher
#    https://www.balena.io/etcher/

# 3. Boot device, complete initial setup
#    User: argus
#    Hostname: argus-edge-[FACILITY]
#    Example: argus-edge-plantA

# 4. Update system
sudo apt update
sudo apt upgrade -y

# 5. Enable UFW firewall
sudo ufw enable
sudo ufw allow 22/tcp     # SSH (bastion only)
sudo ufw allow 443/tcp    # API Gate way
sudo ufw allow 8883/tcp   # MQTT TLS
sudo ufw allow 5684/udp   # CoAP DTLS

# 6. Harden SSH
sudo nano /etc/ssh/sshd_config
# Change:
#  PermitRootLogin no
#  PasswordAuthentication no
#  PubkeyAuthentication yes
sudo systemctl restart ssh

# 7. Install SELinux (optional, advanced)
sudo apt install -y selinux-utils
sudo getenforce  # Check status
```

### Step 2: Install Dependencies

```bash
# Node.js 20 LTS
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs npm

# SQLite3
sudo apt install -y sqlite3 libsqlite3-dev

# nginx (TLS reverse proxy)
sudo apt install -y nginx

# nginx TLS modules
sudo apt install -y certbot python3-certbot-nginx

# Verify installations
node --version          # v20.x.x
npm --version           # v10.x.x
sqlite3 --version       # 3.x.x
nginx -v                # 1.x.x
```

### Step 3: Clone ARGUS Edge Repository

```bash
# Create working directory
mkdir -p ~/argus
cd ~/argus

# Clone (or extract from GitHub release)
git clone https://github.com/[YOUR-ORG]/argus-edge.git
cd argus-edge

# Install Node.js dependencies
npm install --production

# Verify installation
npm list  # Should show minimal deps (no dev packages)
du -sh node_modules  # ~150MB typical
```

### Step 4: Generate TLS Certificates

#### Option A: Self-Signed Certificate (Testing)

```bash
# Create certificate directory
mkdir -p ~/.argus/certs
cd ~/.argus/certs

# Generate private key (4096-bit RSA)
openssl genrsa -out edge-key.pem 4096

# Generate certificate (valid 365 days)
openssl req -new -x509 -key edge-key.pem -out edge-cert.pem -days 365 \
  -subj "/C=IN/ST=MP/L=Indore/O=Mahindra/CN=argus-edge-plantA"

# Restrict permissions
chmod 600 edge-key.pem
chmod 644 edge-cert.pem

# Verify
openssl x509 -in edge-cert.pem -text -noout | grep -E "Subject:|Issuer:|Not Before:|Not After"
```

#### Option B: Certificate from Internal CA (Production)

```bash
# 1. Receive CA certificate from cloud admin
scp user@cloud:/certs/ca-cert.pem ~/.argus/certs/

# 2. Request signed cert (CSR)
openssl req -new -key edge-key.pem -out edge.csr \
  -subj "/C=IN/ST=MP/L=Indore/O=Mahindra/CN=argus-edge-plantA"

# 3. Send CSR to cloud admin for signing
scp ~/.argus/certs/edge.csr user@cloud:/csr-inbox/

# 4. Receive signed certificate
scp user@cloud:/certs-signed/edge-cert.pem ~/.argus/certs/

# Verify CA chain
openssl verify -CAfile ~/.argus/certs/ca-cert.pem ~/.argus/certs/edge-cert.pem
```

### Step 5: Configure nginx

```bash
# Create nginx configuration
sudo nano /etc/nginx/sites-available/argus-edge

# Paste this:
```
```nginx
server {
    listen 443 ssl http2;
    server_name argus-edge-plantA;
    client_max_body_size 10M;

    # TLS Configuration (1.3 only)
    ssl_protocols TLSv1.3;
    ssl_ciphers AEAD-CHACHA20-POLY1305:AES-256-GCM-SHA384:AES-128-GCM-SHA256;
    ssl_prefer_server_ciphers off;
    ssl_certificate /home/argus/.argus/certs/edge-cert.pem;
    ssl_certificate_key /home/argus/.argus/certs/edge-key.pem;

    # HSTS
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # Proxy to local Node.js (localhost:3000)
    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Health check endpoint (no auth)
    location /api/health {
        proxy_pass http://localhost:3000/api/health;
        access_log off;
    }
}
```
```bash

# Enable site
sudo ln -s /etc/nginx/sites-available/argus-edge /etc/nginx/sites-enabled/
sudo nginx -t  # Test syntax
sudo systemctl restart nginx
sudo systemctl enable nginx
```

### Step 6: Configure Environment Variables

```bash
# Create environment file
nano ~/.argus/.env

# Paste this (update with your values):
```
```
# Edge Gateway Configuration
NODE_ENV=production
LISTEN_PORT=3000
LOG_LEVEL=info
FACILITY_ID=PLANT-A
GATEWAY_ID=GW-PLANT-A-001
FACILITY_LOCATION="Indore, Madhya Pradesh"

# Cloud Connectivity
CLOUD_API_ENDPOINT=https://api.argus-iot.cloud
CLOUD_API_KEY=[GET_FROM_CLOUD_ADMIN]
CLOUD_TLS_CA_CERT=/home/argus/.argus/certs/ca-cert.pem
CLOUD_CERT=/home/argus/.argus/certs/edge-cert.pem
CLOUD_KEY=/home/argus/.argus/certs/edge-key.pem

# MQTT Broker (local)
MQTT_BROKER_HOST=localhost
MQTT_BROKER_PORT=1883
MQTT_TLS_ENABLED=false  # Set to true if using TLS on broker

# Rules Engine
RULES_CACHE_TTL_MINUTES=5
RULES_SIGNATURE_VERIFY=true
FAILSAFE_TIMEOUT_MINUTES=5
LOCAL_SHUTDOWN_RELAY_ID=CONTACTOR-MAIN

# SQLite Storage
STORAGE_LOCATION=/home/argus/.argus/data
STORAGE_MAX_SIZE_GB=10
OFFLINE_BUFFER_RETENTION_HOURS=48

# Debugging
DEBUG_MODE=false
VERBOSE_LOGGING=false
```

```bash
# Set restrictive permissions
chmod 600 ~/.argus/.env

# Source it in systemd service (see Step 7)
```

### Step 7: Create systemd Service

```bash
# Create service file
sudo nano /etc/systemd/system/argus-edge.service

# Paste this:
```
```ini
[Unit]
Description=ARGUS Edge Gateway Service
After=network-online.target
Wants=network-online.target
StartLimitIntervalSec=60
StartLimitBurst=3

[Service]
Type=simple
User=argus
WorkingDirectory=/home/argus/argus/argus-edge
EnvironmentFile=/home/argus/.argus/.env

ExecStart=/usr/bin/node /home/argus/argus/argus-edge/dist/main.js

# Auto-restart on failure
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=argus-edge

# Security: Run as non-root user
PrivateTmp=yes
NoNewPrivileges=yes
ProtectHome=yes
ProtectSystem=strict
ReadWritePaths=/home/argus/.argus/data

[Install]
WantedBy=multi-user.target
```

```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable service to auto-start
sudo systemctl enable argus-edge

# Start service
sudo systemctl start argus-edge

# Check status
sudo systemctl status argus-edge
journalctl -u argus-edge -f  # Live logs
```

---

## Device Onboarding

### For Each Sensor/RTU/PLC

#### 1. Generate Device Credentials

```bash
# On edge gateway:
cd /home/argus/argus/argus-edge
node scripts/generate-device-credentials.js \
  --device-id MOTOR-01 \
  --device-type RTU \
  --location "Unit-A, Motor-1" \
  --secret-out=/tmp/motor-01-secret.txt

# Output: Device secret (256-bit symmetric key)
# Store securely: never transmit in plain text
```

#### 2. Configure Device

```bash
# On RTU/PLC/Sensor:
# Via configuration menu or configuration tool:

MQTT_BROKER_IP = <EDGE_GATEWAY_IP>
MQTT_BROKER_PORT = 8883
DEVICE_ID = MOTOR-01
DEVICE_SECRET = [PASTE_SECRET_FROM_STEP_1]
TLS_ENABLED = true
TLS_VERIFY_CERT = true

# Save configuration
# Restart device
```

#### 3. Add Device to Cloud Dashboard

```bash
# From cloud admin user:
# Open https://dashboard.argus-iot.cloud/admin/devices
# Click "Add Device"
# Fill form:
#   Device ID: MOTOR-01
#   Edge Gateway: PLANT-A
#   Device Type: RTU
#   Firmware Version: (from device)
#   Model: (from device specs)
# Submit
```

#### 4. Verify Connectivity

```bash
# On edge gateway, check MQTT:
mosquitto_sub -h localhost -t 'argus/device/MOTOR-01/#' -v

# You should see (every 30 seconds):
# argus/device/MOTOR-01/heartbeat {"status":"connected","ts":"2025-01-04T..."}

# Or check via API:
curl -k https://localhost/api/edge/devices | jq '[] | select(.device_id=="MOTOR-01")'
# Output:
# {
#   "device_id": "MOTOR-01",
#   "status": "ONLINE",
#   "last_heartbeat": "2025-01-04T12:30:45Z"
# }
```

---

## Testing & Validation

### 1. TLS Certificate Verification

```bash
# Check certificate validity
openssl x509 -in ~/.argus/certs/edge-cert.pem -text -noout

# Test TLS connectivity from cloud
openssl s_client -connect argus-edge-plantA:443 \
  -cert ~/.argus/certs/edge-cert.pem \
  -key ~/.argus/certs/edge-key.pem \
  -CAfile ~/.argus/certs/ca-cert.pem

# Should complete handshake with:
# Verify return code: 0 (ok)
```

### 2. Rule Evaluation Latency

```bash
# Inject test event
mosquitto_pub -h localhost -t argus/device/MOTOR-01/telemetry -m \
  '{"device_id":"MOTOR-01","temperature_celsius":88.0}'

# Measure time from publish to rule evaluation
# Target: <100ms local latency

# Check logs
journalctl -u argus-edge -n 50 | grep -i "RULE-MOTOR-01-TEMP"
```

### 3. Offline Failsafe

```bash
# 1. Simulate cloud disconnect
sudo iptables -A OUTPUT -d api.argus-iot.cloud -j DROP

# 2. Inject CRITICAL event (>85°C)
mosquitto_pub -h localhost -t argus/device/MOTOR-01/telemetry -m \
  '{"device_id":"MOTOR-01","temperature_celsius":95.0}'

# 3. Verify local relay triggered
curl -s https://localhost/api/edge/status | jq '.relays[] | select(.id=="CONTACTOR-01")'
# Should show: "state": "OPEN"

# 4. Check offline buffer
sqlite3 ~/.argus/data/buffer.db "SELECT COUNT(*) FROM offline_events WHERE created_at > datetime('now', '-1 minutes');"
# Should show new event count

# 5. Restore connectivity
sudo iptables -D OUTPUT -d api.argus-iot.cloud -j DROP

# 6. Verify replay
curl -s https://localhost/api/edge/offline-sync
# Should replay buffered events
```

### 4. Load Test

```bash
# Simulate 100 devices × 10 msg/sec = 1000 msg/sec
#  (Raspberry Pi 4 target: >500 msg/sec local processing)

cd ~/argus/argus-edge
npm run load-test -- \
  --devices=100 \
  --message-rate=10 \
  --duration=60 \
  --payload-size=256

# Output:
# Throughput: 1050 msg/sec
# Latency (avg): 45ms
# Latency (p95): 120ms
# CPU usage: 35%
# Memory: 850MB
```

---

## Cloud Synchronization API

### Endpoints Available on Edge Gateway

**Base URL:** `https://argus-edge-plantA/api/v1/`

```
# Health & Status
GET  /health                          Health check (no auth)
GET  /edge/status                     Gateway status + metrics
PUT  /edge/status                     Update gateway status

# Rules
GET  /edge/rules                      Download latest rules (5min cache)
POST /edge/rules/evaluate/{rule-id}   Evaluate specific rule

# Events & Logging
POST /edge/events                     Batch submit events
GET  /edge/events?hours=24            Query local events
POST /edge/offline-sync               Trigger offline replay

# Device Management
GET  /edge/devices                    List connected devices
GET  /edge/devices/{device-id}        Device details
POST /edge/devices/{device-id}/cmd    Send command to device

# Configuration
GET  /edge/config                     Current configuration
PUT  /edge/config                     Update config (admin only)

# Diagnostics
GET  /edge/diagnostics                CPU, RAM, disk, network stats
GET  /edge/logs?level=ERROR           Filter logs by level
```

### Example: Check Gateway Metrics

```bash
curl -k https://argus-edge-plantA/api/v1/edge/status | jq '.'

# Response:
{
  "gateway_id": "GW-PLANT-A-001",
  "status": "ONLINE",
  "uptime_seconds": 86400,
  "cloud_connected": true,
  "rules_cached": 47,
  "rules_last_sync": "2025-01-04T12:00:00Z",
  "devices_connected": 12,
  "events_processed_today": 1048576,
  "offline_buffer_usage_percent": 2.3,
  "cpu_usage_percent": 18.5,
  "memory_usage_mb": 620,
  "disk_usage_percent": 35.2,
  "tls_cert_valid_until": "2026-01-04T00:00:00Z"
}
```

---

## Troubleshooting

### Issue: "Connection refused" to cloud

```bash
# Check network connectivity
ping api.argus-iot.cloud

# Check firewall rules
sudo ufw status numbered
# Should show outbound 443 allowed

# Check TLS certificate
curl -k --cert ~/.argus/certs/edge-cert.pem \
     --key ~/.argus/certs/edge-key.pem \
     https://api.argus-iot.cloud/api/v1/health

# Check logs
journalctl -u argus-edge -n 100 | grep -i "connection"
```

### Issue: Devices not connecting

```bash
# Check MQTT broker running
sudo ss -tlnp | grep 8883

# Check device firewall (from device):
telnet <EDGE_GATEWAY_IP> 8883

# Check device logs for connection errors
# (depends on device type)

# Reset device, retry onboarding
```

### Issue: Rules not evaluating

```bash
# Check rules cached
curl -k https://localhost/api/v1/edge/rules | jq '.[] | .id'

# Manually test rule evaluation
curl -k -X POST https://localhost/api/v1/edge/rules/evaluate/RULE-MOTOR-01-TEMP

# Check rule syntax
cat ~/.argus/data/rules.json | jq '.[] | .trigger'

# Verify rule signature
openssl dgst -sha256 ~/.argus/data/rules.json
```

### Issue: Out of disk space

```bash
# Check usage
df -h

# Move old events to archive (S3)
node scripts/archive-old-events.js --older-than-days=30

# Cleanup offline buffer
sqlite3 ~/.argus/data/buffer.db "DELETE FROM offline_events WHERE created_at < datetime('now', '-48 hours');"

# Restart service
sudo systemctl restart argus-edge
```

---

## Maintenance

### Daily Checklist

```bash
# Check connectivity
curl -k https://localhost/api/v1/health

# Check device count
curl -k https://localhost/api/v1/edge/devices | jq 'length'

# Check disk usage
df -h | grep /home

# Monitor logs
journalctl -u argus-edge --since "1 hour ago" | grep -i error
```

### Weekly Tasks

```bash
# Backup configuration
tar -czf ~/.argus/backup-$(date +%Y-%m-%d).tar.gz \
  ~/.argus/certs ~/.argus/.env ~/.argus/data/rules.json
scp ~/.argus/backup-*.tar.gz user@backup-server:/backups/

# Check certificate expiry
openssl x509 -in ~/.argus/certs/edge-cert.pem -noout -dates

# Review system logs
sudo journalctl -u argus-edge --since "1 week ago" | tail -20
```

### Monthly Tasks

```bash
# Rotate logs (handled by systemd, but verify)
sudo journalctl --vacuum=30d  # Keep 30 days

# Update apt packages
sudo apt update && sudo apt upgrade -y

# Test failover (simulate cloud disconnect)
sudo iptables -A OUTPUT -d api.argus-iot.cloud -j DROP
sleep 60  # Verify offline mode
sudo iptables -D OUTPUT -d api.argus-iot.cloud -j DROP  # Restore

# Certificate renewal (90 days before expiry)
# Automated via cron (if using Let's Encrypt)
# Manual: openssl req -renew...
```

### Annual Tasks

```bash
# Full security audit
sudo auditctl -l
sudo fail2ban-client status

# TLS certificate renewal (all certificates)
cd ~/.argus/certs
# Generate new cert via CA or self-signed
# Update nginx config
sudo systemctl restart nginx

# Capacity planning
du -sh ~/.argus/data
# Estimate storage growth over next year
```

---

## References

- **ARGUS Edge Repository:** https://github.com/[YOUR-ORG]/argus-edge
- **ADR-005:** `docs/adr/ADR-005-edge-layer-architecture.md`
- **Cloud API Docs:** https://api.argus-iot.cloud/docs
- **nginx TLS Guide:** https://nginx.org/en/docs/http/ngx_http_ssl_module.html
- **MQTT TLS/SSL:** https://mosquitto.org/man/mosquitto-tls-7.html

---

**Document Date:** 2025-01-04  
**Last Updated:** Sprint 6  
**Next Review:** Post-deployment (Sprint 7)  
