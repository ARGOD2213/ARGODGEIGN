# ARGUS — Event-Driven Industrial IoT Monitoring Platform

Industrial decision-support platform for continuous process facilities. Event-driven alerting, named predictive intelligence models, local machine Q&A, safety workflows, and operator-facing operational dashboards.

## Why This Repo Stands Out

- Full-stack industrial IoT product, not just a model notebook: ingestion, alerting, dashboards, safety logic, handover workflow, PTW, timeline, and compliance views
- Cost-aware AI architecture: local predictive ensemble for machine intelligence, no paid runtime inference API dependency required for the showcase path
- Context-rich analytics: weather-aware, maintenance-aware, and trend-aware scoring with operator-facing explanations instead of black-box labels
- Portfolio-ready UX: multiple dashboards, phone start/stop control path, machine chat, and visibly named models that are easy to discuss in interviews

---

## Sprint Status

| Sprint | Status | Key Deliverables |
|--------|--------|-----------------|
| Sprint 1 | CLOSED | Data foundation, Lambda rule engine, schema |
| Sprint 2 | CLOSED | 5 dashboards, predictive analysis pipeline, sensor health |
| Sprint 3 | CLOSED | Safety backends (WBGT, NH3 TWA, FAID-lite, dispersion) |
| Sprint 4 | CLOSED | Phone control, ACK/escalation, handover, zone risk, timeline |
| Sprint 5 | CLOSED | PTW workflow, compliance export, AI feedback loop |
| Sprint 6 | CLOSED | SIL register, CEMS monitoring, edge layer ADR |
| Sprint 7 | CLOSED | Edge agent, ring buffer, offline rules, ops.html edge status |
| Sprint 8 | IN PROGRESS | OPC-UA readiness pack, load-test harness, and LOPA workpack committed; external validation still required |

---

## Architecture

```
Phone (S3 ops.html)
  → API Gateway + Lambda (start/stop EC2)
    → EC2 t3.micro — Spring Boot 3.2
        → DynamoDB (alerts, PTW, ACK, votes)
        → S3 (handover notes, audit artifacts)
        → Athena (historical sensor data)
        → Local predictive ensemble (named model stack + machine chat)
  → Lambda rule engine (always-on, SQS trigger)
        → SNS iot-alerts (CRITICAL/WARNING notifications)
```

Rule engine is AWS Lambda only (ADR-001).  
Zero write path to OT layer (ADR-002).  
All advisory output uses AiAdvisoryWrapper semantics and remains non-control guidance.  
Athena + S3 for history, DynamoDB for events (ADR-004).  

No ECS. No ALB. No NAT Gateway. No Redis. No MySQL.

---

## Predictive Intelligence Stack

The machine intelligence path now exposes a named ensemble instead of a single generic score:

- `ARGUS Atlas Similarity`: nearest-neighbor failure signature matching against bundled public-dataset-inspired profiles
- `ARGUS Trajectory Breach`: threshold-ratio and slope acceleration model for near-term breach risk
- `ARGUS Operating Envelope`: volatility and ambient-stress model for operating-point instability
- `ARGUS Maintenance Debt`: maintenance-history and sensor-quality penalty model
- `ARGUS Failure Mode Consensus`: agreement model showing which failure family the ensemble is converging on

These models power the machine chat, predictive summary cards, driver breakdowns, and the local advisory path used for portfolio demos without paid runtime inference.

---

## Dashboards (9 live)

| URL | Purpose |
|-----|---------|
| /index.html | Overview — KPI cards, alert feed, platform status |
| /machine.html | Machine grid, trend chart, OEE, ACK, AI advisory |
| /intelligence.html | Predictive ensemble console, model cards, and machine chat |
| /safety.html | WBGT, NH3 zones, fatigue score, zone risk, PTW status |
| /plant.html | Cascade indicator, zone risk cards, weather bar |
| /compliance.html | OCS score, SIL status, CEMS, audit report |
| /handover.html | Shift summary, AI note, operator notes history |
| /timeline.html | Chronological event log per machine |
| /ptw.html | Permit-to-Work issue form and live permit board |

---

## Phone Start (Zero Friction)

1. Open bookmarked S3 ops URL on phone
2. Tap Start ARGUS
3. Wait ~90 seconds for health check
4. Tap Open Dashboard
5. Enter username + password once per session

One-time setup: `bash scripts/deploy-ops-control.sh <elastic-ip>`

---

## Key API Endpoints

```
GET  /api/v1/health
GET  /api/platform/status
GET  /api/v1/dashboard/overview
GET  /api/machines
GET  /api/machines/{id}/alerts
GET  /api/machines/{id}/trend
GET  /api/v1/intelligence/{machineId}/summary
POST /api/v1/intelligence/chat
POST /api/v1/alerts/{id}/acknowledge
GET  /api/v1/alerts/unacknowledged
GET  /api/v1/handover/summary
POST /api/v1/handover/note
GET  /api/v1/zones/risk
GET  /api/v1/timeline
GET  /api/v1/ptw/active
POST /api/v1/ptw/issue
PUT  /api/v1/ptw/{id}/status
GET  /api/v1/compliance/report
GET  /api/v1/compliance/sil-status
GET  /api/v1/compliance/sprint8-readiness
GET  /api/v1/cems/current
POST /api/v1/ai-feedback/vote
```

---

## Safety Standards Referenced

IEC 61511:2016 — SIL assessment register in docs/sil/  
IEC 62443-3-3 — Edge layer cybersecurity (ADR-005)  
ISO 7933:2004 — WBGT heat stress computation  
ACGIH TLV-TWA — NH3 exposure limits (25 ppm)  
EEMUA 191 — Alarm management (max 6/hr/operator)  
ISA-18.2-2016 — Alarm rationalization  
OISD-STD-105 — Permit-to-Work system  
Environment Protection Act 1986 — CEMS monitoring  
Factories Act 1948 — Worker safety compliance  

---

## Local Development

```bash
cp .env.example .env
# Fill ARGUS_USERNAME, ARGUS_PASSWORD, AWS credentials,
# WEATHER_API_KEY
mvn -q -DskipTests package
java -jar target/iot-alert-engine-2.0.0.jar
# Open http://localhost:8080
```

---

## Architecture Decisions

| ADR | Decision |
|-----|---------|
| ADR-001 | Rule engine = AWS Lambda only |
| ADR-002 | Zero write path to OT layer |
| ADR-003 | Generated analysis uses AiAdvisoryWrapper |
| ADR-004 | Athena+S3 for history, DynamoDB for events |
| ADR-005 | Edge layer architecture (Sprint 7 implementation) |

Full ADR documents: `docs/adr/`

---

## Cost Target

~$7 total for 6 months at ~1 hour/day usage.  
EC2 t3.micro start/stop via GitHub Actions.  
Lambda rule engine: always-on at $0 (free tier).  
Predictive intelligence demo path: local-only scoring, no paid inference endpoint required.  
See `docs/SPRINT1_GUARDRAILS.md` for cost controls.
