package com.mahindra.iot.service;

import com.mahindra.iot.model.SensorEvent;
import com.mahindra.iot.repository.SensorEventRepository;
import com.mahindra.iot.service.LocalPredictiveIntelligenceService.MachineIntelligenceReport;
import com.mahindra.iot.service.LocalPredictiveIntelligenceService.ModelSignal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdvancedMachineIntelligenceService {

    private final LocalPredictiveIntelligenceService intelligenceService;
    private final SensorEventRepository repository;
    private final SensorContextEnricher sensorContextEnricher;

    public MachineDeepDive deepDive(String machineId) {
        MachineIntelligenceReport core = intelligenceService.analyzeMachine(machineId);
        List<SensorEvent> history = telemetryHistory(machineId);
        SensorEvent latest = history.isEmpty() ? null : history.get(history.size() - 1);
        SensorContextEnricher.SensorContext context = sensorContextEnricher.enrich(machineId, core.sensorType());

        double sensorDrift = computeSensorDrift(history, latest);
        double cascadePressure = computeCascadePressure(history, context);
        double recoveryInstability = computeRecoveryInstability(history);
        double evidenceCoverage = computeEvidenceCoverage(context);

        List<ModelSignal> expandedModelStack = new ArrayList<>(core.modelBreakdown());
        expandedModelStack.add(new ModelSignal(
            "ARGUS Sensor Drift Sentinel",
            "drift-model",
            score(sensorDrift * 92 + evidenceCoverage * 12),
            scoreBand(score(sensorDrift * 92 + evidenceCoverage * 12)),
            "Measures deviation from the machine's own recent baseline.",
            "Drift=" + round2(sensorDrift) + ", evidence=" + round2(evidenceCoverage)
        ));
        expandedModelStack.add(new ModelSignal(
            "ARGUS Cascade Stress Graph",
            "cascade-model",
            score(cascadePressure * 96),
            scoreBand(score(cascadePressure * 96)),
            "Looks for corroborating stress across multiple alerting signals.",
            "Cascade pressure=" + round2(cascadePressure)
        ));
        expandedModelStack.add(new ModelSignal(
            "ARGUS Recovery Stability",
            "stability-model",
            score(recoveryInstability * 96),
            scoreBand(score(recoveryInstability * 96)),
            "Measures whether the machine is oscillating rather than recovering cleanly.",
            "Recovery instability=" + round2(recoveryInstability)
        ));
        expandedModelStack.add(new ModelSignal(
            "ARGUS Alert Precision Engine",
            "decision-model",
            score(buildAlertPrecision(core, context, history).precisionScore()),
            scoreBand(score(buildAlertPrecision(core, context, history).precisionScore())),
            "Turns evidence coverage and corroboration into operator-facing alert trust.",
            buildAlertPrecision(core, context, history).note()
        ));
        expandedModelStack = expandedModelStack.stream()
            .sorted(Comparator.comparingInt(ModelSignal::score).reversed())
            .toList();

        AlertAccuracy alertAccuracy = buildAlertPrecision(core, context, history);
        RiskTrajectory riskTrajectory = buildRiskTrajectory(core, history);
        List<String> investigationChecklist = buildInvestigationChecklist(core, latest);
        List<String> watchlistReasons = buildWatchlistReasons(core, alertAccuracy, riskTrajectory, expandedModelStack);
        List<String> recommendedQuestions = buildRecommendedQuestions(core);
        String decisionNarrative = buildDecisionNarrative(core, alertAccuracy, riskTrajectory, expandedModelStack);

        return new MachineDeepDive(
            core,
            expandedModelStack,
            alertAccuracy,
            riskTrajectory,
            investigationChecklist,
            watchlistReasons,
            recommendedQuestions,
            decisionNarrative
        );
    }

    public EnhancedMachineChatResponse answerQuestion(String machineId, String question) {
        MachineDeepDive deepDive = deepDive(machineId);
        MachineIntelligenceReport core = deepDive.coreReport();
        String prompt = question == null ? "" : question.trim();
        String lower = prompt.toLowerCase(Locale.ROOT);

        String answer;
        List<String> evidence = new ArrayList<>();

        if (lower.contains("accuracy") || lower.contains("trust") || lower.contains("reliable")) {
            answer = "Alert trust for " + machineId + " is " + deepDive.alertAccuracy().precisionScore()
                + "/100 with " + deepDive.alertAccuracy().actionability() + " actionability. "
                + deepDive.alertAccuracy().note();
            evidence.addAll(deepDive.alertAccuracy().supportingSignals());
        } else if (lower.contains("checklist") || lower.contains("inspect") || lower.contains("action")) {
            answer = "Recommended checks for " + machineId + ": "
                + deepDive.investigationChecklist().stream().limit(3).reduce((a, b) -> a + "; " + b).orElse("No checklist available.");
            evidence.addAll(deepDive.investigationChecklist());
        } else if (lower.contains("watchlist") || lower.contains("fleet") || lower.contains("compare")) {
            List<FleetWatchlistEntry> watchlist = fleetWatchlist();
            int rank = 1;
            for (FleetWatchlistEntry entry : watchlist) {
                if (entry.machineId().equalsIgnoreCase(machineId)) {
                    answer = machineId + " is ranked #" + rank + " in the current fleet watchlist with "
                        + entry.riskScore() + "/100 risk. Primary reason: " + entry.topReason();
                    evidence.addAll(watchlist.stream()
                        .limit(3)
                        .map(item -> item.machineId() + ": " + item.riskScore() + "/100 | " + item.failureMode())
                        .toList());
                    return new EnhancedMachineChatResponse(machineId, prompt, answer, evidence, core.riskScore(), core.riskLevel(), core.dataSource(), core.trainingSources(), deepDive.recommendedQuestions());
                }
                rank++;
            }
            answer = machineId + " is not currently in the top fleet watchlist. Current risk is "
                + core.riskScore() + "/100 with " + core.riskLevel() + " severity.";
        } else if (lower.contains("root cause") || lower.contains("cause") || lower.contains("driver")) {
            answer = "Most likely failure pattern for " + machineId + " is " + core.failureMode()
                + ". " + deepDive.decisionNarrative();
            evidence.addAll(core.topDrivers());
        } else if (lower.contains("model") || lower.contains("why") || lower.contains("how")) {
            ModelSignal topModel = deepDive.expandedModelStack().isEmpty() ? null : deepDive.expandedModelStack().get(0);
            answer = "The leading model signal for " + machineId + " is "
                + (topModel == null ? "ARGUS Atlas Similarity" : topModel.modelName())
                + ". " + deepDive.decisionNarrative();
            if (topModel != null) {
                evidence.add(topModel.rationale());
            }
            evidence.addAll(core.topDrivers().stream().limit(3).toList());
        } else {
            answer = "Machine summary for " + machineId + ": " + core.summary()
                + " Recommended action: " + core.recommendedAction()
                + " Alert actionability: " + deepDive.alertAccuracy().actionability() + ".";
            evidence.addAll(deepDive.watchlistReasons());
        }

        return new EnhancedMachineChatResponse(
            machineId,
            prompt,
            answer,
            evidence,
            core.riskScore(),
            core.riskLevel(),
            core.dataSource(),
            core.trainingSources(),
            deepDive.recommendedQuestions()
        );
    }

    public List<FleetWatchlistEntry> fleetWatchlist() {
        Map<String, SensorEvent> latestPerMachine = new LinkedHashMap<>();
        for (SensorEvent event : repository.findAll()) {
            if (event.getDeviceId() == null || event.getDeviceId().isBlank() || event.getValue() == null) {
                continue;
            }
            SensorEvent current = latestPerMachine.get(event.getDeviceId());
            if (current == null || event.getTimestamp().compareTo(current.getTimestamp()) > 0) {
                latestPerMachine.put(event.getDeviceId(), event);
            }
        }

        return latestPerMachine.keySet().stream()
            .map(this::deepDive)
            .sorted(Comparator.comparingInt((MachineDeepDive dive) -> dive.coreReport().riskScore()).reversed())
            .limit(8)
            .map(dive -> new FleetWatchlistEntry(
                dive.coreReport().machineId(),
                dive.coreReport().riskScore(),
                dive.coreReport().riskLevel(),
                dive.coreReport().failureMode(),
                dive.coreReport().predictedFailureEta(),
                dive.watchlistReasons().isEmpty() ? "No immediate fleet reason" : dive.watchlistReasons().get(0),
                dive.alertAccuracy().actionability()
            ))
            .toList();
    }

    private AlertAccuracy buildAlertPrecision(MachineIntelligenceReport core,
                                              SensorContextEnricher.SensorContext context,
                                              List<SensorEvent> history) {
        long corroboratingSensors = history.stream()
            .filter(e -> "WARNING".equalsIgnoreCase(e.getStatus()) || "CRITICAL".equalsIgnoreCase(e.getStatus()))
            .map(SensorEvent::getSensorType)
            .filter(sensor -> sensor != null && !sensor.isBlank())
            .distinct()
            .count();
        double evidenceCoverage = computeEvidenceCoverage(context);

        int precisionScore = clampInt((int) Math.round(
            30
                + core.riskScore() * 0.18
                + evidenceCoverage * 24.0
                + Math.min(12.0, corroboratingSensors * 3.0)
                - (context.hasBadSensorData() ? 12.0 : 0.0)
        ), 18, 98);

        if ("HIGH".equalsIgnoreCase(core.confidence())) {
            precisionScore = clampInt(precisionScore + 6, 18, 98);
        } else if ("MEDIUM".equalsIgnoreCase(core.confidence())) {
            precisionScore = clampInt(precisionScore + 3, 18, 98);
        }

        String actionability = precisionScore >= 78 || (core.riskScore() >= 80 && precisionScore >= 62)
            ? "HIGH"
            : precisionScore >= 55
                ? "MEDIUM"
                : "LOW";

        List<String> supportingSignals = new ArrayList<>();
        supportingSignals.add("Evidence coverage: " + round2(evidenceCoverage));
        supportingSignals.add("Corroborating sensors: " + corroboratingSensors);
        supportingSignals.add("Confidence: " + core.confidence());
        if (context.hasBadSensorData()) {
            supportingSignals.add("Sensor quality issues require manual confirmation.");
        }

        String note = "Alert precision is " + precisionScore + "/100 with " + actionability
            + " actionability based on contextual coverage, corroborating signals, and current model confidence.";

        return new AlertAccuracy(precisionScore, actionability, note, supportingSignals);
    }

    private RiskTrajectory buildRiskTrajectory(MachineIntelligenceReport core, List<SensorEvent> history) {
        double slope = core.contextSnapshot().slopePerEvent();
        String shortTermTrend = Math.abs(slope) >= 0.06
            ? (slope > 0 ? "ACCELERATING" : "COOLING")
            : "STABLE";
        String nextWindow = core.riskScore() >= 80
            ? "Immediate watch"
            : core.riskScore() >= 60
                ? "Next 4-8 hours"
                : core.riskScore() >= 35
                    ? "Next shift"
                    : "Routine monitoring";
        long criticalEvents = history.stream().filter(e -> "CRITICAL".equalsIgnoreCase(e.getStatus())).count();
        String escalationTrigger = criticalEvents >= 2
            ? "Repeated critical events make this a rapid escalation candidate."
            : "Escalate if the primary sensor sustains critical behavior or a second sensor corroborates the fault.";
        return new RiskTrajectory(shortTermTrend, nextWindow, escalationTrigger);
    }

    private List<String> buildInvestigationChecklist(MachineIntelligenceReport core, SensorEvent latest) {
        List<String> checklist = new ArrayList<>();
        checklist.add("Confirm the latest " + core.sensorType() + " value against historian or panel data.");
        checklist.add("Inspect the machine for signs of " + core.failureMode() + ".");
        if (latest != null) {
            switch (String.valueOf(latest.getSensorType()).toUpperCase(Locale.ROOT)) {
                case "VIBRATION" -> {
                    checklist.add("Check alignment, looseness, and bearing condition.");
                    checklist.add("Correlate with bearing temperature and motor current.");
                }
                case "BEARING_TEMPERATURE", "TEMPERATURE" -> {
                    checklist.add("Review cooling, lubrication, and recent load changes.");
                    checklist.add("Inspect related components for heat spread.");
                }
                case "GAS_LEAK" -> {
                    checklist.add("Validate detector reading with a portable gas instrument.");
                    checklist.add("Review wind and ventilation status before area escalation.");
                }
                default -> checklist.add("Review adjacent sensor channels and recent maintenance notes.");
            }
        }
        return checklist.stream().limit(6).toList();
    }

    private List<String> buildWatchlistReasons(MachineIntelligenceReport core,
                                               AlertAccuracy alertAccuracy,
                                               RiskTrajectory riskTrajectory,
                                               List<ModelSignal> expandedModelStack) {
        List<String> reasons = new ArrayList<>();
        reasons.add("Actionability is " + alertAccuracy.actionability() + " with precision " + alertAccuracy.precisionScore() + "/100.");
        reasons.add("Watch window: " + riskTrajectory.nextWindow() + ".");
        reasons.add("Leading model: " + (expandedModelStack.isEmpty() ? "ARGUS Atlas Similarity" : expandedModelStack.get(0).modelName()) + ".");
        reasons.addAll(core.topDrivers().stream().limit(2).toList());
        return reasons.stream().limit(5).toList();
    }

    private List<String> buildRecommendedQuestions(MachineIntelligenceReport core) {
        return List.of(
            "How accurate is this alert and can I trust it?",
            "What should I inspect first on this machine?",
            "Which model is driving the risk right now?",
            "How does this machine compare with the fleet watchlist?",
            "What is the most likely failure mode for " + core.machineId() + "?"
        );
    }

    private String buildDecisionNarrative(MachineIntelligenceReport core,
                                          AlertAccuracy alertAccuracy,
                                          RiskTrajectory riskTrajectory,
                                          List<ModelSignal> expandedModelStack) {
        String leadModel = expandedModelStack.isEmpty() ? "ARGUS Atlas Similarity" : expandedModelStack.get(0).modelName();
        return "Primary operating concern is " + core.failureMode()
            + ", led by " + leadModel
            + ". Alert precision is " + alertAccuracy.precisionScore()
            + "/100 with " + alertAccuracy.actionability()
            + " actionability. Recommended watch window: " + riskTrajectory.nextWindow() + ".";
    }

    private List<SensorEvent> telemetryHistory(String machineId) {
        return repository.findByDeviceId(machineId).stream()
            .filter(e -> e.getSensorType() != null && !e.getSensorType().isBlank())
            .filter(e -> e.getValue() != null)
            .sorted(Comparator.comparing(SensorEvent::getTimestamp))
            .toList();
    }

    private double computeSensorDrift(List<SensorEvent> history, SensorEvent latest) {
        if (latest == null || latest.getValue() == null || history.isEmpty()) {
            return 0.0;
        }
        double mean = history.stream().map(SensorEvent::getValue).mapToDouble(Double::doubleValue).average().orElse(latest.getValue());
        double denominator = Math.max(Math.abs(mean), 1.0);
        return clamp(Math.abs(latest.getValue() - mean) / denominator, 0.0, 1.5);
    }

    private double computeCascadePressure(List<SensorEvent> history, SensorContextEnricher.SensorContext context) {
        long distinctAlertSensors = history.stream()
            .filter(e -> "WARNING".equalsIgnoreCase(e.getStatus()) || "CRITICAL".equalsIgnoreCase(e.getStatus()))
            .map(SensorEvent::getSensorType)
            .filter(sensor -> sensor != null && !sensor.isBlank())
            .distinct()
            .count();
        long alertCount = history.stream()
            .filter(e -> "WARNING".equalsIgnoreCase(e.getStatus()) || "CRITICAL".equalsIgnoreCase(e.getStatus()))
            .count();
        double total = Math.max(1.0, history.size());
        return clamp(
            ((double) distinctAlertSensors / 4.0)
                + (context.recentAlerts().size() * 0.08)
                + (alertCount / total) * 0.45,
            0.0,
            1.0
        );
    }

    private double computeRecoveryInstability(List<SensorEvent> history) {
        List<Double> values = history.stream().map(SensorEvent::getValue).toList();
        if (values.size() < 4) {
            return 0.0;
        }
        int signChanges = 0;
        Double previousDelta = null;
        for (int i = 1; i < values.size(); i++) {
            double delta = values.get(i) - values.get(i - 1);
            if (previousDelta != null && Math.signum(previousDelta) != 0 && Math.signum(delta) != 0
                && Math.signum(previousDelta) != Math.signum(delta)) {
                signChanges++;
            }
            previousDelta = delta;
        }
        return clamp((double) signChanges / Math.max(1.0, values.size() - 2.0), 0.0, 1.0);
    }

    private double computeEvidenceCoverage(SensorContextEnricher.SensorContext context) {
        return clamp(
            Math.min(1.0, context.dataPointsAvailable() / 72.0) * 0.58
                + (context.hasMaintHistory() ? 0.18 : 0.0)
                + (context.hasWeatherContext() ? 0.12 : 0.0)
                + (context.hasBadSensorData() ? 0.0 : 0.12),
            0.0,
            1.0
        );
    }

    private int score(double raw) {
        return clampInt((int) Math.round(raw), 5, 98);
    }

    private String scoreBand(int score) {
        if (score >= 80) return "CRITICAL";
        if (score >= 60) return "HIGH";
        if (score >= 35) return "MEDIUM";
        return "LOW";
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record AlertAccuracy(
        int precisionScore,
        String actionability,
        String note,
        List<String> supportingSignals
    ) {}

    public record RiskTrajectory(
        String shortTermTrend,
        String nextWindow,
        String escalationTrigger
    ) {}

    public record MachineDeepDive(
        MachineIntelligenceReport coreReport,
        List<ModelSignal> expandedModelStack,
        AlertAccuracy alertAccuracy,
        RiskTrajectory riskTrajectory,
        List<String> investigationChecklist,
        List<String> watchlistReasons,
        List<String> recommendedQuestions,
        String decisionNarrative
    ) {}

    public record EnhancedMachineChatResponse(
        String machineId,
        String question,
        String answer,
        List<String> evidence,
        Integer riskScore,
        String riskLevel,
        String dataSource,
        List<String> trainingSources,
        List<String> recommendedQuestions
    ) {}

    public record FleetWatchlistEntry(
        String machineId,
        Integer riskScore,
        String riskLevel,
        String failureMode,
        String predictedFailureEta,
        String topReason,
        String alertActionability
    ) {}
}
