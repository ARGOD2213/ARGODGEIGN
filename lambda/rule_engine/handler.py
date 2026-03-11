import json
import logging
import os
import time
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

import boto3
from botocore.exceptions import ClientError

LOGGER = logging.getLogger()
LOGGER.setLevel(logging.INFO)

SEVERITY_RANK = {
    "NORMAL": 0,
    "WARNING": 1,
    "CRITICAL": 2,
    "P1": 3
}

RULES_BUCKET = os.environ.get("RULES_BUCKET", "iot-alert-engine-mahindra")
RULES_KEY = os.environ.get("RULES_KEY", "config/rules.json")
SNS_TOPIC_ARN = os.environ.get("SNS_TOPIC_ARN", "")
DEDUP_TABLE = os.environ.get("DEDUP_TABLE", "")
ALERT_TABLE = os.environ.get("ALERT_TABLE", "iot-sensor-events")
PUBLISH_MIN_SEVERITY_ENV = os.environ.get("PUBLISH_MIN_SEVERITY", "").upper()
DEDUP_WINDOW_ENV = os.environ.get("DEDUP_WINDOW_SECONDS", "")

S3_CLIENT = boto3.client("s3")
SNS_CLIENT = boto3.client("sns")
DDB_CLIENT = boto3.client("dynamodb")

_RULE_CACHE: Dict[str, Any] = {}
_RULE_CACHE_TS = 0
_RULE_CACHE_TTL_SECONDS = 300


