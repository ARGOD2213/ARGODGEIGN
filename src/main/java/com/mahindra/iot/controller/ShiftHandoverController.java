package com.mahindra.iot.controller;

import com.mahindra.iot.service.ShiftHandoverService;
import com.mahindra.iot.service.ShiftHandoverService.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/handover")
public class ShiftHandoverController {

    private final ShiftHandoverService handoverService;

    public ShiftHandoverController(ShiftHandoverService handoverService) {
        this.handoverService = handoverService;
    }

    /**
     * GET /api/v1/handover/summary?shiftHours=8
     *
     * Returns shift summary for the last N hours including
     * alert counts, top machines, and AI handover note.
     * Default shift is 8 hours.
     */
    @GetMapping("/summary")
    public ResponseEntity<HandoverSummary> summary(
            @RequestParam(defaultValue = "8") int shiftHours) {
        if (shiftHours < 1 || shiftHours > 24) shiftHours = 8;
        return ResponseEntity.ok(handoverService.generateSummary(shiftHours));
    }

    /**
     * POST /api/v1/handover/note
     * Body: { "operatorId": "OP-01", "note": "COMP-01 vibration elevated" }
     *
     * Saves operator note to S3.
     * ADR-004: document records go to S3, not DynamoDB.
     */
    @PostMapping("/note")
    public ResponseEntity<Map<String, String>> saveNote(
            @RequestBody Map<String, String> body) {
        String operatorId = body.getOrDefault("operatorId", "UNKNOWN");
        String note       = body.getOrDefault("note", "");
        if (note.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Note cannot be empty"));
        }
        handoverService.saveNote(operatorId, note);
        return ResponseEntity.ok(Map.of(
            "status",     "saved",
            "operatorId", operatorId,
            "message",    "Note saved to shift log"
        ));
    }

    /**
     * GET /api/v1/handover/notes?date=2026-03-22
     *
     * Returns operator notes for a given date from S3.
     * Date format: YYYY-MM-DD. Defaults to today.
     */
    @GetMapping("/notes")
    public ResponseEntity<List<String>> getNotes(
            @RequestParam(required = false) String date) {
        if (date == null || date.isBlank()) {
            date = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        }
        return ResponseEntity.ok(handoverService.getNotes(date));
    }
}
