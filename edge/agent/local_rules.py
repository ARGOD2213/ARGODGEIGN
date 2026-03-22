"""
ARGUS Edge Agent - Offline Local Rule Engine
Evaluates sensor events against rules.json thresholds
when cloud connectivity is unavailable.
Writes alerts to a local log file and can be replayed later.
ADR-001: this is a FALLBACK REPLICA of the Lambda rule engine.
         Lambda remains authoritative when cloud is available.
ADR-002: no write path to OT - reads events, writes local log only.
"""
import json
import logging
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from .config import LOG_PATH, RULES_PATH

log = logging.getLogger(__name__)
SEVERITY_RANK = {"NORMAL": 0, "WARNING": 1, "CRITICAL": 2}


class LocalRuleEngine:
    def __init__(self, rules_path: str = RULES_PATH):
        self.rules_path = rules_path
        self.rules = []
        self.alert_log_path = Path(LOG_PATH).with_name("edge-alerts.jsonl")
        self.alert_log_path.parent.mkdir(parents=True, exist_ok=True)
        self._load_rules()

    def _load_rules(self) -> None:
        """Load rules from rules.json. Reload on each evaluation to pick up changes."""
        try:
            with open(self.rules_path, "r", encoding="utf-8") as handle:
                payload = json.load(handle)
            if isinstance(payload, dict):
                self.rules = payload.get("rules", [])
            elif isinstance(payload, list):
                self.rules = payload
            else:
                self.rules = []
            log.info("LocalRuleEngine: loaded %d rules from %s", len(self.rules), self.rules_path)
        except (FileNotFoundError, json.JSONDecodeError) as exc:
            log.error("LocalRuleEngine: could not load rules: %s", exc)
            self.rules = []

    def evaluate(self, event: dict) -> Optional[dict]:
        """
        Evaluate a normalised sensor event against all rules.
        Returns an alert dict if a CRITICAL or WARNING threshold is breached.
        """
        self._load_rules()
        matches = []

        for rule in self.rules:
            alert = self._evaluate_rule(rule, event)
            if alert:
                matches.append(alert)

        if not matches:
            return None

        alert = max(matches, key=lambda item: SEVERITY_RANK.get(item["severity"], 0))
        self._append_alert_log(alert)
        log.warning(
            "LOCAL ALERT %s: %s/%s = %s %s",
            alert["severity"],
            alert["machineId"],
            alert["sensorType"],
            alert["value"],
            alert["unit"],
        )
        return alert

    def evaluate_batch(self, events: list) -> list:
        """Evaluate a list of events. Returns list of alerts (may be empty)."""
        alerts = []
        for event in events:
            alert = self.evaluate(event)
            if alert:
                alerts.append(alert)
        return alerts

    def _evaluate_rule(self, rule: dict, event: dict) -> Optional[dict]:
        sensor_type = str(event.get("sensor_type", "")).upper()
        sensor_value = float(event.get("sensor_value", 0))
        machine_class = str(event.get("machine_class", "")).upper()

        rule_sensor_type = str(rule.get("sensor_type") or rule.get("sensor") or "").upper()
        if rule_sensor_type and rule_sensor_type != sensor_type:
            return None

        rule_machine_class = str(rule.get("machine_class", "")).upper()
        if rule_machine_class and rule_machine_class != machine_class:
            return None

        if "threshold" in rule:
            severity = self._severity_from_lambda_rule(rule, sensor_value)
        else:
            severity = self._severity_from_local_rule(rule, sensor_value)
        if not severity:
            return None

        title = rule.get("title") or rule.get("description") or "Offline threshold breached"
        recommendation = rule.get("recommendation") or rule.get("reference") or "Inspect asset immediately."

        return {
            "alertId": f"local#{event.get('machine_id', '')}#{int(time.time())}",
            "machineId": event.get("machine_id", ""),
            "sensorType": sensor_type,
            "value": str(sensor_value),
            "unit": event.get("sensor_unit", rule.get("unit", "")),
            "severity": severity,
            "zone": event.get("plant_zone", ""),
            "source": "LOCAL_RULE_ENGINE - cloud unavailable",
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "title": title,
            "recommendation": recommendation,
            "ruleId": rule.get("id", ""),
            "note": "AI ADVISORY | Not a control action | Rule engine has final authority",
        }

    def _severity_from_lambda_rule(self, rule: dict, sensor_value: float) -> Optional[str]:
        operator = str(rule.get("operator", "GT")).upper()
        threshold = float(rule.get("threshold", 0))
        if self._matches(operator, sensor_value, threshold):
            return str(rule.get("severity", "WARNING")).upper()
        return None

    def _severity_from_local_rule(self, rule: dict, sensor_value: float) -> Optional[str]:
        operator = str(rule.get("op", "gt")).upper()
        crit_thresh = rule.get("crit")
        warn_thresh = rule.get("warn")

        if crit_thresh is not None and self._matches(operator, sensor_value, float(crit_thresh)):
            return "CRITICAL"
        if warn_thresh is not None and self._matches(operator, sensor_value, float(warn_thresh)):
            return "WARNING"
        return None

    def _matches(self, operator: str, sensor_value: float, threshold: float) -> bool:
        if operator == "GT":
            return sensor_value > threshold
        if operator == "GTE":
            return sensor_value >= threshold
        if operator == "LT":
            return sensor_value < threshold
        if operator == "LTE":
            return sensor_value <= threshold
        if operator == "EQ":
            return sensor_value == threshold
        return False

    def _append_alert_log(self, alert: dict) -> None:
        try:
            with open(self.alert_log_path, "a", encoding="utf-8") as handle:
                handle.write(json.dumps(alert) + "\n")
        except OSError as exc:
            log.error("LocalRuleEngine: could not append alert log: %s", exc)
