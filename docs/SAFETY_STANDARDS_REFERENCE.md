# Safety Standards Reference — ARGUS Platform

This document maps international and regional safety standards to their implementation within the ARGUS IoT monitoring platform.

---

## IEC 61511:2016 — Functional Safety: Safety Instrumented Systems

**Scope:** Applies to safety instrumented systems in the process industry  
**Relevance:** ARGUS provides SIL assessment, risk evaluation, and safety function implementation  

### Key Concepts
- **Safety Integrity Level (SIL):** Ranges from 1 (low) to 4 (highest)
- **Safety Function:** An instrumented function designed to achieve/maintain a safe state
- **Dangerous Failure Rate:** Probability of failure per hour (average, ~10 years)

### ARGUS Implementation

| SIL Level | Control Measure | ARGUS Component | Example |
|-----------|-----------------|-----------------|---------|
| SIL-1 | Basic | Spring Boot validator | Temperature > 90°C → Warning alert |
| SIL-2 | Intermediate | Spring Boot + Lambda rule | Temperature > 95°C → Relay open + SNS CRITICAL |
| SIL-3 | High | Lambda + redundant sensor | Pressure > 10 bar → Dual-channel verify + auto-shutdown |
| SIL-4 | Very High | Not currently implemented | Requires certified hardware + formal verification |

### SIL Assessment Process

1. **Hazard Identification** (HAZOP)
   - Identify deviations from design intent (Too hot, Too fast, etc.)
   - Risk matrix: Severity × Likelihood

2. **Target SIL Assignment**
   ```
   Risk = Severity × Likelihood
   If Risk = High → Target SIL ≥ 2
   If Risk = Medium → Target SIL ≥ 1
   ```

3. **Safety Functions Definition**
   - Sensor: Detect condition
   - Logic: Evaluate against threshold
   - Action: Trigger response (relay, alert, shutdown)

4. **Implementation Verification**
   - Code review
   - Unit testing (8-10 test cases per function)
   - Integration testing

### ARGUS Compliance

✓ **SIL Register Maintained:** `docs/sil/IEC_61511_SIL_REGISTER.md`  
✓ **Safety Functions Mapped:** 12 machines × 3-5 functions each = 50+ safety functions  
✓ **Testing Evidence:** 75+ unit tests covering safety backends  
✓ **Monthly Review:** Procedure documented in SIL register  

### References
- IEC 61511-1:2016 — Functional Safety: Safety Systems for the Process Industry
- IEC 61511-2:2016 — Guidelines
- IEC 61511-3:2016 — Example Implementation

---

## IEC 62443-3-3:2013 — Industrial Automation and Control Systems Security

**Scope:** Information security for IACS systems  
**Relevance:** ARGUS edge layer and cloud-to-device communications  

### Security Levels

| Level | Description | ARGUS Implementation |
|-------|-------------|---------------------|
| **SL-1** | Protection against casual/inadvertent violations | Passwords, basic firewall |
| **SL-2** | Protection against intentional violations (low skill) | TLS encryption, role-based access |
| **SL-3** | Protection against intentional violations (intermediate skill) | mTLS, certificate pinning, audit trail |
| **SL-4** | Protection against intentional violations (high skill/resources) | Secure boot, TPM, formal verification (future) |

### Applicable Security Requirements (SRs)

#### SR-2: Structural Security
- **SR-2.1: Password Management**
  ✓ No plaintext passwords
  ✓ OAuth2 JWT tokens for API access
  ✓ Device certificates (X.509) for mTLS

- **SR-2.2: Access Control**
  ✓ Role-based: OPERATOR, ADMIN, DEVICE
  ✓ API key + JWT validation
  ✓ Logged in DynamoDB.access_log

- **SR-2.3: Network Segmentation**
  ✓ OT network isolated from IT (via edge gateway)
  ✓ DMZ firewall rule: outbound HTTPS only to AWS
  ✓ VLAN isolation: IoT sensor network separate

- **SR-2.4: Secure Data Flow**
  ✓ All data in transit: TLS 1.3 minimum
  ✓ MQTT + TLS (port 8883)
  ✓ HTTPS + mTLS (device → cloud)
  ✓ Data at rest: DynamoDB encryption (AES-256)

- **SR-2.5: Secure Remote Access**
  ✓ Bastion host required for SSH
  ✓ No direct internet access to edge gateway
  ✓ SSH via AWS Systems Manager Session Manager only

