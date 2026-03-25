package com.mahindra.iot.service;

import com.mahindra.iot.model.SensorEvent;
import org.springframework.stereotype.Service;

@Service
public class OperationalAlertScoringService {

    public void enrichAlertMetadata(SensorEvent event, SensorContextEnricher.SensorContext context) {
        if (event == null) {
            return;
        }

        int aiRiskScore = event.getAiRiskScore() != null ? event.getAiRiskScore() : 45;
        double thresholdSupport = thresholdSupport(event);
        double evidenceCoverage = evidenceCoverage(context);
        double dataPenalty = context.hasBadSensorData() ? 0.14 : 0.0;
        double maintenanceBonus = context.hasMaintHistory() ? 0.08 : 0.0;

        int precisionScore = clampInt((int) Math.round(
            28
                + (thresholdSupport * 28.0)
                + (evidenceCoverage * 26.0)
                + maintenanceBonus * 100.0
                - dataPenalty * 100.0
                + Math.min(12.0, aiRiskScore * 0.12)
        ), 18, 98);

        String confidence = event.getAiConfidence() != null ? event.getAiConfidence() : "LOW";
        if ("HIGH".equalsIgnoreCase(confidence)) {
            precisionScore = clampInt(precisionScore + 6, 18, 98);
        } else if ("MEDIUM".equalsIgnoreCase(confidence)) {
            precisionScore = clampInt(precisionScore + 3, 18, 98);
        }

        String actionability = precisionScore >= 78 || (aiRiskScore >= 80 && precisionScore >= 62)
            ? "HIGH"
            : precisionScore >= 55
                ? "MEDIUM"
                : "LOW";

        event.setAlertPrecisionScore(precisionScore);
        event.setAlertActionability(actionability);
        event.setAlertAccuracyNote(buildAccuracyNote(event, context, precisionScore, actionability));
        if (event.getPrimaryFailureMode() == null || event.getPrimaryFailureMode().isBlank()) {
            event.setPrimaryFailureMode(inferFailureMode(event));
        }
    }

    private double thresholdSupport(SensorEvent event) {
        if (event.getValue() == null) {
            return 0.0;
        }

        Double warning = event.getWarningThreshold();
        Double critical = event.getCriticalThreshold();
        if (warning == null && critical == null) {
            return 0.25;
        }

        double value = event.getValue();
        double support = 0.0;

        if (critical != null && critical != 0.0) {
            support = Math.max(support, Math.abs(value / critical));
        }
        if (warning != null && warning != 0.0) {
            support = Math.max(support, Math.abs(value / warning) * 0.85);
        }

        return Math.max(0.0, Math.min(support, 1.4));
    }

    private double evidenceCoverage(SensorContextEnricher.SensorContext context) {
        return clamp(
            Math.min(1.0, context.dataPointsAvailable() / 72.0) * 0.58
                + (context.hasMaintHistory() ? 0.18 : 0.0)
                + (context.hasWeatherContext() ? 0.12 : 0.0)
                + (context.hasBadSensorData() ? 0.0 : 0.12),
            0.0,
            1.0
        );
    }

    private String buildAccuracyNote(SensorEvent event,
                                     SensorContextEnricher.SensorContext context,
                                     int precisionScore,
                                     String actionability) {
        String supportText = precisionScore >= 75
            ? "strong evidence support"
            : precisionScore >= 55
                ? "moderate evidence support"
                : "limited corroboration";

        String dataText = context.hasBadSensorData()
            ? "Sensor quality issues reduce trust and require operator confirmation."
            : "Sensor quality is currently acceptable.";

        return "Alert precision is " + precisionScore + "/100 with " + actionability
            + " actionability based on " + supportText + ". " + dataText;
    }

    private String inferFailureMode(SensorEvent event) {
        String sensorType = event.getSensorType() == null ? "" : event.getSensorType().toUpperCase();
        return switch (sensorType) {
            case "VIBRATION" -> "rotating assembly degradation";
            case "BEARING_TEMPERATURE", "TEMPERATURE" -> "thermal stress or lubrication breakdown";
            case "PRESSURE", "OIL_PRESSURE", "INSTRUMENT_AIR_PRESSURE" -> "pressure containment or supply instability";
            case "GAS_LEAK" -> "containment breach";
            case "MOTOR_CURRENT" -> "electrical overload or mechanical drag";
            case "VOLTAGE" -> "power quality instability";
            default -> "threshold breach";
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
