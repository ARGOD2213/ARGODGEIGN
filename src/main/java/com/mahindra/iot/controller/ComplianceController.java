package com.mahindra.iot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Compliance Dashboard API", description = "Compliance placeholders for Sprint 2")
public class ComplianceController {

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
}
