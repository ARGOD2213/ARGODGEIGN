package com.mahindra.iot.service;

import com.mahindra.iot.config.ThresholdConfig;
import com.mahindra.iot.model.SensorEvent;
import com.mahindra.iot.repository.SensorEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PredictiveMaintenanceService {

    private final SensorEventRepository repository;
    private final ThresholdConfig thresholdConfig;

    public Map<String, Object> predict(String deviceId, int forecastSteps) {
        int safeSteps = Math.max(1, Math.min(forecastSteps, 48));
        List<SensorEvent> history = repository.findByDeviceId(deviceId).stream()
                .filter(e -> e.getValue() != null)
                .sorted(Comparator.comparing(SensorEvent::getTimestamp))
                .toList();

        if (history.size() < 3) {
            return Map.of(
                    "deviceId", deviceId,
                    "status", "INSUFFICIENT_DATA",
                    "message", "Need at least 3 events for prediction",
                    "required", 3,
                    "available", history.size()
            );
        }

        SensorEvent latest = history.get(history.size() - 1);
        String sensorType = latest.getSensorType();
        double warningThreshold = thresholdConfig.getWarningThreshold(sensorType);
        double criticalThreshold = thresholdConfig.getCriticalThreshold(sensorType);
        boolean lowIsBad = warningThreshold > criticalThreshold;

        List<Double> values = history.stream().map(SensorEvent::getValue).toList();
        double slope = linearSlope(values);
        double currentValue = latest.getValue();
        double forecastValue = currentValue + (slope * safeSteps);

        String trend = Math.abs(slope) < 0.01 ? "STABLE" : (slope > 0 ? "RISING" : "FALLING");
        String forecastStatus = evaluateStatus(forecastValue, warningThreshold, criticalThreshold, lowIsBad);

        Map<String, Object> eta = estimateCriticalBreachEta(currentValue, criticalThreshold, slope, lowIsBad);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deviceId", deviceId);
        response.put("sensorType", sensorType);
        response.put("currentValue", round2(currentValue));
        response.put("currentStatus", latest.getStatus());
        response.put("trend", trend);
        response.put("slopePerEvent", round4(slope));
        response.put("forecastSteps", safeSteps);
        response.put("forecastValue", round2(forecastValue));
        response.put("forecastStatus", forecastStatus);
        response.put("warningThreshold", warningThreshold);
        response.put("criticalThreshold", criticalThreshold);
        response.put("lowIsBad", lowIsBad);
        response.put("estimatedCriticalBreach", eta);
        response.put("recommendation", buildRecommendation(forecastStatus, eta));

        return response;
    }

    private double linearSlope(List<Double> values) {
        int n = values.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;

        for (int i = 0; i < n; i++) {
            double x = i + 1;
            double y = values.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }

        double denominator = n * sumXX - sumX * sumX;
        if (Math.abs(denominator) < 1e-9) {
            return 0;
        }
        return (n * sumXY - sumX * sumY) / denominator;
    }

    private String evaluateStatus(double value, double warning, double critical, boolean lowIsBad) {
        if (lowIsBad) {
            if (value <= critical) return "CRITICAL";
            if (value <= warning) return "WARNING";
            return "NORMAL";
        }
        if (value >= critical) return "CRITICAL";
        if (value >= warning) return "WARNING";
        return "NORMAL";
    }

    private Map<String, Object> estimateCriticalBreachEta(double current, double critical, double slope, boolean lowIsBad) {
        if (!lowIsBad && slope <= 0) {
            return Map.of("possible", false, "reason", "Trend is not moving toward critical threshold");
        }
        if (lowIsBad && slope >= 0) {
            return Map.of("possible", false, "reason", "Trend is not moving toward critical threshold");
        }

        double distance = lowIsBad ? (current - critical) : (critical - current);
        double rate = Math.abs(slope);
        if (distance <= 0) {
            return Map.of("possible", true, "eventsToCritical", 0, "note", "Already at or beyond critical threshold");
        }
        if (rate < 1e-9) {
            return Map.of("possible", false, "reason", "Trend is too flat for reliable ETA");
        }

        int events = (int) Math.ceil(distance / rate);
        return Map.of(
                "possible", true,
                "eventsToCritical", events,
                "approximate", true,
                "note", "Based on linear trend of historical events"
        );
    }

    private String buildRecommendation(String forecastStatus, Map<String, Object> eta) {
        if ("CRITICAL".equals(forecastStatus)) {
            return "Trigger maintenance now and inspect the asset immediately.";
        }
        if ("WARNING".equals(forecastStatus)) {
            return "Schedule preventive maintenance and increase sampling frequency.";
        }
        if (Boolean.TRUE.equals(eta.get("possible"))) {
            return "System is normal now, but trend indicates future risk. Keep monitoring.";
        }
        return "No immediate risk detected. Continue standard monitoring.";
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
