package com.mahindra.iot.controller;

import com.mahindra.iot.service.AlertAcknowledgementService;
import com.mahindra.iot.service.AlertAcknowledgementService.AckResult;
import com.mahindra.iot.service.AlertAcknowledgementService.UnacknowledgedAlert;
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

    public AlertWorkflowController(AlertAcknowledgementService ackService) {
        this.ackService = ackService;
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
}
