import boto3
import json
import datetime
import os
from boto3.dynamodb.conditions import Attr

ec2 = boto3.client("ec2", region_name="ap-south-1")
ssm = boto3.client("ssm", region_name="ap-south-1")
INSTANCE_ID = os.environ.get("INSTANCE_ID", "i-0761b14159f2e2a3f")
ELASTIC_IP = os.environ.get("ELASTIC_IP", "")

_cached_password = None


def get_control_password():
    global _cached_password
    if _cached_password is None:
        response = ssm.get_parameter(
            Name="/argus/ops/control_password",
            WithDecryption=True,
        )
        _cached_password = response["Parameter"]["Value"]
    return _cached_password


def check_auth(event):
    body = {}
    try:
        body = json.loads(event.get("body") or "{}")
    except Exception:
        pass

    headers = event.get("headers") or {}
    provided = body.get("password") or headers.get("x-ops-password", "")
    return provided == get_control_password()


def get_status():
    response = ec2.describe_instances(InstanceIds=[INSTANCE_ID])
    instance = response["Reservations"][0]["Instances"][0]
    state = instance["State"]["Name"]
    launch = instance.get("LaunchTime")
    uptime = 0

    if state == "running" and launch:
        uptime = int(
            (datetime.datetime.now(datetime.timezone.utc) - launch).seconds / 60
        )

    def get_edge_status():
        """
        Check DynamoDB for the last EDGE_HEARTBEAT record.
        Returns ONLINE if heartbeat is within 5 minutes,
        OFFLINE if older than 5 minutes, UNKNOWN if no record exists.
        """
        try:
            ddb = boto3.resource("dynamodb", region_name="ap-south-1")
            table = ddb.Table("iot-sensor-events")
            resp = table.scan(
                FilterExpression=Attr("alertId").begins_with("edge_heartbeat#")
            )
            items = resp.get("Items", [])
            if not items:
                return "UNKNOWN"

            latest = max(items, key=lambda item: item.get("timestamp", ""))
            ts_str = latest.get("timestamp", "")
            if not ts_str:
                return "UNKNOWN"

            ts = datetime.datetime.fromisoformat(ts_str.replace("Z", "+00:00"))
            age_seconds = (datetime.datetime.now(datetime.timezone.utc) - ts).total_seconds()
            return "ONLINE" if age_seconds < 300 else "OFFLINE"
        except Exception:
            return "UNKNOWN"

    return {
        "instanceId": INSTANCE_ID,
        "state": state,
        "uptimeMinutes": uptime,
        "appUrl": f"http://{ELASTIC_IP}:8080" if state == "running" else None,
        "checkedAt": datetime.datetime.utcnow().isoformat() + "Z",
        "edgeStatus": get_edge_status(),
    }


def cors_headers():
    return {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type,x-ops-password",
    }


def respond(code, body):
    return {
        "statusCode": code,
        "headers": cors_headers(),
        "body": json.dumps(body),
    }


def lambda_handler(event, context):
    method = event.get("requestContext", {}).get("http", {}).get("method", "GET")
    path = event.get("requestContext", {}).get("http", {}).get("path", "/")

    if method == "OPTIONS":
        return respond(200, {})

    if path == "/ops/status":
        return respond(200, get_status())

    if not check_auth(event):
        return respond(401, {"error": "Unauthorized"})

    if path == "/ops/start":
        state = get_status()["state"]
        if state == "stopped":
            ec2.start_instances(InstanceIds=[INSTANCE_ID])
            return respond(
                200,
                {
                    "action": "start",
                    "message": "Starting. App ready in ~90 seconds.",
                },
            )
        return respond(
            200,
            {
                "action": "start",
                "message": f"Instance is {state} — no action taken.",
            },
        )

    if path == "/ops/stop":
        ec2.stop_instances(InstanceIds=[INSTANCE_ID])
        return respond(
            200,
            {
                "action": "stop",
                "message": "Stopping. Billing stops in ~30 seconds.",
            },
        )

    return respond(404, {"error": "Unknown route"})
