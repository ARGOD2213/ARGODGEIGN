package com.mahindra.iot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Compliance Dashboard API", description = "Compliance placeholders for Sprint 2")
public class ComplianceController {

    @GetMapping("/api/v1/compliance/ocs-score")
    @Operation(summary = "Overall compliance score demo snapshot")
    public ResponseEntity<Map<String, Object>> getOcsScore() {
        double machine = 96.0;
        double human = 93.5;
        double environmental = 92.8;
        double overall = machine * 0.35 + human * 0.35 + environmental * 0.30;

        return ResponseEntity.ok(Map.of(
                "overallComplianceScore", Math.round(overall * 10.0) / 10.0,
                "machineCompliance", machine,
                "humanCompliance", human,
                "environmentalCompliance", environmental,
                "status", "GREEN",
                "period", "last_30_days",
                "formula", "OCS = Machine*0.35 + Human*0.35 + Env*0.30",
                "dataMode", "demo_data",
                "note", "Static demo data for dashboard wiring. Live compliance integration starts in Sprint 5."
        ));
    }

    @GetMapping("/api/v1/compliance/summary")
    @Operation(summary = "Compliance weighted summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        double machine = 92.0;
        double human = 89.0;
        double env = 94.0;
        double ocs = machine * 0.35 + human * 0.35 + env * 0.30;
        return ResponseEntity.ok(Map.of(
                "machineCompliance", machine,
                "humanCompliance", human,
                "environmentCompliance", env,
                "overallComplianceScore", Math.round(ocs * 10.0) / 10.0,
                "formula", "Machine*0.35 + Human*0.35 + Env*0.30"
        ));
    }

    @GetMapping("/api/v1/compliance/sprint8-readiness")
    @Operation(summary = "Sprint 8 readiness pack status")
    public ResponseEntity<Map<String, Object>> getSprint8Readiness() {
        return ResponseEntity.ok(Map.of(
            "status", "REPO_READY_EXTERNAL_VALIDATION_PENDING",
            "opcUaReadiness", Map.of(
                "status", "DOCUMENTED",
                "mode", "READ_ONLY",
                "doc", "docs/SPRINT8_OPCUA_READINESS.md"
            ),
            "loadTesting", Map.of(
                "status", "HARNESS_READY",
                "script", "scripts/load-test-ingest.js",
                "doc", "docs/SPRINT8_LOAD_TEST_PLAN.md"
            ),
            "lopa", Map.of(
                "status", "WORKPACK_READY_EXTERNAL_SIGNOFF_PENDING",
                "register", "docs/sil/SIL_ASSESSMENT_REGISTER.md",
                "template", "docs/sil/LOPA_TEMPLATE.md",
                "packet", "docs/sil/LOPA_EXECUTION_PACKET.md"
            ),
            "productionGate", "External plant access, executed load tests, and formal LOPA signoff still required.",
            "checkedAt", java.time.Instant.now().toString()
        ));
    }

    @GetMapping("/api/v1/compliance/inspections")
    @Operation(summary = "Machine inspection due list")
    public ResponseEntity<List<Map<String, Object>>> getInspections() {
        return ResponseEntity.ok(List.of(
                Map.of("machine", "MCH-VIBR-COMPRESSOR-003", "lastInspected", "2026-03-01", "nextDue", "2026-03-16", "status", "DUE_SOON"),
                Map.of("machine", "MCH-TEMP-BOILER-011", "lastInspected", "2026-02-20", "nextDue", "2026-03-22", "status", "ON_TRACK"),
                Map.of("machine", "SAF-GAS-BOILER-ROOM", "lastInspected", "2026-03-05", "nextDue", "2026-03-25", "status", "ON_TRACK"),
                Map.of("machine", "MCH-VIBR-PUMP-002", "lastInspected", "2026-02-10", "nextDue", "2026-03-12", "status", "OVERDUE")
        ));
    }

    @GetMapping("/api/v1/compliance/environment")
    @Operation(summary = "Environmental compliance snapshot")
    public ResponseEntity<Map<String, Object>> getEnvironment() {
        double nh3StackPpm = 62.5;
        double cpcbLimitPpm = 100.0;
        return ResponseEntity.ok(Map.of(
                "nh3StackPpm", nh3StackPpm,
                "cpcbLimitPpm", cpcbLimitPpm,
                "status", nh3StackPpm <= cpcbLimitPpm ? "COMPLIANT" : "BREACH",
                "timestamp", LocalDate.now().toString()
        ));
    }

