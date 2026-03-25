# Sprint 8 OPC-UA Readiness Pack

## Purpose

This document defines the repo-ready portion of Sprint 8 for read-only OPC-UA historian integration. It is intentionally limited to a non-control, read-only boundary consistent with ADR-002 and the OT write-path restrictions already documented in this repo.

## Scope

- Read-only historian ingestion path
- Tag normalization into the existing ARGUS sensor schema
- Security and network constraints for DMZ deployment
- Acceptance criteria before connecting to a live facility

## Target Architecture

```text
PLC / DCS -> Historian -> OPC-UA Server (read-only)
                      -> DMZ OPC-UA client / normalizer
                      -> ARGUS ingest API or MQTT bridge
                      -> Lambda rules + Spring dashboards
```

## Security Boundary

- No OPC-UA write capability
- No browsing or method invocation against control assets
- Whitelisted historian endpoint only
- Credentials stored outside source control
- All downstream ARGUS outputs remain advisory unless explicitly implemented in certified plant systems

## Minimum Tag Contract

| Historian Tag | ARGUS Field | Example |
|---|---|---|
| `MachineId` | `deviceId` | `COMP-01` |
| `SensorType` | `sensorType` | `VIBRATION` |
| `Value` | `value` | `6.4` |
| `Unit` | `unit` | `mm/s` |
| `TimestampUtc` | `timestamp` | `2026-03-26T08:15:00Z` |
| `FacilityId` | `facilityId` | `FACTORY-HYD-001` |
| `Location` | `location` | `SYNTHESIS` |

## Machine-Class Priorities

1. Ammonia synthesis compressor
2. Boiler feed water pump
3. Reformer
4. Urea HP section
5. Instrument air system

## Acceptance Criteria

- Historian endpoint reachable from DMZ only
- Test tags mapped to existing ARGUS schema without manual data edits
- Timestamp skew under 5 seconds for live tags
- Read-only credentials verified and documented
- No write path observed in security review
- At least 24 hours of historian replay ingested successfully in a non-production environment

## Cutover Checklist

- Confirm historian IP / hostname and certificate chain
- Validate tag naming conventions with plant instrumentation team
- Dry-run 10 representative tags per critical machine class
- Capture latency, dropped-message rate, and parse-failure rate
- Archive test evidence in Sprint 8 closure notes before production approval

## Out of Scope

- Direct PLC/DCS writes
- Setpoint changes
- Safety-instrumented action execution
- Production sign-off without site validation
