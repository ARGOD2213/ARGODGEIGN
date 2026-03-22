"""
ARGUS Edge Agent - Configuration
All values read from environment variables.
Never hardcode credentials or endpoints.
"""
import os

# AWS
AWS_REGION = os.environ.get("AWS_REGION", "ap-south-1")
SQS_QUEUE_URL = os.environ.get("SQS_QUEUE_URL", "")
SNS_TOPIC_ARN = os.environ.get("SNS_TOPIC_ARN", "")
AWS_ACCESS_KEY_ID = os.environ.get("AWS_ACCESS_KEY_ID", "")
AWS_SECRET_ACCESS_KEY = os.environ.get("AWS_SECRET_ACCESS_KEY", "")
AWS_SECRET_KEY = AWS_SECRET_ACCESS_KEY

# MQTT broker (local on edge PC)
MQTT_HOST = os.environ.get("MQTT_HOST", "localhost")
MQTT_PORT = int(os.environ.get("MQTT_PORT", "1883"))
MQTT_TOPIC = os.environ.get("MQTT_TOPIC", "argus/sensors/#")

# Ring buffer
RING_BUFFER_PATH = os.environ.get("RING_BUFFER_PATH", "/var/argus/ring_buffer.db")
RING_BUFFER_HOURS = int(os.environ.get("RING_BUFFER_HOURS", "4"))

# Rules
RULES_PATH = os.environ.get("RULES_PATH", "lambda/rule_engine/config/rules.json")

# Health beacon
BEACON_INTERVAL = int(os.environ.get("BEACON_INTERVAL_SEC", "60"))
EDGE_ID = os.environ.get("EDGE_ID", "edge-argus-001")

# Schema
SCHEMA_PATH = os.environ.get("SCHEMA_PATH", "docs/schema/sensor_event_schema.json")

# Logging
LOG_LEVEL = os.environ.get("LOG_LEVEL", "INFO")
LOG_PATH = os.environ.get("LOG_PATH", "/var/argus/edge.log")
