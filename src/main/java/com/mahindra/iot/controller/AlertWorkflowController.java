package com.mahindra.iot.controller;

import com.mahindra.iot.service.AlertAcknowledgementService;
import com.mahindra.iot.service.AlertAcknowledgementService.AckResult;
import com.mahindra.iot.service.AlertAcknowledgementService.UnacknowledgedAlert;
import com.mahindra.iot.service.AdvisoryWorkflowService;
import com.mahindra.iot.service.AdvisoryWorkflowService.AdvisoryDecision;
import com.mahindra.iot.service.AdvisoryWorkflowService.AdvisoryEvidence;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertWorkflowController {

    private final AlertAcknowledgementService ackService;
    private final AdvisoryWorkflowService advisoryWorkflowService;

    public AlertWorkflowController(AlertAcknowledgementService ackService,
                                   AdvisoryWorkflowService advisoryWorkflowService) {
        this.ackService = ackService;
        this.advisoryWorkflowService = advisoryWorkflowService;
    }

    @PostMapping("/{alertId}/acknowledge")
    public ResponseEntity<AckResult> acknowledge(
            @PathVariable String alertId,
            @RequestBody Map<String, String> body) {
        String operatorId = body.getOrDefault("operatorId", "UNKNOWN");
        String note = body.getOrDefault("note", "");
        try {
            return ResponseEntity.ok(ackService.acknowledge(alertId, operatorId, note));
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/unacknowledged")
    public ResponseEntity<List<UnacknowledgedAlert>> unacknowledged() {
        return ResponseEntity.ok(ackService.getUnacknowledged());
    }

    @PostMapping("/{alertId}/escalate")
    public ResponseEntity<Map<String, String>> escalate(
            @PathVariable String alertId,
            @RequestBody Map<String, String> body) {
        ackService.escalate(
            alertId,
            body.getOrDefault("machineId", "UNKNOWN"),
            body.getOrDefault("sensorType", "UNKNOWN"),
            body.getOrDefault("value", "?")
        );
        return ResponseEntity.ok(Map.of(
            "alertId", alertId,
            "action", "ESCALATED",
            "message", "Escalation notification sent via SNS"
        ));
    }

    @GetMapping("/{alertId}/ack-status")
    public ResponseEntity<Map<String, Object>> ackStatus(
            @PathVariable String alertId) {
        boolean acked = ackService.isAcknowledged(alertId);
        return ResponseEntity.ok(Map.of(
            "alertId", alertId,
            "acknowledged", acked
        ));
    }

    @PostMapping("/{alertId}/review")
    public ResponseEntity<?> review(
            @PathVariable String alertId,
            @RequestBody Map<String, String> body) {
        return executeDecision(() -> advisoryWorkflowService.review(
                alertId,
                body.getOrDefault("reviewer", body.getOrDefault("operatorId", "UNASSIGNED")),
                body.getOrDefault("note", "")
        ));
    }

    @PostMapping("/{alertId}/approve")
    public ResponseEntity<?> approve(
            @PathVariable String alertId,
            @RequestBody Map<String, String> body) {
        return executeDecision(() -> advisoryWorkflowService.approve(
                alertId,
                body.getOrDefault("reviewer", body.getOrDefault("operatorId", "UNASSIGNED")),
                body.getOrDefault("note", "")
        ));
    }

    @PostMapping("/{alertId}/reject")
    public ResponseEntity<?> reject(
            @PathVariable String alertId,
            @RequestBody Map<String, String> body) {
        return executeDecision(() -> advisoryWorkflowService.reject(
                alertId,
                body.getOrDefault("reviewer", body.getOrDefault("operatorId", "UNASSIGNED")),
                body.getOrDefault("note", "")
        ));
    }

    @GetMapping("/{alertId}/evidence")
    public ResponseEntity<?> evidence(@PathVariable String alertId) {
        try {
            return ResponseEntity.ok(advisoryWorkflowService.getEvidence(alertId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/evidence")
    public ResponseEntity<List<AdvisoryEvidence>> recentEvidence() {
        return ResponseEntity.ok(advisoryWorkflowService.getRecentEvidence());
    }

    private ResponseEntity<?> executeDecision(DecisionSupplier supplier) {
        try {
            return ResponseEntity.ok(supplier.get());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @FunctionalInterface
    private interface DecisionSupplier {
        AdvisoryDecision get();
    }
}
