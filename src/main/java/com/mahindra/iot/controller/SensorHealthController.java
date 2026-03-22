package com.mahindra.iot.controller;

import com.mahindra.iot.service.SensorHealthMonitor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sensor/health")
@RequiredArgsConstructor
@Tag(name = "Sensor Health API", description = "Sensor data quality monitor for Sprint 3")
public class SensorHealthController {

    private final SensorHealthMonitor sensorHealthMonitor;

    @GetMapping("/{machineId}")
    @Operation(summary = "Sensor health by machine")
    public ResponseEntity<Map<String, Object>> getSensorHealth(@PathVariable String machineId) {
        SensorHealthMonitor.MachineSensorHealthReport report = sensorHealthMonitor.getMachineHealth(machineId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("machineId", report.machineId());
        payload.put("sensors", report.sensors());
        payload.put("overallHealth", report.overallHealth());
        payload.put("badSensorCount", report.badSensorCount());
        payload.put("suspectSensorCount", report.suspectSensorCount());
        return ResponseEntity.ok(payload);
    }
}