#### SR-3: Technical Security

- **SR-3.1: Ports, Protocols, Services**
  ✓ Only TLS ports open (443, 8883, 5684)
  ✓ No Telnet, SSH, FTP, HTTP (unencrypted)
  ✓ Service hardening: Run as non-root

- **SR-3.2: Security Event Logging**
  ✓ Immutable audit trail: DynamoDB.security_events
  ✓ Logged events: Auth attempts, rule changes, alerts
  ✓ Retention: 3 years (S3 Glacier after 1 year)

- **SR-3.3: Certificate & Key Management**
  ✓ X.509 certificates: 90-day rotation
  ✓ Private keys: Stored in AWS Secrets Manager
  ✓ Public key pinning: Implemented in edge gateway
  ✓ Key backup: Encrypted in S3 (KMS-protected)

- **SR-3.4: Unplanned Security Patch Management**
  ✓ EC2: Auto-patching via Systems Manager
  ✓ Edge gateway: systemd timer (weekly, 02:00 UTC)
  ✓ Sensors: Firmware OTA required (Sprint 7)

### ARGUS Compliance

✓ **Target SL:** SL-2 (with SL-3 edge gateway + mTLS)  
✓ **Evidence:** `docs/SECURITY_AUDIT_TRAIL.md`  
✓ **Gaps:** SL-4 features (secure boot, TPM) deferred to Sprint 8  

### References
- IEC 62443-3-3:2013 — System Security Requirements and Security Levels

---

## ISO 7933:2004 — Ergonomics of the Thermal Environment

**Scope:** Physical thermal environment assessment  
**Relevance:** WBGT (Wet Bulb Globe Temperature) safety backend  

### WBGT Calculation

```
WBGT = 0.7 × Tnwb + 0.2 × Tg + 0.1 × Tdb

Where:
  Tnwb = Natural Wet Bulb Temperature (psychrometry)
  Tg = Black Globe Temperature
  Tdb = Dry Bulb Temperature (standard thermometer)
```

### ARGUS Implementation

**API Endpoint:**
```
GET /api/v1/safety/wbgt/{zone_id}
```

**Response:**
```json
{
  "zone_id": "UNIT-A-HOT",
  "wbgt_celsius": 32.5,
  "dry_bulb": 38.2,
  "naturally_wet_bulb": 29.4,
  "globe_temp": 52.1,
  "timestamp": "2025-01-04T12:30:00Z",
  "alert_level": "WARNING",
  "threshold_critical": 35.0,
  "threshold_warning": 30.0
}
```

**Alert Logic:**
- CRITICAL: WBGT > 35°C (stop work, cool immediately)
- WARNING: WBGT > 30°C (limit exposure to 1 hour/10 min break)
- OK: WBGT ≤ 30°C (normal work pace)

**Code Location:**
- `src/main/java/com/argodreign/safety/WbgtEngineImpl.java` (85 lines)
- Test coverage: 8 unit tests

### References
- ISO 7933:2004 — Ergonomics of the Thermal Environment
- ACGIH Heat Stress and Strain Guidelines
- NIOHS/ICMR Heat Stress Index

---

## ACGIH TLV-TWA (Time Weighted Average) — Ammonia (NH3)

**Scope:** Occupational exposure limits  
**Relevance:** Ammonia facility monitoring (ARGUS primary use case)  

### Ammonia Exposure Limits

| Metric | Standard | Limit | Duration |
|--------|----------|-------|----------|
| TWA (Time Weighted Average) | ACGIH TLV | **25 ppm** | 8 hours |
| STEL (Short-Term Exposure Limit) | ACGIH TLV | **35 ppm** | 15 minutes |
| IDLH (Immediately Dangerous to Life/Health) | NIOSH | 300 ppm | any duration |

### ARGUS Implementation

**API Endpoint:**
```
GET /api/v1/safety/nh3-twa/{location_id}
```

**Response:**
```json
{
  "location_id": "ABSORBER-UNIT-01",
  "nh3_ppm": 12.4,
  "twa_8h_ppm": 18.7,
  "stel_15m_ppm": 29.8,
  "timestamp": "2025-01-04T12:30:00Z",
  "alert_level": "OK",
  "status": "within_limits",
  "hours_to_twa_threshold": 2.5
}
```

