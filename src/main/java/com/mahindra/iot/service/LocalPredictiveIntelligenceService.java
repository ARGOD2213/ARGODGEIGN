package com.mahindra.iot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahindra.iot.config.ThresholdConfig;
import com.mahindra.iot.model.SensorEvent;
import com.mahindra.iot.repository.SensorEventRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalPredictiveIntelligenceService {

    private static final int K_NEIGHBORS = 5;

    private final ObjectMapper objectMapper;
    private final SensorEventRepository repository;
    private final ThresholdConfig thresholdConfig;
    private final PredictiveMaintenanceService predictiveMaintenanceService;
    private final SensorContextEnricher sensorContextEnricher;

    @Value("classpath:ml/public_predictive_seed_profiles.json")
    private Resource seedProfileResource;

    @Value("${ml.training.synthetic.multiplier:14}")
    private int syntheticMultiplier;

    private List<TrainingSample> trainingSamples = List.of();

    @PostConstruct
    void init() {
        this.trainingSamples = loadTrainingSamples();
        log.info("Loaded {} local predictive training samples", trainingSamples.size());
    }

    public void enrichEvent(SensorEvent event, SensorContextEnricher.SensorContext context) {
        if (event.getDeviceId() == null || event.getDeviceId().isBlank()) {
            applyFallback(event, "LOW", "LOCAL_ML_NO_DEVICE");
            return;
        }

        MachineIntelligenceReport report = buildReport(event.getDeviceId(), context, event);
        event.setAiRiskScore(report.riskScore());
        event.setAiRiskLevel(report.riskLevel());
        event.setAiIncidentSummary(report.summary());
        event.setAiRecommendedAction(report.recommendedAction());
        event.setAiPredictedFailureEta(report.predictedFailureEta());
        event.setAiConfidence(report.confidence());
        event.setModelRiskScore(report.riskScore() != null ? report.riskScore().doubleValue() : null);
        event.setAnalysisSource(report.dataSource());
    }

    public MachineIntelligenceReport analyzeMachine(String deviceId) {
        SensorEvent latest = latestEvent(deviceId);
        SensorContextEnricher.SensorContext context = sensorContextEnricher.enrich(
            deviceId,
            latest != null ? latest.getSensorType() : null
        );
        return buildReport(deviceId, context, latest);
    }

    public MachineChatResponse answerQuestion(String deviceId, String question) {
        MachineIntelligenceReport report = analyzeMachine(deviceId);
        List<SensorEvent> history = sortedHistory(deviceId);
        String prompt = question == null ? "" : question.trim();
        String lower = prompt.toLowerCase(Locale.ROOT);

        String answer;
        List<String> bullets = new ArrayList<>();

        if (lower.contains("model") || lower.contains("why") || lower.contains("how")) {
            String topModel = report.modelBreakdown().isEmpty()
                ? "ARGUS Atlas Similarity"
                : report.modelBreakdown().get(0).modelName();
            answer = "The leading model signal for " + deviceId + " is " + topModel
                + ". It contributes to an ensemble risk of " + report.riskScore()
                + "/100 with " + report.confidence() + " confidence. Primary rationale: "
                + (report.modelBreakdown().isEmpty()
                    ? "insufficient telemetry for deeper explanation."
                    : report.modelBreakdown().get(0).rationale());
            bullets.add("Ensemble source: " + report.dataSource());
            bullets.add("Top model: " + topModel);
            bullets.add("Failure mode: " + report.failureMode());
        } else if (lower.contains("oee") || lower.contains("performance") || lower.contains("efficiency")) {
            Map<String, Object> prediction = predictiveMaintenanceService.predict(deviceId, 12);
            answer = "Performance view for " + deviceId + ": current risk is "
                + report.riskLevel() + " at " + report.riskScore() + "/100. "
                + "Trend is " + prediction.getOrDefault("trend", "UNKNOWN")
                + " and the forecast status is " + prediction.getOrDefault("forecastStatus", "UNKNOWN") + ".";
            bullets.add("Risk score: " + report.riskScore());
            bullets.add("Trend: " + prediction.getOrDefault("trend", "UNKNOWN"));
            bullets.add("Forecast status: " + prediction.getOrDefault("forecastStatus", "UNKNOWN"));
        } else if (lower.contains("alert") || lower.contains("critical") || lower.contains("warning")) {
            long warningCount = history.stream().filter(e -> "WARNING".equalsIgnoreCase(e.getStatus())).count();
            long criticalCount = history.stream().filter(e -> "CRITICAL".equalsIgnoreCase(e.getStatus())).count();
            answer = deviceId + " has " + criticalCount + " critical and " + warningCount
                + " warning events in the retained history. The model currently flags "
                + report.failureMode() + " as the closest failure pattern.";
            bullets.add("Critical events: " + criticalCount);
            bullets.add("Warning events: " + warningCount);
            bullets.add("Likely failure mode: " + report.failureMode());
        } else if (lower.contains("eta") || lower.contains("when") || lower.contains("fail")) {
            answer = "Predicted intervention window for " + deviceId + ": "
                + report.predictedFailureEta() + ". Recommended action: "
                + report.recommendedAction();
            bullets.add("ETA: " + report.predictedFailureEta());
            bullets.add("Action: " + report.recommendedAction());
        } else if (containsSensorKeyword(lower)) {
            SensorEvent sensorEvent = latestMatchingSensor(history, lower);
            if (sensorEvent == null) {
                answer = "I could not find a recent matching sensor for that question on "
                    + deviceId + ". The latest model summary is: " + report.summary();
            } else {
                answer = "Latest matching sensor on " + deviceId + " is "
                    + sensorEvent.getSensorType() + " at " + round2(sensorEvent.getValue())
                    + " with status " + sensorEvent.getStatus() + ". "
                    + "Model view: " + report.summary();
                bullets.add("Sensor: " + sensorEvent.getSensorType());
                bullets.add("Value: " + round2(sensorEvent.getValue()));
                bullets.add("Status: " + sensorEvent.getStatus());
            }
        } else {
            answer = "Machine summary for " + deviceId + ": " + report.summary()
                + " Recommended action: " + report.recommendedAction()
                + " Predicted intervention window: " + report.predictedFailureEta() + ".";
            bullets.add("Risk score: " + report.riskScore());
            bullets.add("Confidence: " + report.confidence());
            bullets.add("Failure mode: " + report.failureMode());
            bullets.add("Top model: " + (report.modelBreakdown().isEmpty() ? "N/A" : report.modelBreakdown().get(0).modelName()));
        }

        return new MachineChatResponse(
            deviceId,
            prompt,
            answer,
            bullets,
            report.riskScore(),
            report.riskLevel(),
            report.dataSource(),
            report.trainingSources()
        );
    }

    private MachineIntelligenceReport buildReport(
            String deviceId,
            SensorContextEnricher.SensorContext context,
            SensorEvent preferredLatest) {
        List<SensorEvent> history = sortedHistory(deviceId);
        SensorEvent latest = preferredLatest != null ? preferredLatest : (history.isEmpty() ? null : history.get(history.size() - 1));
        if (latest == null || latest.getValue() == null) {
            return new MachineIntelligenceReport(
                deviceId,
                "unknown",
                "UNKNOWN",
                15,
                "LOW",
                "LOW",
                "No recent telemetry available for model scoring.",
                "Collect more sensor history before relying on predictive guidance.",
                "N/A",
                "insufficient telemetry",
                List.of(),
                defaultTrainingSources(),
                "LOCAL_ML_PUBLIC_SAMPLE_BLEND",
                List.of(),
                new ContextSnapshot(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0),
                defaultPortfolioHighlights()
            );
        }

        MachineFeatureVector vector = buildFeatureVector(history, latest, context);
        List<TrainingMatch> nearest = nearestSamples(vector);

        NeighborAggregate aggregate = aggregateNearest(nearest);
        int atlasSimilarityScore = clampInt((int) Math.round(aggregate.weightedRisk()), 5, 95);
        int trajectoryScore = clampInt((int) Math.round(
            (vector.ratioToCritical() * 52.0)
                + (Math.abs(vector.slopePerEvent()) * 180.0)
                + (vector.criticalRate() * 120.0)
        ), 5, 98);
        int operatingStressScore = clampInt((int) Math.round(
            (vector.ambientStress() * 42.0)
                + (vector.volatility() * 160.0)
                + (vector.warningRate() * 95.0)
        ), 5, 96);
        int maintenanceDebtScore = clampInt((int) Math.round(
            (vector.maintenanceDebt() * 75.0)
                + (vector.warningRate() * 20.0)
                + (vector.criticalRate() * 30.0)
        ), 5, 97);
        int failureConsensusScore = clampInt((int) Math.round(
            (aggregate.consensusStrength() * 55.0)
                + (aggregate.weightedRisk() * 0.45)
        ), 5, 95);

        List<ModelSignal> modelBreakdown = List.of(
            new ModelSignal(
                "ARGUS Atlas Similarity",
                "neighbor-model",
                atlasSimilarityScore,
                scoreBand(atlasSimilarityScore),
                "Matches live machine behavior to public-dataset-inspired degradation signatures.",
                "Closest failure mode family: " + aggregate.failureMode()
            ),
            new ModelSignal(
                "ARGUS Trajectory Breach",
                "trend-model",
                trajectoryScore,
                scoreBand(trajectoryScore),
                "Looks at slope, threshold distance, and short-horizon breach velocity.",
                "Threshold ratio " + round2(vector.ratioToCritical()) + ", slope/event " + round2(vector.slopePerEvent())
            ),
            new ModelSignal(
                "ARGUS Operating Envelope",
                "stress-model",
                operatingStressScore,
                scoreBand(operatingStressScore),
                "Measures volatility, ambient stress, and warning density around the current operating point.",
                "Ambient stress " + round2(vector.ambientStress()) + ", volatility " + round2(vector.volatility())
            ),
            new ModelSignal(
                "ARGUS Maintenance Debt",
                "health-model",
                maintenanceDebtScore,
                scoreBand(maintenanceDebtScore),
                "Raises risk when maintenance history is thin or sensor quality is degraded.",
                "Maintenance debt " + round2(vector.maintenanceDebt()) + ", warnings " + vector.warningCount()
            ),
            new ModelSignal(
                "ARGUS Failure Mode Consensus",
                "consensus-model",
                failureConsensusScore,
                scoreBand(failureConsensusScore),
                "Checks how strongly the nearest degradation signatures agree on one failure family.",
                "Consensus family: " + aggregate.failureMode()
            )
        ).stream()
            .sorted(Comparator.comparingInt(ModelSignal::score).reversed())
            .toList();

        double ensembleRisk = atlasSimilarityScore * 0.34
            + trajectoryScore * 0.24
            + operatingStressScore * 0.18
            + maintenanceDebtScore * 0.14
            + failureConsensusScore * 0.10;
        int riskScore = clampInt((int) Math.round(ensembleRisk), 5, 99);

        String riskLevel = riskScore >= 80 ? "CRITICAL"
            : riskScore >= 60 ? "HIGH"
            : riskScore >= 35 ? "MEDIUM"
            : "LOW";

        String confidence = confidenceFrom(vector, nearest);
        String failureMode = aggregate.failureMode();
        String recommendedAction = topRecommendation(nearest, riskLevel);
        String eta = deriveEta(deviceId, riskScore, vector);
        String summary = buildSummary(deviceId, latest, vector, riskLevel, riskScore, failureMode, eta, modelBreakdown);

        List<String> topDrivers = topDrivers(vector, latest);
        List<String> trainingSources = aggregate.sources().isEmpty()
            ? defaultTrainingSources()
            : aggregate.sources();

        return new MachineIntelligenceReport(
            deviceId,
            vector.machineClass(),
            latest.getSensorType(),
            riskScore,
            riskLevel,
            confidence,
            summary,
            recommendedAction,
            eta,
            failureMode,
            topDrivers,
            trainingSources,
            "LOCAL_ML_PUBLIC_SAMPLE_BLEND",
            modelBreakdown,
            new ContextSnapshot(
                vector.eventsObserved(),
                vector.warningCount(),
                vector.criticalCount(),
                round2(vector.ratioToCritical()),
                round2(vector.slopePerEvent()),
                round2(vector.volatility()),
                round2(vector.ambientStress()),
                round2(vector.maintenanceDebt())
            ),
            defaultPortfolioHighlights()
        );
    }

    private MachineFeatureVector buildFeatureVector(
            List<SensorEvent> history,
            SensorEvent latest,
            SensorContextEnricher.SensorContext context) {
        List<SensorEvent> sensorHistory = history.stream()
            .filter(e -> latest.getSensorType().equalsIgnoreCase(e.getSensorType()))
            .filter(e -> e.getValue() != null)
            .toList();
        if (sensorHistory.isEmpty()) {
            sensorHistory = history.stream().filter(e -> e.getValue() != null).toList();
        }

        List<Double> values = sensorHistory.stream().map(SensorEvent::getValue).toList();
        double warning = thresholdConfig.getWarningThreshold(latest.getSensorType());
        double critical = thresholdConfig.getCriticalThreshold(latest.getSensorType());
        boolean lowIsBad = warning > critical;

        long warningCount = history.stream().filter(e -> "WARNING".equalsIgnoreCase(e.getStatus())).count();
        long criticalCount = history.stream().filter(e -> "CRITICAL".equalsIgnoreCase(e.getStatus())).count();
        double total = Math.max(1.0, history.size());

        double ambientStress = normalizeAmbient(context.weatherContext().tempC(), 25.0, 48.0) * 0.6
            + normalizeAmbient(context.weatherContext().humidityPct(), 35.0, 95.0) * 0.4;
        long badSensors = context.sensorHealth().stream()
            .filter(s -> "BAD".equalsIgnoreCase(s.quality().name()))
            .count();
        double maintenanceDebt = (context.hasMaintHistory() ? 0.24 : 0.55)
            + (context.hasBadSensorData() ? 0.15 : 0.0)
            + ((double) badSensors / Math.max(1.0, context.sensorHealth().size())) * 0.2;

        return new MachineFeatureVector(
            inferMachineClass(latest.getDeviceId()),
            latest.getSensorType(),
            safeThresholdRatio(latest.getValue(), warning, lowIsBad),
            safeThresholdRatio(latest.getValue(), critical, lowIsBad),
            linearSlope(values),
            stdDev(values),
            warningCount / total,
            criticalCount / total,
            clamp(ambientStress, 0.0, 1.0),
            clamp(maintenanceDebt, 0.0, 1.0),
            history.size(),
            warningCount,
            criticalCount
        );
    }

    private List<TrainingMatch> nearestSamples(MachineFeatureVector vector) {
        return trainingSamples.stream()
            .map(sample -> new TrainingMatch(sample, distance(vector, sample)))
            .sorted(Comparator.comparingDouble(TrainingMatch::distance))
            .limit(K_NEIGHBORS)
            .map(match -> new TrainingMatch(
                match.sample(),
                match.distance(),
                1.0 / (0.12 + match.distance())
            ))
            .toList();
    }

    private NeighborAggregate aggregateNearest(List<TrainingMatch> nearest) {
        if (nearest.isEmpty()) {
            return new NeighborAggregate(20.0, "general degradation", defaultTrainingSources(), 0.0);
        }

        double weightedRisk = 0.0;
        double totalWeight = 0.0;
        Map<String, Double> failureModeWeights = new LinkedHashMap<>();
        Map<String, Double> sourceWeights = new LinkedHashMap<>();

        for (TrainingMatch match : nearest) {
            weightedRisk += match.weight() * match.sample().failureRisk();
            totalWeight += match.weight();
            failureModeWeights.merge(match.sample().failureMode(), match.weight(), Double::sum);
            sourceWeights.merge(match.sample().sourceFamily(), match.weight(), Double::sum);
        }

        double topFailureWeight = failureModeWeights.values().stream()
            .max(Double::compareTo)
            .orElse(0.0);

        return new NeighborAggregate(
            totalWeight > 0 ? weightedRisk / totalWeight : 20.0,
            topWeightedKey(failureModeWeights, "general degradation"),
            sourceWeights.keySet().stream().toList(),
            totalWeight > 0 ? topFailureWeight / totalWeight : 0.0
        );
    }

    private double distance(MachineFeatureVector vector, TrainingSample sample) {
        double penalty = 0.0;
        if (!sample.machineClass().equalsIgnoreCase(vector.machineClass())) {
            penalty += 0.35;
        }
        if (!sample.sensorType().equalsIgnoreCase(vector.sensorType())) {
            penalty += 0.22;
        }

        return penalty
            + Math.abs(vector.ratioToWarning() - sample.ratioToWarning()) * 1.4
            + Math.abs(vector.slopePerEvent() - sample.slopePerEvent()) * 1.2
            + Math.abs(vector.volatility() - sample.volatility()) * 1.0
            + Math.abs(vector.warningRate() - sample.warningRate()) * 0.9
            + Math.abs(vector.criticalRate() - sample.criticalRate()) * 1.2
            + Math.abs(vector.ambientStress() - sample.ambientStress()) * 0.5
            + Math.abs(vector.maintenanceDebt() - sample.maintenanceDebt()) * 0.8;
    }

    private List<String> topDrivers(MachineFeatureVector vector, SensorEvent latest) {
        List<String> drivers = new ArrayList<>();
        if (vector.ratioToCritical() >= 1.0) {
            drivers.add("Current value is already at or beyond the critical threshold.");
        } else if (vector.ratioToWarning() >= 1.0) {
            drivers.add("Current value is above the warning threshold.");
        }
        if (Math.abs(vector.slopePerEvent()) >= 0.06) {
            drivers.add("Trend slope is accelerating toward a more severe state.");
        }
        if (vector.criticalRate() >= 0.08) {
            drivers.add("Recent history includes repeated critical events.");
        } else if (vector.warningRate() >= 0.20) {
            drivers.add("Warning density is elevated in recent history.");
        }
        if (vector.maintenanceDebt() >= 0.55) {
            drivers.add("Data suggests maintenance debt or incomplete maintenance history.");
        }
        if (drivers.isEmpty()) {
            drivers.add("Machine is presently stable but still tracked by the local model.");
        }
        drivers.add("Primary sensor under analysis: " + latest.getSensorType() + ".");
        return drivers.stream().limit(4).toList();
    }

    private String buildSummary(
            String deviceId,
            SensorEvent latest,
            MachineFeatureVector vector,
            String riskLevel,
            int riskScore,
            String failureMode,
            String eta,
            List<ModelSignal> modelBreakdown) {
        String leadModel = modelBreakdown.isEmpty()
            ? "ARGUS Atlas Similarity"
            : modelBreakdown.get(0).modelName();
        return deviceId + " is running with " + riskLevel + " predictive risk (" + riskScore + "/100)"
            + " on sensor " + latest.getSensorType()
            + ". The model sees " + failureMode
            + " as the closest failure pattern, led by " + leadModel
            + ", with threshold ratio "
            + round2(vector.ratioToWarning()) + " and volatility "
            + round2(vector.volatility()) + ". Estimated intervention window: " + eta + ".";
    }

    private String deriveEta(String deviceId, int riskScore, MachineFeatureVector vector) {
        Map<String, Object> prediction = predictiveMaintenanceService.predict(deviceId, 12);
        Object eta = prediction.get("estimatedCriticalBreach");
        if (eta instanceof Map<?, ?> etaMap && Boolean.TRUE.equals(etaMap.get("possible"))) {
            Object events = etaMap.get("eventsToCritical");
            return events + " events at current trend";
        }
        if (riskScore >= 80 || vector.ratioToCritical() >= 1.0) {
            return "Immediate intervention recommended";
        }
        if (riskScore >= 60) {
            return "Within the next shift";
        }
        if (riskScore >= 35) {
            return "Within 24-48 hours if the trend persists";
        }
        return "No near-term intervention expected";
    }

    private String topRecommendation(List<TrainingMatch> nearest, String riskLevel) {
        if (nearest.isEmpty()) {
            return "Continue normal monitoring and collect more data.";
        }
        String recommendation = nearest.stream()
            .max(Comparator.comparingDouble(TrainingMatch::weight))
            .map(match -> match.sample().recommendation())
            .orElse("Continue normal monitoring and collect more data.");
        if ("CRITICAL".equals(riskLevel)) {
            return recommendation + " Escalate to maintenance supervision now.";
        }
        return recommendation;
    }

    private String confidenceFrom(MachineFeatureVector vector, List<TrainingMatch> nearest) {
        if (nearest.isEmpty()) {
            return "LOW";
        }
        double bestDistance = nearest.get(0).distance();
        if (bestDistance < 0.22 && vector.criticalRate() < 0.08) {
            return "HIGH";
        }
        if (bestDistance < 0.45) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String scoreBand(int score) {
        if (score >= 80) return "CRITICAL";
        if (score >= 60) return "HIGH";
        if (score >= 35) return "MEDIUM";
        return "LOW";
    }

    private SensorEvent latestMatchingSensor(List<SensorEvent> history, String question) {
        return history.stream()
            .filter(e -> e.getSensorType() != null)
            .filter(e -> question.contains(e.getSensorType().toLowerCase(Locale.ROOT).replace('_', ' '))
                || question.contains(e.getSensorType().toLowerCase(Locale.ROOT)))
            .max(Comparator.comparing(SensorEvent::getTimestamp))
            .orElse(null);
    }

    private boolean containsSensorKeyword(String question) {
        return question.contains("temperature")
            || question.contains("vibration")
            || question.contains("pressure")
            || question.contains("voltage")
            || question.contains("current")
            || question.contains("bearing")
            || question.contains("sensor");
    }

    private List<TrainingSample> loadTrainingSamples() {
        try (InputStream inputStream = seedProfileResource.getInputStream()) {
            List<SeedProfile> seeds = objectMapper.readValue(inputStream, new TypeReference<List<SeedProfile>>() {});
            return augmentSeeds(seeds);
        } catch (IOException ex) {
            log.error("Could not load local predictive seed profiles: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<TrainingSample> augmentSeeds(List<SeedProfile> seeds) {
        List<TrainingSample> expanded = new ArrayList<>();
        Random random = new Random(42L);
        for (SeedProfile seed : seeds) {
            expanded.add(toTrainingSample(seed));
            for (int i = 0; i < syntheticMultiplier; i++) {
                expanded.add(new TrainingSample(
                    seed.sourceFamily(),
                    seed.machineClass(),
                    seed.sensorType(),
                    clamp(jitter(seed.ratioToWarning(), random, 0.08), 0.35, 1.45),
                    jitter(seed.slopePerEvent(), random, 0.025),
                    clamp(jitter(seed.volatility(), random, 0.04), 0.01, 0.35),
                    clamp(jitter(seed.warningRate(), random, 0.05), 0.0, 0.45),
                    clamp(jitter(seed.criticalRate(), random, 0.03), 0.0, 0.20),
                    clamp(jitter(seed.ambientStress(), random, 0.08), 0.0, 1.0),
                    clamp(jitter(seed.maintenanceDebt(), random, 0.08), 0.0, 1.0),
                    clampInt((int) Math.round(jitter(seed.failureRisk(), random, 7.0)), 5, 95),
                    seed.failureMode(),
                    seed.recommendation()
                ));
            }
        }
        return expanded;
    }

    private TrainingSample toTrainingSample(SeedProfile seed) {
        return new TrainingSample(
            seed.sourceFamily(),
            seed.machineClass(),
            seed.sensorType(),
            seed.ratioToWarning(),
            seed.slopePerEvent(),
            seed.volatility(),
            seed.warningRate(),
            seed.criticalRate(),
            seed.ambientStress(),
            seed.maintenanceDebt(),
            seed.failureRisk(),
            seed.failureMode(),
            seed.recommendation()
        );
    }

    private void applyFallback(SensorEvent event, String confidence, String source) {
        int score = "CRITICAL".equals(event.getStatus()) ? 78 : 42;
        event.setAiRiskScore(score);
        event.setAiRiskLevel(score >= 70 ? "HIGH" : "MEDIUM");
        event.setAiIncidentSummary("Local predictive model fell back to threshold-based guidance.");
        event.setAiRecommendedAction("Inspect the machine and collect more recent telemetry.");
        event.setAiPredictedFailureEta("N/A");
        event.setAiConfidence(confidence);
        event.setModelRiskScore((double) score);
        event.setAnalysisSource(source);
    }

    private SensorEvent latestEvent(String deviceId) {
        return sortedHistory(deviceId).stream().reduce((first, second) -> second).orElse(null);
    }

    private List<SensorEvent> sortedHistory(String deviceId) {
        return repository.findByDeviceId(deviceId).stream()
            .sorted(Comparator.comparing(SensorEvent::getTimestamp))
            .toList();
    }

    private String inferMachineClass(String machineId) {
        if (machineId == null || machineId.isBlank()) {
            return "unknown";
        }
        String up = machineId.toUpperCase(Locale.ROOT);
        if (up.contains("COMP")) return "compressor";
        if (up.contains("PUMP")) return "pump";
        if (up.contains("TURB")) return "turbine";
        if (up.contains("FAN") || up.contains("BLOWER")) return "fan";
        if (up.contains("BOILER")) return "boiler";
        if (up.contains("GEN")) return "generator";
        return "unknown";
    }

    private double linearSlope(List<Double> values) {
        if (values.size() < 2) {
            return 0.0;
        }
        int n = values.size();
        double sumX = 0.0;
        double sumY = 0.0;
        double sumXY = 0.0;
        double sumXX = 0.0;
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
            return 0.0;
        }
        return (n * sumXY - sumX * sumY) / denominator;
    }

    private double stdDev(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        return Math.sqrt(variance);
    }

    private double safeThresholdRatio(Double value, double threshold, boolean lowIsBad) {
        if (value == null || threshold == 0.0) {
            return 0.0;
        }
        if (lowIsBad) {
            return threshold / Math.max(value, 0.0001);
        }
        return value / threshold;
    }

    private double normalizeAmbient(double value, double low, double high) {
        if (high <= low) {
            return 0.0;
        }
        return clamp((value - low) / (high - low), 0.0, 1.0);
    }

    private String topWeightedKey(Map<String, Double> weighted, String fallback) {
        return weighted.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(fallback);
    }

    private double jitter(double value, Random random, double amplitude) {
        return value + ((random.nextDouble() * 2.0) - 1.0) * amplitude;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round2(Double value) {
        if (value == null) {
            return 0.0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private List<String> defaultTrainingSources() {
        return List.of(
            "UCI AI4I 2020 Predictive Maintenance Dataset",
            "NASA C-MAPSS Aircraft Engine Simulator Data"
        );
    }

    private List<String> defaultPortfolioHighlights() {
        return List.of(
            "Named predictive ensemble with visible model attribution",
            "Local machine Q&A without paid inference APIs",
            "Weather-aware and maintenance-aware feature engineering",
            "Operator-facing dashboard with source transparency",
            "Cost-safe architecture for portfolio-ready demos"
        );
    }

    private record SeedProfile(
        String sourceFamily,
        String machineClass,
        String sensorType,
        double ratioToWarning,
        double slopePerEvent,
        double volatility,
        double warningRate,
        double criticalRate,
        double ambientStress,
        double maintenanceDebt,
        int failureRisk,
        String failureMode,
        String recommendation
    ) {}

    private record TrainingSample(
        String sourceFamily,
        String machineClass,
        String sensorType,
        double ratioToWarning,
        double slopePerEvent,
        double volatility,
        double warningRate,
        double criticalRate,
        double ambientStress,
        double maintenanceDebt,
        int failureRisk,
        String failureMode,
        String recommendation
    ) {}

    private record TrainingMatch(
        TrainingSample sample,
        double distance,
        double weight
    ) {
        private TrainingMatch(TrainingSample sample, double distance) {
            this(sample, distance, 0.0);
        }
    }

    private record NeighborAggregate(
        double weightedRisk,
        String failureMode,
        List<String> sources,
        double consensusStrength
    ) {}

    private record MachineFeatureVector(
        String machineClass,
        String sensorType,
        double ratioToWarning,
        double ratioToCritical,
        double slopePerEvent,
        double volatility,
        double warningRate,
        double criticalRate,
        double ambientStress,
        double maintenanceDebt,
        int eventsObserved,
        long warningCount,
        long criticalCount
    ) {}

    public record ModelSignal(
        String modelName,
        String family,
        int score,
        String interpretation,
        String method,
        String rationale
    ) {}

    public record ContextSnapshot(
        int eventsObserved,
        long warningEvents,
        long criticalEvents,
        double thresholdRatio,
        double slopePerEvent,
        double volatility,
        double ambientStress,
        double maintenanceDebt
    ) {}

    public record MachineIntelligenceReport(
        String machineId,
        String machineClass,
        String sensorType,
        Integer riskScore,
        String riskLevel,
        String confidence,
        String summary,
        String recommendedAction,
        String predictedFailureEta,
        String failureMode,
        List<String> topDrivers,
        List<String> trainingSources,
        String dataSource,
        List<ModelSignal> modelBreakdown,
        ContextSnapshot contextSnapshot,
        List<String> portfolioHighlights
    ) {}

    public record MachineChatResponse(
        String machineId,
        String question,
        String answer,
        List<String> evidence,
        Integer riskScore,
        String riskLevel,
        String dataSource,
        List<String> trainingSources
    ) {}
}
