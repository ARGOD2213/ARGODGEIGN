package com.mahindra.iot.service;

import com.mahindra.iot.model.SensorEvent;
import com.mahindra.iot.service.PredictiveAnalysisCacheService.CachedAnalysis;
import com.mahindra.iot.service.SensorContextEnricher.SensorContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictiveAnalysisService {

    private static final int MEDIUM_DATA_POINTS = 12;
    private static final int HIGH_DATA_POINTS = 144;

    private final SensorContextEnricher sensorContextEnricher;
    private final PredictiveAnalysisCacheService predictiveAnalysisCacheService;
    private final LocalPredictiveIntelligenceService localPredictiveIntelligenceService;
    private final OperationalAlertScoringService operationalAlertScoringService;

    @Value("${ai.analysis.enabled:true}")
    private boolean analysisEnabled;

    public void analyze(SensorEvent event) {
        SensorContext context = sensorContextEnricher.enrich(event.getDeviceId(), event.getSensorType());
        String confidence = computeConfidence(
            context.dataPointsAvailable(),
            context.hasMaintHistory(),
            context.hasWeatherContext(),
            context.hasBadSensorData()
        );
        event.setAiConfidence(confidence);

        if (!analysisEnabled || (!"WARNING".equals(event.getStatus()) && !"CRITICAL".equals(event.getStatus()))) {
            applyRuleBasedAnalysis(event, confidence);
            return;
        }

        try {
            localPredictiveIntelligenceService.enrichEvent(event, context);
            if (event.getAiConfidence() == null || event.getAiConfidence().isBlank()) {
                event.setAiConfidence(confidence);
            }
            if (event.getAnalysisSource() == null || event.getAnalysisSource().isBlank()) {
                event.setAnalysisSource("LOCAL_ML_PUBLIC_SAMPLE_BLEND");
            }
            cacheCurrentAnalysis(event);
            log.info("Local predictive analysis complete for {} - riskScore={}, confidence={}, dataPoints={}",
                event.getDeviceId(), event.getAiRiskScore(), event.getAiConfidence(), context.dataPointsAvailable());
        } catch (Exception e) {
            log.warn("Local predictive analysis failed for {}: {}", event.getDeviceId(), e.getMessage());
            if (!applyCachedAnalysis(event)) {
                applyRuleBasedAnalysis(event, confidence);
            }
        }

        operationalAlertScoringService.enrichAlertMetadata(event, context);
    }

    private boolean applyCachedAnalysis(SensorEvent event) {
        Optional<CachedAnalysis> cachedOpt = predictiveAnalysisCacheService.readFresh(event.getDeviceId());
        if (cachedOpt.isEmpty()) {
            return false;
        }

        CachedAnalysis cached = cachedOpt.get();
        event.setAiIncidentSummary(cached.analysis());
        event.setAiRecommendedAction(cached.recommendedAction());
        event.setAiPredictedFailureEta(cached.predictedFailureEta());
        event.setAiRiskScore(cached.riskScore() != null ? cached.riskScore() : 50);
        event.setAiRiskLevel(cached.riskLevel() != null ? cached.riskLevel() : "MEDIUM");
        event.setAiConfidence(cached.confidence() != null ? cached.confidence() : event.getAiConfidence());
        event.setModelRiskScore(event.getAiRiskScore() != null ? event.getAiRiskScore().doubleValue() : null);
        event.setAnalysisSource(cached.source() != null ? cached.source() : "LOCAL_CACHE");
        return true;
    }

    private void cacheCurrentAnalysis(SensorEvent event) {
        CachedAnalysis cacheRecord = predictiveAnalysisCacheService.fromEvent(
            event.getAnalysisSource(),
            event.getAiRiskScore(),
            event.getAiRiskLevel(),
            event.getAiIncidentSummary(),
            event.getAiRecommendedAction(),
            event.getAiPredictedFailureEta(),
            event.getAiConfidence()
        );
        predictiveAnalysisCacheService.write(event.getDeviceId(), cacheRecord);
    }

    private String computeConfidence(int dataPointsAvailable,
                                     boolean hasMaintHistory,
                                     boolean hasWeatherContext,
                                     boolean hasBadSensorData) {
        int score = 0;

        if (dataPointsAvailable >= HIGH_DATA_POINTS) {
            score += 2;
        } else if (dataPointsAvailable >= MEDIUM_DATA_POINTS) {
            score += 1;
        }

        if (hasMaintHistory) {
            score += 1;
        }
        if (hasWeatherContext) {
            score += 1;
        }

        String confidence;
        if (score >= 4) {
            confidence = "HIGH";
        } else if (score >= 2) {
            confidence = "MEDIUM";
        } else {
            confidence = "LOW";
        }

        if (hasBadSensorData) {
            return "HIGH".equals(confidence) ? "MEDIUM" : "LOW";
        }
        return confidence;
    }

    private void applyRuleBasedAnalysis(SensorEvent event, String confidence) {
        int score = "CRITICAL".equals(event.getStatus()) ? 85 : "WARNING".equals(event.getStatus()) ? 55 : 20;
        String level = "CRITICAL".equals(event.getStatus()) ? "HIGH" : "WARNING".equals(event.getStatus()) ? "MEDIUM" : "LOW";

        event.setAiRiskScore(score);
        event.setAiRiskLevel(level);
        event.setAiConfidence(confidence != null ? confidence : "LOW");
        event.setModelRiskScore((double) score);
        if (event.getAnalysisSource() == null || event.getAnalysisSource().isBlank()) {
            event.setAnalysisSource("RULE_BASED");
        }

        String type = event.getSensorType();
        event.setAiIncidentSummary(String.format(
            "%s sensor on device %s is %s with value %.2f. Threshold exceeded, immediate attention required.",
            type, event.getDeviceId(), event.getStatus(), event.getValue()));

        event.setAiRecommendedAction(switch (type) {
            case "TEMPERATURE", "BEARING_TEMPERATURE" -> "Check cooling system and reduce load. Inspect bearings for lubrication.";
            case "VIBRATION" -> "Inspect motor or pump for mechanical imbalance or bearing wear.";
            case "GAS_LEAK" -> "EVACUATE area immediately. Shut off gas supply. Contact safety team.";
            case "SMOKE_DENSITY" -> "Activate fire suppression system. Check for ignition sources.";
            case "WATER_LEAK" -> "Shut off water supply to affected line. Inspect pipe junction.";
            case "MOTOR_CURRENT" -> "Check for mechanical overload or electrical fault. Reduce load.";
            case "OIL_PRESSURE" -> "Check oil pump and supply lines. Top up oil level immediately.";
            case "VOLTAGE" -> "Check power supply unit. Inspect for grid fluctuations.";
            case "BATTERY_LEVEL" -> "Replace or recharge UPS battery. Test backup power immediately.";
            default -> "Investigate sensor reading. Inspect equipment and notify maintenance team.";
        });

        event.setAiPredictedFailureEta("CRITICAL".equals(event.getStatus()) ? "Within 2-4 hours" : "Within 24 hours if unaddressed");
    }
}
