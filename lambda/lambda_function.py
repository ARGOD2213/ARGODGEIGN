"""
AWS Lambda — IoT MQTT Simulator
Reads CSV from S3 row by row, sends each row to SQS (simulates MQTT)
"""
import json, boto3, csv, io, os, time, hashlib, logging
from datetime import datetime

logger = logging.getLogger()
logger.setLevel(logging.INFO)

s3  = boto3.client('s3',  region_name='ap-south-1')
sqs = boto3.client('sqs', region_name='ap-south-1')

BUCKET    = os.environ.get('S3_BUCKET',    'iot-alert-engine-mahindra')
CSV_KEY   = os.environ.get('CSV_KEY',      'data/iot_sensor_data_100days.csv')
SQS_URL   = os.environ.get('SQS_URL',      '')
BATCH     = int(os.environ.get('BATCH_SIZE', '50'))
STATE_KEY = 'data/injector_state.json'

def lambda_handler(event, context):
    cmd = event.get('command', 'CONTINUE')
    if cmd == 'RESET':
        save_state({'current_row': 0})
        return {'statusCode': 200, 'body': 'Reset to row 0'}
    if cmd == 'STATUS':
        return {'statusCode': 200, 'body': json.dumps(load_state())}
    if cmd == 'INJECT_DAY':
        return inject_day(event.get('day', 1))
    if cmd == 'INJECT_ALL':
        return inject_all()
    return inject_batch()

def inject_batch():
    state = load_state()
    row_start = state.get('current_row', 0)
    rows = read_rows(row_start, BATCH)
    if not rows:
        save_state({'current_row': 0})
        return {'statusCode': 200, 'body': 'Cycle complete. Restarting.'}
    ok = fail = 0
    for row in rows:
        try:
            send(row); ok += 1; time.sleep(0.1)
        except Exception as e:
            logger.error(f"Row failed: {e}"); fail += 1
    save_state({'current_row': row_start + len(rows), 'total_injected': state.get('total_injected', 0) + ok})
    return {'statusCode': 200, 'body': json.dumps({'injected': ok, 'failed': fail})}

def inject_day(day):
    rows = [r for r in read_all() if str(r.get('dayNumber')) == str(day)]
    ok = 0
    for row in rows:
        send(row); ok += 1; time.sleep(0.05)
    return {'statusCode': 200, 'body': json.dumps({'injected': ok, 'day': day})}

def inject_all():
    rows = read_all()
    ok = 0
    for row in rows:
        try: send(row); ok += 1
        except: pass
    return {'statusCode': 200, 'body': json.dumps({'injected': ok})}

def send(row):
    payload = {
        'deviceId':   row.get('deviceId'),
        'sensorType': row.get('sensorType'),
        'value':      float(row.get('value', 0)),
        'unit':       row.get('unit', ''),
        'location':   row.get('location', ''),
        'facilityId': row.get('facilityId', 'FACTORY-HYD-001'),
        'latitude':   float(row.get('latitude', 17.385)),
        'longitude':  float(row.get('longitude', 78.487)),
        'minValue':   float(row['minValue']) if row.get('minValue') else None,
        'maxValue':   float(row['maxValue']) if row.get('maxValue') else None,
        'avgValue':   float(row['avgValue']) if row.get('avgValue') else None,
        'deltaFromPrevious': float(row['deltaFromPrevious']) if row.get('deltaFromPrevious') else None,
        '_source': 'S3_MQTT_SIMULATOR',
        '_originalTimestamp': row.get('timestamp'),
    }
    dedup = hashlib.md5(f"{row.get('deviceId')}_{row.get('timestamp')}".encode()).hexdigest()
    sqs.send_message(
        QueueUrl=SQS_URL,
        MessageBody=json.dumps(payload),
        MessageGroupId=(row.get('deviceId') or 'default')[:128],
        MessageDeduplicationId=dedup
    )

def read_rows(start, count):
    obj = s3.get_object(Bucket=BUCKET, Key=CSV_KEY)
    rows = list(csv.DictReader(io.StringIO(obj['Body'].read().decode('utf-8'))))
    return rows[start:start+count]

def read_all():
    obj = s3.get_object(Bucket=BUCKET, Key=CSV_KEY)
    return list(csv.DictReader(io.StringIO(obj['Body'].read().decode('utf-8'))))

def load_state():
    try:
        obj = s3.get_object(Bucket=BUCKET, Key=STATE_KEY)
        return json.loads(obj['Body'].read())
    except: return {'current_row': 0}

def save_state(state):
    s3.put_object(Bucket=BUCKET, Key=STATE_KEY,
                  Body=json.dumps(state), ContentType='application/json')
