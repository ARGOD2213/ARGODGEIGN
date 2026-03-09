package com.mahindra.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahindra.iot.model.SensorEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SensorEventService sensorEventService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getOverview() {
        List<SensorEvent> events = sensorEventService.getLiveDashboard().stream()
                .map(this::toSensorEvent)
                .filter(Objects::nonNull)
                .toList();

        long warningCount = events.stream().filter(e -> "WARNING".equalsIgnoreCase(e.getStatus())).count();
        long criticalCount = events.stream().filter(e -> "CRITICAL".equalsIgnoreCase(e.getStatus())).count();
        long normalCount = events.stream().filter(e -> "NORMAL".equalsIgnoreCase(e.getStatus())).count();

        double avgRisk = events.stream()
                .map(SensorEvent::getAiRiskScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        Map<String, Long> topSensors = events.stream()
                .map(SensorEvent::getSensorType)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        List<Map<String, Object>> topSensorList = topSensors.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> Map.<String, Object>of("sensorType", e.getKey(), "count", e.getValue()))
                .toList();

        Map<Object, Object> alertHistory = sensorEventService.getAlertHistory();

        return Map.of(
                "lastUpdated", Instant.now().toString(),
                "liveEventCount", events.size(),
                "warningCount", warningCount,
                "criticalCount", criticalCount,
                "normalCount", normalCount,
                "averageAiRiskScore", Math.round(avgRisk * 100.0) / 100.0,
                "activeDevicesWithAlerts", alertHistory.size(),
                "topSensors", topSensorList,
                "latestEvents", events.stream().limit(10).toList()
        );
    }

    private SensorEvent toSensorEvent(Object raw) {
        if (raw instanceof SensorEvent sensorEvent) {
            return sensorEvent;
        }
        try {
            return objectMapper.convertValue(raw, SensorEvent.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