    @PostMapping("/api/compliance/form7")
    @Operation(summary = "Generate Form 7 placeholder payload")
    public ResponseEntity<Map<String, Object>> generateForm7() {
        return ResponseEntity.ok(Map.of(
                "form", "Form 7 - Accident/Incident Preliminary Record",
                "generatedAt", LocalDate.now().toString(),
                "status", "DRAFT",
                "note", "Sprint 5 will generate PDF output"
        ));
    }

    @GetMapping(value = "/api/compliance/monthly-report", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Monthly KPI CSV placeholder")
    public ResponseEntity<String> exportMonthlyReport() {
        String csv = "kpi,value\n" +
                "overall_compliance_score,91.5\n" +
                "machine_compliance,92.0\n" +
                "human_compliance,89.0\n" +
                "environment_compliance,94.0\n";
        return ResponseEntity.ok(csv);
    }

    @GetMapping("/report")
    public ResponseEntity<ComplianceReport> getReport(
            @RequestParam(defaultValue = "current") String month) {

        String period = month.equals("current")
            ? java.time.YearMonth.now().toString()
            : month;

        ComplianceReport report = new ComplianceReport(
            "ARGUS Monthly Safety Compliance Report",
            period,
            java.time.Instant.now().toString(),
            94.2, 96.0, 93.5, 92.8, "GREEN",
            "OCS = Machine*0.35 + Human*0.35 + Env*0.30",
            buildKpiSummary(),
            buildAlertSummary(),
            buildSensorSummary(),
            List.of(
                "Compressor COMP-01 vibration elevated — maintenance completed",
                "NH3 zone SYNTHESIS reached 22ppm — ventilation increased"
            ),
            "AI ADVISORY | This report is generated from sensor data and " +
            "rule engine outputs. Human verification required before " +
            "submission to statutory authorities (PESO, Factory Inspector, SPCB).",
            "JSON — print via browser or convert with any PDF tool"
        );
        return ResponseEntity.ok(report);
    }

    private List<Map<String,Object>> buildKpiSummary() {
        return List.of(
            Map.of("kpi","OEE","value",84.7,"unit","%","status","GREEN","threshold",85.0),
            Map.of("kpi","CRITICAL Alert Count","value",3,"unit","count","status","YELLOW","threshold",0),
            Map.of("kpi","Acknowledgement Rate","value",100.0,"unit","%","status","GREEN","threshold",100.0),
            Map.of("kpi","NH3 TWA Max","value",18.4,"unit","ppm","status","GREEN","threshold",25.0),
            Map.of("kpi","WBGT Peak","value",31.2,"unit","degC","status","YELLOW","threshold",30.0),
            Map.of("kpi","Sensor Health GOOD","value",21,"unit","count","status","GREEN","threshold",20),
            Map.of("kpi","Active PTW Compliance","value",100.0,"unit","%","status","GREEN","threshold",100.0)
        );
    }

    private Map<String,Object> buildAlertSummary() {
        return Map.of(
            "CRITICAL",3,"WARNING",12,
            "acknowledged",15,"escalated",0,
            "averageAckTimeMinutes",3.2
        );
    }

    private Map<String,Object> buildSensorSummary() {
        return Map.of(
            "GOOD",21,"SUSPECT",1,"BAD",0,"CALIBRATION_DUE",0
        );
    }

    @GetMapping("/sil-status")
    public ResponseEntity<Map<String,Object>> getSilStatus() {
        Map<String,Object> result = new java.util.LinkedHashMap<>();
        result.put("standard", "IEC 61511:2016 Clause 9.3");
        result.put("platformStatus", "DEMO_PILOT — not connected to live plant");
        result.put("lopaStatus", "NOT_COMPLETED");
        result.put("sifsRegistered", 7);
        result.put("sifsAssessed", 0);
        result.put("sifsNotAssessed", 7);
        result.put("productionGate", "SIL assessment required before production deployment");
        result.put("registerLocation", "docs/sil/SIL_ASSESSMENT_REGISTER.md");
        result.put("lopaTemplate", "docs/sil/LOPA_TEMPLATE.md");
        result.put("lopaExecutionPacket", "docs/sil/LOPA_EXECUTION_PACKET.md");
        result.put("architectureNote", "ADR-001: Rule engine is AWS Lambda. " +
            "Platform is advisory only. Not a certified SIS.");
        result.put("checkedAt", java.time.Instant.now().toString());
        return ResponseEntity.ok(result);
    }

    record ComplianceReport(
        String reportTitle, String period, String generatedAt,
        double ocsScore, double machineCompliance,
        double humanCompliance, double envCompliance,
        String status, String formula,
        List<Map<String,Object>> kpiSummary,
        Map<String,Object> alertSummary,
        Map<String,Object> sensorHealthSummary,
        List<String> notableEvents,
        String declaration, String exportFormat
    ) {}
}
