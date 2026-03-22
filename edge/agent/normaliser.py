"""
ARGUS Edge Agent - Data Normaliser
Reads raw MQTT sensor events and maps them to the
ARGUS canonical schema (docs/schema/sensor_event_schema.json).
Validates all required fields before forwarding.
ADR-002: read-only from OT, no write path to PLC/DCS.
"""
import logging
import time
from datetime import datetime, timezone
from typing import Any, Optional

from .config import EDGE_ID

log = logging.getLogger(__name__)

REQUIRED_FIELDS = [
    "timestamp_epoch",
    "machine_id",
    "machine_class",
    "sensor_type",
    "sensor_value",
    "sensor_unit",
    "quality_tag",
    "plant_zone",
    "alert_severity",
]

SENSOR_TYPE_MAP = {
    "vibration": "VIBRATION",
    "vib": "VIBRATION",
    "compressor_vibration": "VIBRATION",
    "temperature": "TEMPERATURE",
    "temp": "TEMPERATURE",
    "bearing_temperature": "BEARING_TEMPERATURE",
    "bearing_temp": "BEARING_TEMPERATURE",
    "gas": "GAS_LEAK",
    "nh3": "GAS_LEAK",
    "gas_leak": "GAS_LEAK",
    "pressure": "OIL_PRESSURE",
    "press": "OIL_PRESSURE",
    "oil_pressure": "OIL_PRESSURE",
    "power_factor": "POWER_FACTOR",
    "pf": "POWER_FACTOR",
}

UNIT_MAP = {
    "mm/s": "mm/s",
    "mms": "mm/s",
    "mm_s": "mm/s",
    "degc": "C",
    "celsius": "C",
    "c": "C",
    "ppm": "ppm",
    "bar": "bar",
    "barg": "bar",
    "ph": "pH",
    "%": "%",
}


def normalise(raw_payload: dict, mqtt_topic: str = "") -> Optional[dict]:
    """
    Convert a raw MQTT payload to the ARGUS canonical schema.
    Returns None if payload cannot be normalised.
    Logs a warning for every rejected event.
    """
    try:
        ts_epoch = _parse_timestamp(_pick_first(raw_payload, "timestamp", "ts", "time"))

        machine_id = _pick_first(
            raw_payload,
            "machine_id",
            "deviceId",
            "device_id",
            "machineId",
        ) or _extract_machine_from_topic(mqtt_topic)
        if not machine_id:
            log.warning("normalise: no machine_id in %s", raw_payload)
            return None

        raw_type = (
            _pick_first(raw_payload, "sensor_type", "sensorType", "type")
            or _extract_type_from_topic(mqtt_topic)
            or ""
        )
        sensor_type = _canonical_sensor_type(raw_type)
        if not sensor_type:
            log.warning("normalise: no sensor_type in %s", raw_payload)
            return None

        value = _pick_first(raw_payload, "sensor_value", "value", "val")
        if value is None:
            log.warning("normalise: no value in %s", raw_payload)
            return None
        sensor_value = float(value)

        raw_unit = str(_pick_first(raw_payload, "unit", "sensor_unit") or "").strip().lower()
        sensor_unit = UNIT_MAP.get(raw_unit, raw_unit or "unknown")

        machine_class = _pick_first(raw_payload, "machine_class", "machineClass") or _infer_machine_class(machine_id)
        plant_zone = _pick_first(raw_payload, "plant_zone", "zone", "area", "location") or _infer_zone(machine_id)
        quality_tag = str(_pick_first(raw_payload, "quality_tag", "qualityTag") or "GOOD").strip().upper()
        alert_severity = str(_pick_first(raw_payload, "alert_severity", "status") or "NORMAL").strip().upper()

        canonical = {
            "timestamp_epoch": ts_epoch,
            "timestamp": datetime.fromtimestamp(ts_epoch, tz=timezone.utc).isoformat(),
            "machine_id": str(machine_id).strip(),
            "machine_class": str(machine_class).strip(),
            "sensor_type": sensor_type,
            "sensor_value": sensor_value,
            "sensor_unit": sensor_unit,
            "quality_tag": quality_tag,
            "plant_zone": str(plant_zone).strip(),
            "alert_severity": alert_severity,
            "source": "EDGE_AGENT",
            "edge_id": str(raw_payload.get("edge_id") or EDGE_ID),
            "normalised_at": datetime.now(timezone.utc).isoformat(),
        }

        for field in REQUIRED_FIELDS:
            if canonical.get(field) in ("", None):
                log.warning("normalise: missing required field %s in %s", field, canonical)
                return None

        return canonical

    except (ValueError, TypeError, KeyError) as exc:
        log.warning("normalise: error processing payload %s: %s", raw_payload, exc)
        return None


def _pick_first(payload: dict, *keys: str) -> Any:
    for key in keys:
        if key in payload and payload[key] is not None:
            return payload[key]
    return None


def _parse_timestamp(ts_raw: Any) -> int:
    if ts_raw is None:
        return int(time.time())
    if isinstance(ts_raw, str):
        return int(datetime.fromisoformat(ts_raw.replace("Z", "+00:00")).timestamp())

    ts_value = float(ts_raw)
    if ts_value > 1e10:
        return int(ts_value / 1000)
    return int(ts_value)


def _canonical_sensor_type(raw_type: str) -> str:
    normalized = str(raw_type).strip().lower()
    if not normalized:
        return ""
    return SENSOR_TYPE_MAP.get(normalized, normalized.upper())


def _extract_machine_from_topic(topic: str) -> str:
    """Extract machine_id from MQTT topic like argus/sensors/COMP-01/vibration."""
    parts = topic.split("/")
    return parts[2] if len(parts) > 2 else ""


def _extract_type_from_topic(topic: str) -> str:
    """Extract a sensor type hint from the MQTT topic last segment."""
    parts = topic.split("/")
    return parts[-1] if parts else ""


def _infer_machine_class(machine_id: str) -> str:
    mid = machine_id.upper()
    if "COMP" in mid:
        return "Compressor"
    if "PUMP" in mid:
        return "Pump"
    if "REFORM" in mid:
        return "Reformer"
    if "REACT" in mid or "HP" in mid:
        return "Reactor"
    if "BFW" in mid or "BOILER" in mid:
        return "Boiler"
    if "GRAN" in mid or "UREA" in mid:
        return "Granulator"
    if "ENV" in mid or "WEATHER" in mid:
        return "Environmental"
    if "FAN" in mid or "BLOW" in mid:
        return "Blower"
    return "Package"


def _infer_zone(machine_id: str) -> str:
    mid = machine_id.upper()
    if "COMP" in mid:
        return "Ammonia-Unit"
    if "REFORM" in mid:
        return "Reformer"
    if "UREA" in mid or "HP" in mid:
        return "Urea-Section"
    if "BFW" in mid or "BOILER" in mid:
        return "Utilities"
    return "General"
