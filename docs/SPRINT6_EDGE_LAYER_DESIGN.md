# ARGUS Edge Layer — Design Document
**Sprint:** 6 (architecture) | Sprint 7 (implementation)  
**Standard:** IEC 62443-3-3 Industrial Cybersecurity  
**Status:** DESIGNED — not yet implemented  

## Overview
The edge layer bridges the plant OT network and the ARGUS cloud platform. It handles protocol translation, local buffering, offline alarm capability, and cybersecurity zone separation.

## Key Design Principles
1. **Read-only from OT** — the edge agent never writes to any PLC, DCS, or field device (ADR-002)
2. **Offline-capable** — critical alarms fire locally even when cloud connectivity fails
3. **Zero trust boundary** — edge layer assumes OT network is compromised; all data is validated before ingestion
4. **Schema-enforced** — all data must match docs/schema/sensor_event_schema.json before ingestion
5. **Cost-zero AWS impact** — edge hardware is on-premise; only SNS heartbeat adds minimal cloud cost

## Protocol Translation Map

| Plant Protocol | Edge Component | Output |
|---------------|----------------|--------|
| OPC-UA (historian) | OPC-UA client | JSON events |
| Modbus RTU | Modbus bridge (future) | JSON events |
| MQTT (existing sim) | Pass-through | JSON events |
| 4-20mA (analog) | Edge DAQ (future) | JSON events |

## Data Flow Diagram

```
[OT Layer]
  PLC/DCS → Historian (OPC-UA read-only)
              ↓
[Edge Layer — isolated PC]
  OPC-UA Client → Normaliser → Schema Validator
                                  ↓
                          ┌───────────────┐
                          │  Ring Buffer  │ ← always writes
                          │  (SQLite 4hr) │
                          └───────┬───────┘
                                  ↓
                  ┌─────────────────┴──────────────────┐
                  │ Online                              │ Offline
                  ↓                                    ↓
  SQS iot-events.fifo (cloud)          Local Rule Engine
          ↓                                    ↓
  Lambda rule engine                  Local alert log
  DynamoDB + SNS                      (replays when reconnected)
```

## Ring Buffer Specification
- Storage: SQLite database on edge SSD
- Capacity: 4 hours at 22 sensors × 1Hz = ~316,800 events
- Schema: matches sensor_event_schema.json exactly
- Replay: on cloud reconnection, drain oldest first
- Deduplication: use existing argodreign-dedup DynamoDB table

## Health Beacon
- Frequency: every 60 seconds
- Target: SNS iot-alerts with subject EDGE_HEARTBEAT
- ops.html: if no heartbeat for 5 minutes, show EDGE_OFFLINE warning banner
- Lambda processes heartbeat and updates platform status

## Security Configuration
**OT NIC (eth0):**
- IP: 192.168.10.x (OT VLAN)
- Firewall: ACCEPT in from historian IP only
- Firewall: DROP all other inbound
- No outbound internet access

**IT NIC (eth1):**
- IP: DHCP from IT network
- Firewall: ACCEPT out to AWS endpoints only
  (sqs.ap-south-1.amazonaws.com, sns.ap-south-1.amazonaws.com)
- Firewall: DROP all other outbound
- No inbound connections