def _upper_or_empty(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip().upper()


def _to_float(value: Any) -> Optional[float]:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _severity_rank(severity: str) -> int:
    return SEVERITY_RANK.get(_upper_or_empty(severity), -1)


def _load_rules_config(force_refresh: bool = False) -> Dict[str, Any]:
    global _RULE_CACHE, _RULE_CACHE_TS

    now = int(time.time())
    if not force_refresh and _RULE_CACHE and (now - _RULE_CACHE_TS) < _RULE_CACHE_TTL_SECONDS:
        return _RULE_CACHE

    response = S3_CLIENT.get_object(Bucket=RULES_BUCKET, Key=RULES_KEY)
    body = response["Body"].read().decode("utf-8")
    config = json.loads(body)

    if "rules" not in config or not isinstance(config["rules"], list):
        raise ValueError("rules.json must contain a top-level 'rules' array")

    _RULE_CACHE = config
    _RULE_CACHE_TS = now
    return config


def _extract_payload(record_or_event: Dict[str, Any]) -> Dict[str, Any]:
    def _parse_json_dict(text: str) -> Dict[str, Any]:
        candidates = [text]
        stripped = text.strip()
        if stripped.startswith("'") and stripped.endswith("'") and len(stripped) > 1:
            candidates.append(stripped[1:-1])
        candidates.append(stripped.replace('\\"', '"'))
        candidates.append(stripped.replace("'", '"'))

        for candidate in candidates:
            try:
                parsed = json.loads(candidate)
                if isinstance(parsed, str):
                    nested = json.loads(parsed)
                    if isinstance(nested, dict):
                        return nested
                if isinstance(parsed, dict):
                    return parsed
            except json.JSONDecodeError:
                continue

        raise json.JSONDecodeError("Unable to parse record body into JSON object", text, 0)

    if "body" in record_or_event:
        body = record_or_event.get("body", "{}")
        if isinstance(body, str):
            payload = _parse_json_dict(body)
        elif isinstance(body, dict):
            payload = body
        else:
            payload = {}
    else:
        payload = record_or_event

    # SNS forwarded payloads can come wrapped as {"Message":"{...json...}"}
    if isinstance(payload, dict) and "Message" in payload and isinstance(payload["Message"], str):
        try:
            payload = json.loads(payload["Message"])
        except json.JSONDecodeError:
            pass
    return payload if isinstance(payload, dict) else {}


def _canonicalize_event(payload: Dict[str, Any]) -> Dict[str, Any]:
    machine_id = payload.get("machine_id") or payload.get("deviceId") or payload.get("machineId") or "UNKNOWN_MACHINE"
    machine_class = payload.get("machine_class") or payload.get("machineClass") or ""
    sensor_type = payload.get("sensor_type") or payload.get("sensorType") or "UNKNOWN_SENSOR"
    sensor_value = payload.get("sensor_value")
    if sensor_value is None:
        sensor_value = payload.get("value")
    unit = payload.get("sensor_unit") or payload.get("unit") or ""
    location = payload.get("plant_zone") or payload.get("location") or payload.get("area") or ""
    ts = payload.get("timestamp") or payload.get("_originalTimestamp") or datetime.now(timezone.utc).isoformat()

    return {
        "machine_id": str(machine_id).strip(),
        "machine_class": str(machine_class).strip(),
        "sensor_type": _upper_or_empty(sensor_type),
        "sensor_value": _to_float(sensor_value),
        "sensor_unit": str(unit).strip(),
        "plant_zone": str(location).strip(),
        "timestamp": str(ts),
        "raw_payload": payload
    }


def _operator_match(operator: str, value: float, threshold: float) -> bool:
    op = _upper_or_empty(operator)
    if op == "GT":
        return value > threshold
    if op == "GTE":
        return value >= threshold
    if op == "LT":
        return value < threshold
    if op == "LTE":
        return value <= threshold
    if op == "EQ":
        return value == threshold
    if op == "NEQ":
        return value != threshold
    raise ValueError(f"Unsupported operator '{operator}' in rules.json")


def _rule_matches(rule: Dict[str, Any], event: Dict[str, Any]) -> bool:
    if event["sensor_value"] is None:
        return False

    sensor_type = _upper_or_empty(rule.get("sensor_type"))
    if sensor_type and sensor_type != event["sensor_type"]:
        return False

    rule_machine_class = _upper_or_empty(rule.get("machine_class", ""))
    if rule_machine_class and rule_machine_class != _upper_or_empty(event["machine_class"]):
        return False

    operator = rule.get("operator", "GT")
    threshold = _to_float(rule.get("threshold"))
    if threshold is None:
        return False

    return _operator_match(operator, event["sensor_value"], threshold)


def _pick_highest_severity_rule(matched_rules: List[Dict[str, Any]]) -> Dict[str, Any]:
    return sorted(
        matched_rules,
        key=lambda r: (_severity_rank(str(r.get("severity", "NORMAL"))), _to_float(r.get("threshold")) or 0),
        reverse=True
    )[0]


def _min_publish_severity(config: Dict[str, Any]) -> str:
    if PUBLISH_MIN_SEVERITY_ENV:
        return PUBLISH_MIN_SEVERITY_ENV
    return _upper_or_empty(config.get("publish_min_severity") or "WARNING")


def _dedup_window_seconds(config: Dict[str, Any]) -> int:
    if DEDUP_WINDOW_ENV:
        try:
            return max(60, int(DEDUP_WINDOW_ENV))
        except ValueError:
            LOGGER.warning("Invalid DEDUP_WINDOW_SECONDS env '%s'. Falling back to config.", DEDUP_WINDOW_ENV)
    return int(config.get("dedup_window_seconds", 600))


def _should_publish(severity: str, min_severity: str) -> bool:
    return _severity_rank(severity) >= _severity_rank(min_severity)


def _dedup_key(event: Dict[str, Any], severity: str) -> str:
    return f"{event['machine_id']}#{event['sensor_type']}#{severity}"


def _dedup_pass(dedup_key: str, window_seconds: int) -> bool:
    if not DEDUP_TABLE:
        return True

    now = int(time.time())
    expires_at = now + window_seconds

    try:
        DDB_CLIENT.put_item(
            TableName=DEDUP_TABLE,
            Item={
                "dedup_key": {"S": dedup_key},
                "created_at": {"N": str(now)},
                "expires_at": {"N": str(expires_at)}
            },
            ConditionExpression="attribute_not_exists(dedup_key) OR expires_at < :now",
            ExpressionAttributeValues={
                ":now": {"N": str(now)}
            }
        )
        return True
    except ClientError as err:
        if err.response.get("Error", {}).get("Code") == "ConditionalCheckFailedException":
            return False
        raise


def _build_alert(event: Dict[str, Any], matched_rules: List[Dict[str, Any]]) -> Dict[str, Any]:
    top_rule = _pick_highest_severity_rule(matched_rules)
    severity = _upper_or_empty(top_rule.get("severity"))
    threshold = _to_float(top_rule.get("threshold")) or 0.0
    now_iso = datetime.now(timezone.utc).isoformat()
    alert_id = f"{event['machine_id']}:{event['sensor_type']}:{int(time.time())}"

    return {
        "alert_id": alert_id,
        "generated_at": now_iso,
        "severity": severity,
        "machine_id": event["machine_id"],
        "machine_class": event["machine_class"],
        "sensor_type": event["sensor_type"],
        "sensor_value": event["sensor_value"],
        "sensor_unit": event["sensor_unit"],
        "plant_zone": event["plant_zone"],
        "event_timestamp": event["timestamp"],
        "warning_threshold": threshold,
        "critical_threshold": threshold,
        "title": top_rule.get("title", "Rule threshold matched"),
        "recommendation": top_rule.get("recommendation", "Review asset immediately."),
        "reference": top_rule.get("reference", ""),
        "matched_rule_ids": [r.get("id") for r in matched_rules if r.get("id")],
        "source": "ARGODREIGN_RULE_ENGINE",
        "raw_payload": event["raw_payload"]
    }


def _publish_alert(alert: Dict[str, Any]) -> str:
    if not SNS_TOPIC_ARN:
        raise ValueError("SNS_TOPIC_ARN environment variable is required")

    subject = f"[{alert['severity']}] {alert['machine_id']} {alert['sensor_type']}"
    response = SNS_CLIENT.publish(
        TopicArn=SNS_TOPIC_ARN,
        Subject=subject[:100],
        Message=json.dumps(alert, default=str)
    )
    return response.get("MessageId", "")


def _persist_alert(alert: Dict[str, Any]) -> None:
    DDB_CLIENT.put_item(
        TableName=ALERT_TABLE,
        Item={
            "deviceId": {"S": str(alert["machine_id"])},
            "timestamp": {"S": str(alert["generated_at"])},
            "sensorType": {"S": str(alert["sensor_type"])},
            "sensorCategory": {"S": "RuleEngine"},
            "unit": {"S": str(alert.get("sensor_unit", ""))},
            "value": {"N": str(alert.get("sensor_value", 0.0))},
            "status": {"S": str(alert["severity"])},
            "location": {"S": str(alert.get("plant_zone", ""))},
            "facilityId": {"S": "FACTORY-HYD-001"},
            "processedAt": {"S": str(alert["generated_at"])},
            "alertId": {"S": str(alert["alert_id"])},
            "warningThreshold": {"N": str(alert.get("warning_threshold", 0.0))},
            "criticalThreshold": {"N": str(alert.get("critical_threshold", 0.0))},
            "aiIncidentSummary": {"S": "[AI ADVISORY | Not a control action | Rule engine has final authority]"},
            "aiRiskScore": {"N": "0"},
            "llmConsensus": {"S": "RULE_ENGINE_ONLY"}
        }
    )


def _iter_records(event: Dict[str, Any]) -> List[Dict[str, Any]]:
    records = event.get("Records")
    if isinstance(records, list) and records:
        return records
    return [event]


def lambda_handler(event: Dict[str, Any], context: Any) -> Dict[str, Any]:
    config = _load_rules_config()
    rules = config.get("rules", [])
    min_severity = _min_publish_severity(config)
    dedup_window = _dedup_window_seconds(config)

    summary = {
        "records_received": 0,
        "alerts_published": 0,
        "alerts_dedup_suppressed": 0,
        "alerts_below_min_severity": 0,
        "no_rule_match": 0,
        "parse_failures": 0
    }

    for record in _iter_records(event):
        summary["records_received"] += 1
        try:
            payload = _extract_payload(record)
            canonical = _canonicalize_event(payload)

            matched_rules = [rule for rule in rules if _rule_matches(rule, canonical)]
            if not matched_rules:
                summary["no_rule_match"] += 1
                continue

            alert = _build_alert(canonical, matched_rules)
            severity = alert["severity"]

            if not _should_publish(severity, min_severity):
                summary["alerts_below_min_severity"] += 1
                continue

            key = _dedup_key(canonical, severity)
            if not _dedup_pass(key, dedup_window):
                summary["alerts_dedup_suppressed"] += 1
                continue

            message_id = _publish_alert(alert)
            _persist_alert(alert)
            summary["alerts_published"] += 1
            LOGGER.info("Published alert %s (%s)", message_id, alert["alert_id"])

        except Exception as exc:  # pylint: disable=broad-except
            summary["parse_failures"] += 1
            LOGGER.exception("Record processing failed: %s", exc)

    return {
        "statusCode": 200,
        "body": json.dumps(summary)
    }
