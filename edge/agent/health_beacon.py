"""
ARGUS Edge Agent - Health Beacon
Publishes SNS heartbeat every N seconds.
ops.html shows EDGE_OFFLINE if no heartbeat for 5 minutes.
ops_control Lambda reads heartbeat timestamp from DynamoDB.
"""
import json
import logging
import os
import time
from datetime import datetime, timezone

import boto3

from .config import (
    AWS_ACCESS_KEY_ID,
    AWS_REGION,
    AWS_SECRET_ACCESS_KEY,
    BEACON_INTERVAL,
    EDGE_ID,
    SNS_TOPIC_ARN,
)

log = logging.getLogger(__name__)


class HealthBeacon:
    def __init__(self):
        self.sns = boto3.client(
            "sns",
            region_name=AWS_REGION,
            aws_access_key_id=AWS_ACCESS_KEY_ID or None,
            aws_secret_access_key=AWS_SECRET_ACCESS_KEY or None,
        )
        self.ddb = boto3.resource(
            "dynamodb",
            region_name=AWS_REGION,
            aws_access_key_id=AWS_ACCESS_KEY_ID or None,
            aws_secret_access_key=AWS_SECRET_ACCESS_KEY or None,
        )
        self.heartbeat_table = os.environ.get("HEARTBEAT_TABLE", "iot-sensor-events")
        self.interval = BEACON_INTERVAL
        self.edge_id = EDGE_ID
        self._running = False

    def send_once(self, ring_stats: dict = None) -> bool:
        """Send a single heartbeat to SNS and persist a status record for ops.html."""
        payload = {
            "type": "EDGE_HEARTBEAT",
            "alertId": f"edge_heartbeat#{self.edge_id}#{int(time.time())}",
            "edgeId": self.edge_id,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "status": "ONLINE",
            "ringBuffer": ring_stats or {},
        }

        sns_ok = False
        ddb_ok = False

        if SNS_TOPIC_ARN:
            try:
                self.sns.publish(
                    TopicArn=SNS_TOPIC_ARN,
                    Subject=f"EDGE_HEARTBEAT - {self.edge_id}",
                    Message=json.dumps(payload),
                    MessageAttributes={
                        "type": {"DataType": "String", "StringValue": "EDGE_HEARTBEAT"}
                    },
                )
                sns_ok = True
            except Exception as exc:  # pylint: disable=broad-except
                log.error("HealthBeacon: SNS publish failed: %s", exc)
        else:
            log.warning("HealthBeacon: SNS_TOPIC_ARN is empty, skipping publish")

        try:
            table = self.ddb.Table(self.heartbeat_table)
            table.put_item(
                Item={
                    "deviceId": self.edge_id,
                    "timestamp": payload["timestamp"],
                    "sensorType": "EDGE_HEARTBEAT",
                    "sensorCategory": "Edge",
                    "unit": "",
                    "value": 1,
                    "status": "ONLINE",
                    "location": "EDGE",
                    "facilityId": self.edge_id,
                    "processedAt": payload["timestamp"],
                    "alertId": payload["alertId"],
                    "aiIncidentSummary": "[AI ADVISORY | Not a control action | Rule engine has final authority]",
                    "llmConsensus": "EDGE_HEARTBEAT",
                }
            )
            ddb_ok = True
        except Exception as exc:  # pylint: disable=broad-except
            log.error("HealthBeacon: DynamoDB write failed: %s", exc)

        if sns_ok or ddb_ok:
            log.debug("HealthBeacon: heartbeat recorded for %s", self.edge_id)
            return True
        return False

    def run_forever(self, ring_buffer=None) -> None:
        """
        Send heartbeat every self.interval seconds.
        Pass a RingBuffer instance to include buffer stats.
        Run in a separate thread from the main agent loop.
        """
        self._running = True
        log.info("HealthBeacon: starting, interval=%ds", self.interval)
        while self._running:
            stats = ring_buffer.stats() if ring_buffer else {}
            self.send_once(stats)
            time.sleep(self.interval)

    def stop(self) -> None:
        self._running = False