**Alert Logic:**
```
CRITICAL: NH3 > 300 ppm (evacuate immediately)
WARNING: TWA > 25 ppm (report to safety officer)
ALERT: STEL > 35 ppm (restrict area access)
OK: All values within limits
```

**Computation:**
- Real-time: Exponential moving average (EMA)
- TWA: Rolling 8-hour integrated average
- STEL: Sliding 15-minute window

**Code Location:**
- `src/main/java/com/argodreign/safety/AmmoniaExposureService.java` (120 lines)
- Test coverage: 10 unit tests

### References
- ACGIH — Threshold Limit Values (TLVs) for Ammonia
- NIOSH Pocket Guide — Ammonia
- India Factories Act & DGMS Guidelines

---

## EEMUA 191:2021 — Alarm Management System Design

**Scope:** Alarm management lifecycle  
**Relevance:** Alert rationalization, operator workload, SIL assessment  

### Key Principles

1. **Alarm Frequency Limits**
   - Critical alarms: Max 2 per hour per operator
   - Warning alarms: Max 6 per hour per operator
   - Non-urgent: Max 10 per hour per operator

2. **Alarm Response**
   - Operator must be able to respond within time window
   - Typical: 5 seconds for CRITICAL, 5 minutes for WARNING

3. **Alarm Prioritization**
   - CRITICAL: Immediate action required
   - WARNING: Planned investigation
   - INFO: Monitoring only

### ARGUS Implementation

**Alert Configuration:**
```json
{
  "alert_id": "MOTOR-01-TEMP-HIGH",
  "severity": "CRITICAL",
  "frequency_limit_per_hour": 2,
  "response_time_target_seconds": 30,
  "operator_role": "SHIFT_SUPERVISOR",
  "notification_channels": ["SNS", "SMS", "PUSH"],
  "acknowledgment_required": true,
  "auto_escalate_minutes": 10
}
```

**Alert Rationalization (Sprint 5+):**
- Tuning: Adjust thresholds to reduce false positives
- Clustering: Group related alerts
- Suppression: Disable low-value alerts during known events

**Code Location:**
- `src/main/java/com/argodreign/alerts/AlertRationalizationEngine.java`
- Test coverage: 5 unit tests

### References
- EEMUA 191:2021 — Alarm Management System Design
- ISA-18.2-2016 — Management of Alarm Systems for the Process Industries

---

## ISA-18.2-2016 — Alarm Management Systems

**Scope:** Alarm system design, deployment, and maintenance  
**Relevance:** Overarching alarm strategy  

### Lifecycle Phases

1. **Design Phase**
   - Identify alarms from hazard analysis
   - Assign SIL + priority
   - Set response time expectations

2. **Implementation Phase**
   - Configure in SCADA/DCS
   - Document in alarm database
   - Train operators

3. **Operations Phase**
   - Monitor performance metrics
   - Rationalization (tune thresholds)
   - Regular review

4. **Maintenance Phase**
   - Update as plant changes
   - Retirement of obsolete alarms
   - Audit trail

### ARGUS Implementation

**Alarm Database:** DynamoDB.alerts  
**Review Process:** Monthly (documented in SIL register)  
**Metrics Tracked:**
- False positives per day
- Operator response time
- Critical alert count/frequency

### References
- ISA-18.2-2016 — Management of Alarm Systems for the Process Industries

---

## OISD-STD-105:2009 — Guidelines for Permit-to-Work System

**Scope:** Work permits in process industries  
**Relevance:** ARGUS PTW module (Sprint 5)  

### PTW Workflow

```
1. Initiation → Supervisor approves work order
2. Hazard Assessment → Identify risks
3. Control Measures → Apply isolation, PPE
4. Approval → Verify all controls in place
5. Work Execution → Monitor continuously
6. Closure → Verify area returned to safe state
7. Review → Document lessons learned
```

### ARGUS Implementation

**API Endpoints:**
- `POST /api/v1/ptw/issue` — Create permit
- `GET /api/v1/ptw/active` — List live permits
- `PUT /api/v1/ptw/{id}/status` — Update status

**State Machine:**
```
DRAFT → SUBMITTED → APPROVED → ACTIVE → CLOSED → ARCHIVED
```

**UI:** `/ptw.html` (mobile-responsive)

**Code Location:**
- `src/main/java/com/argodreign/ptw/PtwWorkflowService.java`
- Test coverage: 8 unit tests

### References
- OISD-STD-105:2009 — Guidelines for Permit-to-Work System in Petroleum/Petrochemical Operations

