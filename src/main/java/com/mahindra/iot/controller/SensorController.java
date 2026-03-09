package com.mahindra.iot.controller;

import com.mahindra.iot.dto.SensorIngestRequest;
import com.mahindra.iot.model.SensorEvent;
import com.mahindra.iot.service.DashboardService;
import com.mahindra.iot.service.PredictiveMaintenanceService;
import com.mahindra.iot.service.SensorEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "ARGODREIGN IoT Alert Engine", description = "22-sensor industrial IoT monitoring with Weather AI + Multi-LLM")
public class SensorController {

    private final SensorEventService sensorEventService;
    private final DashboardService dashboardService;
    private final PredictiveMaintenanceService predictiveMaintenanceService;

    @PostMapping("/sensor/ingest")
    @Operation(summary = "Ingest sensor telemetry",
               description = "Accepts sensor payload, evaluates 22-sensor thresholds, runs Weather AI + Gemini LLM analysis, fires SNS/SQS alerts, persists to DynamoDB")
    public ResponseEntity<Map<String, Object>> ingestEvent(@Valid @RequestBody SensorIngestRequest request) {
        SensorEvent event = sensorEventService.ingestEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message",       "Event ingested successfully",
            "deviceId",      event.getDeviceId(),
            "timestamp",     event.getTimestamp(),
            "sensorType",    event.getSensorType(),
            "value",         event.getValue(),
            "status",        event.getStatus(),
            "alertFired",    event.getAlertId() != null,
            "aiRiskScore",   event.getAiRiskScore() != null ? event.getAiRiskScore() : "N/A",
            "weatherNote",   event.getWeatherCorrelationNote() != null ? event.getWeatherCorrelationNote() : "N/A",
            "llmConsensus",  event.getLlmConsensus() != null ? event.getLlmConsensus() : "NONE"
        ));
    }

    @GetMapping("/dashboard/live")
    @Operation(summary = "Live event stream", description = "Last 50 sensor events from Redis cache")
    public ResponseEntity<List<Object>> getLiveDashboard() {
        return ResponseEntity.ok(sensorEventService.getLiveDashboard());
    }

    @GetMapping("/dashboard/overview")
    @Operation(summary = "Dashboard overview", description = "Aggregated counts, risk summary, top sensors, and latest events")
    public ResponseEntity<Map<String, Object>> getDashboardOverview() {
        return ResponseEntity.ok(dashboardService.getOverview());
    }

    @GetMapping("/alerts/history")
    @Operation(summary = "Latest alert per device", description = "Most recent alert per device from Redis, includes AI risk score and weather correlation")
    public ResponseEntity<Map<Object, Object>> getAlertHistory() {
        return ResponseEntity.ok(sensorEventService.getAlertHistory());
    }

    @GetMapping("/device/{deviceId}/history")
    @Operation(summary = "Device event history from DynamoDB")
    public ResponseEntity<List<SensorEvent>> getDeviceHistory(@PathVariable String deviceId) {
        return ResponseEntity.ok(sensorEventService.getDeviceHistory(deviceId));
    }

    @GetMapping("/device/{deviceId}/stats")
    @Operation(summary = "Device statistics", description = "Warning/critical counts, average value, total events. Redis cached 1 min.")
    public ResponseEntity<Map<String, Object>> getDeviceStats(@PathVariable String deviceId) {
        return ResponseEntity.ok(sensorEventService.getDeviceStats(deviceId));
    }

    @GetMapping("/device/{deviceId}/prediction")
    @Operation(summary = "Predictive maintenance forecast", description = "Forecasts near-term sensor behavior and estimated time to critical threshold")
    public ResponseEntity<Map<String, Object>> getDevicePrediction(@PathVariable String deviceId,
                                                                   @RequestParam(defaultValue = "6") int forecastSteps) {
        return ResponseEntity.ok(predictiveMaintenanceService.predict(deviceId, forecastSteps));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status",  "UP",
            "service", "ARGODREIGN IoT Alert Engine",
            "version", "2.0.0"
        ));
    }
}
