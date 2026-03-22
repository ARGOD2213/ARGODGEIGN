"""
ARGUS Edge Agent - Main Entry Point
Starts all agent components in separate threads.
Reads MQTT, normalises, writes to ring buffer,
forwards to SQS, sends health beacons.
"""
import json
import logging
import signal
import sys
import threading
from pathlib import Path

import paho.mqtt.client as mqtt

from .config import EDGE_ID, LOG_LEVEL, LOG_PATH, MQTT_HOST, MQTT_PORT, MQTT_TOPIC
from .health_beacon import HealthBeacon
from .local_rules import LocalRuleEngine
from .normaliser import normalise
from .ring_buffer import RingBuffer
from .sqs_forwarder import SqsForwarder

log = logging.getLogger("argus.edge")
_components = {}


def configure_logging() -> None:
    handlers = [logging.StreamHandler(sys.stdout)]
    try:
        Path(LOG_PATH).parent.mkdir(parents=True, exist_ok=True)
        handlers.append(logging.FileHandler(LOG_PATH, mode="a", encoding="utf-8"))
    except OSError as exc:
        logging.basicConfig(level=getattr(logging, LOG_LEVEL, logging.INFO), force=True)
        log.warning("File logging disabled for %s: %s", LOG_PATH, exc)
        return

    logging.basicConfig(
        level=getattr(logging, LOG_LEVEL, logging.INFO),
        format="%(asctime)s %(levelname)-8s %(name)s - %(message)s",
        handlers=handlers,
        force=True,
    )


def on_mqtt_message(client, userdata, msg):
    """
    MQTT message callback.
    Normalise -> write ring buffer -> evaluate local rules.
    Local rules fire only when cloud forwarding is unavailable.
    """
    try:
        raw = json.loads(msg.payload.decode("utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        log.warning("MQTT: could not parse payload on %s: %s", msg.topic, exc)
        return

    event = normalise(raw, mqtt_topic=msg.topic)
    if event is None:
        return

    if not _components["ring_buffer"].write(event):
        log.error("MQTT: failed to persist event for topic %s", msg.topic)

    forwarder = _components["forwarder"]
    if not forwarder.cloud_available:
        alert = _components["local_rules"].evaluate(event)
        if alert:
            log.warning("LOCAL_ALERT: %s", json.dumps(alert))


def on_mqtt_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        log.info("MQTT: connected to %s:%s", MQTT_HOST, MQTT_PORT)
        client.subscribe(MQTT_TOPIC)
        log.info("MQTT: subscribed to %s", MQTT_TOPIC)
    else:
        log.error("MQTT: connection failed with code %d", rc)


def on_mqtt_disconnect(client, userdata, rc, properties=None):
    if rc != 0:
        log.warning("MQTT: unexpected disconnect (rc=%d) - broker/client will retry", rc)


def shutdown(sig=None, frame=None):
    log.info("Shutting down ARGUS Edge Agent...")
    mqtt_client = _components.get("mqtt_client")
    if mqtt_client is not None:
        try:
            mqtt_client.disconnect()
        except Exception:  # pylint: disable=broad-except
            pass

    for name, component in _components.items():
        if hasattr(component, "stop"):
            component.stop()
            log.info("Stopped: %s", name)
    raise SystemExit(0)


def _build_mqtt_client():
    try:
        return mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, client_id=EDGE_ID)
    except AttributeError:
        return mqtt.Client(client_id=EDGE_ID)


def main():
    configure_logging()
    log.info("Starting ARGUS Edge Agent - ID: %s", EDGE_ID)

    ring_buffer = RingBuffer()
    local_rules = LocalRuleEngine()
    forwarder = SqsForwarder(ring_buffer)
    beacon = HealthBeacon()

    _components["ring_buffer"] = ring_buffer
    _components["local_rules"] = local_rules
    _components["forwarder"] = forwarder
    _components["beacon"] = beacon

    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)

    threading.Thread(target=forwarder.run_forever, daemon=True, name="sqs-forwarder").start()
    threading.Thread(
        target=beacon.run_forever,
        args=(ring_buffer,),
        daemon=True,
        name="health-beacon",
    ).start()

    client = _build_mqtt_client()
    client.on_connect = on_mqtt_connect
    client.on_message = on_mqtt_message
    client.on_disconnect = on_mqtt_disconnect
    client.reconnect_delay_set(min_delay=1, max_delay=30)
    client.connect(MQTT_HOST, MQTT_PORT, keepalive=60)
    _components["mqtt_client"] = client

    log.info("ARGUS Edge Agent running. Press Ctrl+C to stop.")
    client.loop_forever()


if __name__ == "__main__":
    main()
