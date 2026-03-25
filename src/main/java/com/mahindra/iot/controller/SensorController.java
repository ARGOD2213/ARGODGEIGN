package com.mahindra.iot.controller;

import com.mahindra.iot.dto.SensorIngestRequest;
import com.mahindra.iot.model.SensorEvent;
import com.mahindra.iot.service.DashboardService;
import com.mahindra.iot.service.PredictiveMaintenanceService;
import com.mahindra.iot.service.SensorEventService;
import com.mahindra.iot.service.WeatherService;
import com.mahindra.iot.util.AiAdvisoryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "ARGODREIGN IoT Alert Engine", description = "22-sensor industrial IoT monitoring with weather-aware local predictive intelligence")
public class SensorController {

    private final SensorEventService sensorEventService;
    private final DashboardService dashboardService;
    private final PredictiveMaintenanceService predictiveMaintenanceService;
    private final WeatherService weatherService;

    @PostMapping("/sensor/ingest")
    @Operation(summary = "Ingest sensor telemetry",
               description = "Accepts sensor payload, evaluates 22-sensor thresholds, runs weather-aware local predictive analysis, fires SNS/SQS alerts, persists to DynamoDB")
    public ResponseEntity<Map<String, Object>> ingestEvent(@Valid @RequestBody SensorIngestRequest request) {
        SensorEvent event = sensorEventService.ingestEvent(request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Event ingested successfully");
        response.put("deviceId", event.getDeviceId());
        response.put("timestamp", event.getTimestamp());
        response.put("sensorType", event.getSensorType());
        response.put("value", event.getValue());
        response.put("status", event.getStatus());
        response.put("alertFired", event.getAlertId() != null);
        response.put("aiRiskScore", event.getAiRiskScore() != null ? event.getAiRiskScore() : "N/A");
        response.put("alertPrecisionScore", event.getAlertPrecisionScore() != null ? event.getAlertPrecisionScore() : "N/A");
        response.put("alertActionability", event.getAlertActionability() != null ? event.getAlertActionability() : "N/A");
        response.put("primaryFailureMode", event.getPrimaryFailureMode() != null ? event.getPrimaryFailureMode() : "N/A");
        response.put("weatherNote", event.getWeatherCorrelationNote() != null ? event.getWeatherCorrelationNote() : "N/A");
        response.put("analysisSource", event.getAnalysisSource() != null ? event.getAnalysisSource() : "NONE");
        response.put("aiAdvisory", AiAdvisoryWrapper.fromEvent(event));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

    @GetMapping("/weather/current")
    @Operation(summary = "Current weather snapshot for safety dashboard")
    public ResponseEntity<Map<String, Object>> getCurrentWeather(@RequestParam(defaultValue = "17.3850") double lat,
                                                                 @RequestParam(defaultValue = "78.4867") double lon) {
        WeatherService.WeatherData weather = weatherService.getWeather(lat, lon);
        double wbgt = weather.getWbgt();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("temperature", weather.getTempC());
        payload.put("humidity", weather.getHumidity());
        payload.put("condition", weather.getCondition());
        payload.put("windSpeed", weather.getWindSpeed());
        payload.put("windDeg", weather.getWindDeg());
        payload.put("wbgt", wbgt);
        payload.put("wbgtWorkRestBand", weatherService.getWbgtWorkRestBand(wbgt));
        payload.put("wbgtStatus", weatherService.getWbgtStatus(wbgt));
        payload.put("isoReference", "ISO 7933:2004");
        payload.put("source", weather.isAvailable() ? "OPENWEATHER" : "SYNTHETIC_FALLBACK");
        return ResponseEntity.ok(payload);
    }
}