---

## Environment Protection Act 1986 (India)

**Scope:** Environmental compliance and emissions monitoring  
**Relevance:** CEMS (Continuous Emissions Monitoring System)  

### Key Requirements

- **Air Emissions:** Monitor NOx, SO₂, PM₂.₅, CO
- **Reporting:** Quarterly to SPCB (State Pollution Control Board)
- **Audit:** Annual third-party verification
- **Record Retention:** 3 years minimum

### ARGUS Implementation

**CEMS Data Model:**
```json
{
  "facility_id": "PLANT-A",
  "measurement_timestamp": "2025-01-04T14:00:00Z",
  "pollutants": {
    "NOx": { "value": 125.4, "unit": "mg/m3" },
    "SO2": { "value": 42.1, "unit": "mg/m3" },
    "PM25": { "value": 31.2, "unit": "ug/m3" },
    "CO": { "value": 11.8, "unit": "mg/m3" }
  },
  "compliance_status": "PASS"
}
```

**API Endpoints:**
- `POST /api/v1/cems/ingest` — Record measurement
- `GET /api/v1/cems/report/{date_range}` — Export compliance report

**Regulatory Limits (NCR-SPCB):**
| Pollutant | Alert (mg/m³) | Critical (mg/m³) |
|-----------|---------------|-----------------|
| NOx | 250 | 400 |
| SO₂ | 100 | 200 |
| PM₂.₅ | 60 | 100 |
| CO | 30 | 50 |

**Code Location:**
- `src/main/java/com/argodreign/cems/CemsDataAdapter.java`
- Test coverage: 9 unit tests

### References
- Environment Protection Act 1986 (Government of India)
- NCR-SPCB CEMS Guidelines
- EPA Part 60 Appendix A (US standard, adopted by many countries)

---

## Factories Act 1948 (India)

**Scope:** Worker safety, occupational health, and welfare  
**Relevance:** Overall safety culture, SIL governance  

### Key Sections

- **Section 38:** Welfare officers (for facilities >500 workers)
- **Section 40:** Occupational safety committees
- **Section 44:** First aid facilities
- **Section 45:** Ambulance room (for >500 workers)
- **Chapter V:** Safety (machinery guards, pressure vessels, etc.)

### ARGUS Compliance

✓ **Section 40 – OSH Committee:** SIL register maintained, monthly review  
✓ **Risk Assessment:** HAZOP process documented  
✓ **Worker Training:** Alarm response procedures notified  
✓ **Audit Trail:** DynamoDB security_events captures all changes  
✓ **Emergency Procedures:** PTW + relays enable safe shutdown  

### References
- Factories Act 1948 (Government of India, as amended)
- DGMS Safety Circulars (Directorate General of Mine Safety)

---

## Summary Table: Standards vs. ARGUS Components

| Standard | Key Requirement | ARGUS Component | Status |
|----------|-----------------|-----------------|--------|
| IEC 61511 | SIL assessment | SIL register + safety backends | ✓ DONE |
| IEC 62443 | Cybersecurity | TLS, mTLS, audit trail | ✓ DONE (SL-2/3) |
| ISO 7933 | Heat stress (WBGT) | WbgtEngineImpl | ✓ DONE |
| ACGIH | NH₃ exposure | AmmoniaExposureService | ✓ DONE |
| EEMUA 191 | Alarm management | Alert rationalization | ✓ DONE |
| ISA-18.2 | Alarm systems | Alert framework | ✓ DONE |
| OISD-105 | PTW workflows | PtwWorkflowService | ✓ DONE |
| EPA 1986 | Emissions monitoring | CemsDataAdapter | ✓ DONE |
| Factories Act | Worker safety | SIL + OSH procedures | ✓ DONE |

---

## Audit & Certification Path

1. **Internal Review** (Monthly)
   - SIL register updates
   - Safety function effectiveness
   - Incident analysis

2. **Third-Party Audit** (Annual)
   - IEC 61511 compliance
   - IEC 62443 security assessment
   - CEMS accuracy (EPA-certified)

3. **Regulatory Inspection** (Per statute)
   - Factory inspector (Factories Act)
   - SPCB (CEMS compliance)
   - TÜV (SIL certification, if pursuing)

---

**Document Date:** 2025-01-04  
**Last Updated:** Sprint 6  
**Next Review:** Post-production, prior to plant connection  
