"""
ARGUS Edge Agent - SQS Forwarder
Forwards normalised events from ring buffer to SQS iot-events.fifo.
Runs in a background thread, drains unreplayed buffer events.
Handles connectivity failures gracefully - events stay in buffer.
"""
import json
import logging
import time
import uuid

import boto3

from .config import AWS_ACCESS_KEY_ID, AWS_REGION, AWS_SECRET_ACCESS_KEY, SQS_QUEUE_URL

log = logging.getLogger(__name__)


class SqsForwarder:
    def __init__(self, ring_buffer):
        self.buffer = ring_buffer
        self.sqs = boto3.client(
            "sqs",
            region_name=AWS_REGION,
            aws_access_key_id=AWS_ACCESS_KEY_ID or None,
            aws_secret_access_key=AWS_SECRET_ACCESS_KEY or None,
        )
        self._running = False
        self.batch_size = 10
        self.interval = 5
        self.cloud_available = True
        self.last_send_ok = True
        self.last_error = ""

    def _send_batch(self, events: list) -> list:
        """
        Send a batch of events to SQS FIFO.
        Returns list of successfully sent buffer IDs.
        """
        if not events:
            return []
        if not SQS_QUEUE_URL:
            log.warning("SqsForwarder: SQS_QUEUE_URL is empty, cannot forward events")
            self.cloud_available = False
            self.last_send_ok = False
            self.last_error = "SQS_QUEUE_URL not configured"
            return []

        entries = []
        for event in events:
            buf_id = event.get("_buf_id")
            body = {key: value for key, value in event.items() if key != "_buf_id"}
            entries.append(
                {
                    "Id": str(buf_id),
                    "MessageBody": json.dumps(body),
                    "MessageGroupId": body.get("machine_id", "default"),
                    "MessageDeduplicationId": str(uuid.uuid4()),
                }
            )

        try:
            response = self.sqs.send_message_batch(QueueUrl=SQS_QUEUE_URL, Entries=entries)
            successful_ids = [int(item["Id"]) for item in response.get("Successful", [])]
            failed = response.get("Failed", [])
            self.cloud_available = True
            self.last_send_ok = not failed
            self.last_error = ""
            if failed:
                log.warning("SqsForwarder: %d messages failed", len(failed))
            return successful_ids
        except Exception as exc:  # pylint: disable=broad-except
            self.cloud_available = False
            self.last_send_ok = False
            self.last_error = str(exc)
            log.error("SqsForwarder: batch send failed: %s", exc)
            return []

    def drain_once(self) -> None:
        """Drain one batch of unreplayed events from the ring buffer to SQS."""
        events = self.buffer.get_unreplayed(limit=self.batch_size)
        if not events:
            return
        sent_ids = self._send_batch(events)
        if sent_ids:
            self.buffer.mark_replayed(sent_ids)
            log.info("SqsForwarder: forwarded %d events to SQS", len(sent_ids))

    def run_forever(self) -> None:
        """Drain the ring buffer continuously. Stop on self._running = False."""
        self._running = True
        log.info("SqsForwarder: starting drain loop, interval=%ds", self.interval)
        while self._running:
            try:
                self.drain_once()
                self.buffer.purge_old()
            except Exception as exc:  # pylint: disable=broad-except
                self.cloud_available = False
                self.last_send_ok = False
                self.last_error = str(exc)
                log.error("SqsForwarder: unexpected error: %s", exc)
            time.sleep(self.interval)

    def stop(self) -> None:
        self._running = False
