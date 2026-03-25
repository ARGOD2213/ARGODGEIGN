# LOPA Execution Packet

## Purpose

This packet turns the existing SIL and LOPA placeholders into a concrete work package for plant review or an external consultant.

## Mandatory SIFs For First Pass

1. `SIF-01` NH3 synthesis compressor trip on high-high vibration
2. `SIF-02` NH3 area evacuation alert on high ppm
3. `SIF-03` Urea HP section overpressure alert

## Required Inputs

- Last 3-5 years of relevant incident / trip history
- Current HAZOP nodes and consequence categories
- Existing protection layers and proof-test assumptions
- Plant risk matrix and tolerable event frequency definitions
- Instrumentation ownership for each initiating event

## Consultant Deliverables

- Validated initiating event frequency
- Independent protection layer credits
- Residual risk calculation
- Required risk reduction factor
- Final SIL target recommendation
- Action list for any instrumentation or architecture gaps

## ARGUS Boundary Statement

ARGUS is not a certified SIS in its present repo state. It provides alerting, analysis, dashboards, and workflow support. Any future safety role must be assessed against plant architecture and certified independently.

## Repo References

- `docs/sil/SIL_ASSESSMENT_REGISTER.md`
- `docs/sil/LOPA_TEMPLATE.md`
- `docs/SPRINT8_OPCUA_READINESS.md`
- `docs/security/OT_WRITE_PATH_ENFORCEMENT.md`
