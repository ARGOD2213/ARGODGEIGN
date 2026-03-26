package com.mahindra.iot.service;

import com.mahindra.iot.model.SensorEvent;
import com.mahindra.iot.repository.SensorEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdvisoryWorkflowService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_REVIEWED = "REVIEWED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String DEFAULT_RULE_VERSION = "thresholds/v1";

    private final SensorEventRepository sensorEventRepository;

    public void initializeDraftEvidence(SensorEvent event) {
        if (event.getAlertId() == null || event.getAlertId().isBlank()) {
            return;
        }

        if (event.getAiEvidenceId() == null || event.getAiEvidenceId().isBlank()) {
            event.setAiEvidenceId("evidence-" + UUID.randomUUID());
        }
        event.setAiReviewStatus(STATUS_DRAFT);
        event.setAiReviewedBy("UNASSIGNED");
        event.setAiReviewNote("Awaiting human review");
        event.setAiReviewedAt(null);
        event.setAiEvidenceCapturedAt(Instant.now().toString());
        event.setAiInputTags(buildInputTags(event));
        event.setAiRuleVersion(DEFAULT_RULE_VERSION);
        event.setAiModelVersion(resolveModelVersion(event));
    }

    public AdvisoryDecision review(String alertId, String reviewer, String note) {
        SensorEvent event = getRequiredEvent(alertId);
        requireCurrentState(event, Set.of(STATUS_DRAFT));
        return persistDecision(event, STATUS_REVIEWED, reviewer, note);
    }

    public AdvisoryDecision approve(String alertId, String reviewer, String note) {
        SensorEvent event = getRequiredEvent(alertId);
        requireCurrentState(event, Set.of(STATUS_REVIEWED));
        return persistDecision(event, STATUS_APPROVED, reviewer, note);
    }

    public AdvisoryDecision reject(String alertId, String reviewer, String note) {
        SensorEvent event = getRequiredEvent(alertId);
        requireCurrentState(event, Set.of(STATUS_DRAFT, STATUS_REVIEWED));
        return persistDecision(event, STATUS_REJECTED, reviewer, note);
    }

    public AdvisoryEvidence getEvidence(String alertId) {
        return toEvidence(getRequiredEvent(alertId));
    }

    public List<AdvisoryEvidence> getRecentEvidence() {
        return sensorEventRepository.findAll().stream()
                .filter(event -> event.getAlertId() != null && !event.getAlertId().isBlank())
                .sorted(Comparator.comparing(
                        SensorEvent::getProcessedAt,
                        Comparator.nullsLast(String::compareTo)
                ).reversed())
                .limit(25)
                .map(this::toEvidence)
                .toList();
    }

    public Map<String, Long> getApprovalSummary() {
        Map<String, Long> counts = sensorEventRepository.findAll().stream()
                .filter(event -> event.getAlertId() != null && !event.getAlertId().isBlank())
                .collect(Collectors.groupingBy(
                        event -> normalizeStatus(event.getAiReviewStatus()),
                        Collectors.counting()
                ));

        return Map.of(
                STATUS_DRAFT, counts.getOrDefault(STATUS_DRAFT, 0L),
                STATUS_REVIEWED, counts.getOrDefault(STATUS_REVIEWED, 0L),
                STATUS_APPROVED, counts.getOrDefault(STATUS_APPROVED, 0L),
                STATUS_REJECTED, counts.getOrDefault(STATUS_REJECTED, 0L)
        );
    }

    private AdvisoryDecision persistDecision(SensorEvent event, String nextState, String reviewer, String note) {
        event.setAiReviewStatus(nextState);
        event.setAiReviewedBy(defaultReviewer(reviewer));
        event.setAiReviewNote((note == null || note.isBlank()) ? defaultNote(nextState) : note);
        event.setAiReviewedAt(Instant.now().toString());
        sensorEventRepository.save(event);
        return new AdvisoryDecision(
                event.getAlertId(),
                event.getAiReviewStatus(),
                event.getAiReviewedBy(),
                event.getAiReviewedAt(),
                event.getAiReviewNote(),
                event.getAiEvidenceId()
        );
    }

    private AdvisoryEvidence toEvidence(SensorEvent event) {
        return new AdvisoryEvidence(
                event.getAlertId(),
                event.getDeviceId(),
                event.getSensorType(),
                event.getStatus(),
                normalizeStatus(event.getAiReviewStatus()),
                event.getAiEvidenceId(),
                event.getAiEvidenceCapturedAt(),
                splitTags(event.getAiInputTags()),
                event.getAiRuleVersion(),
                event.getAiModelVersion(),
                event.getAiConfidence(),
                event.getAiReviewedBy(),
                event.getAiReviewedAt(),
                event.getAiReviewNote(),
                event.getAiIncidentSummary(),
                event.getAiRecommendedAction()
        );
    }

    private SensorEvent getRequiredEvent(String alertId) {
        return sensorEventRepository.findByAlertId(alertId)
                .orElseThrow(() -> new IllegalArgumentException("No advisory found for alertId=" + alertId));
    }

    private void requireCurrentState(SensorEvent event, Set<String> allowedStates) {
        String currentState = normalizeStatus(event.getAiReviewStatus());
        if (!allowedStates.contains(currentState)) {
            throw new IllegalStateException(
                    "Invalid advisory transition from " + currentState + " to requested state"
            );
        }
    }

    private String buildInputTags(SensorEvent event) {
        return List.of(
                        event.getSensorType(),
                        event.getSensorCategory(),
                        event.getDeviceId(),
                        event.getLocation(),
                        event.getFacilityId(),
                        event.getWeatherCondition(),
                        Boolean.TRUE.equals(event.getWeatherAlertActive()) ? "WEATHER_ALERT" : "NO_WEATHER_ALERT"
                ).stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(","));
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return List.of(tags.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String resolveModelVersion(SensorEvent event) {
        String analysisSource = event.getAnalysisSource() == null ? "" : event.getAnalysisSource().toUpperCase();
        if (!analysisSource.isBlank()) {
            return analysisSource;
        }
        String consensus = event.getLlmConsensus() == null ? "" : event.getLlmConsensus().toUpperCase();
        if (consensus.contains("GEMINI")) {
            return "gemini-1.5-pro";
        }
        if (consensus.contains("CACHED")) {
            return "cache/gemini-1.5-pro";
        }
        return "rule-based/v1";
    }

    private String normalizeStatus(String status) {
        return (status == null || status.isBlank()) ? STATUS_DRAFT : status;
    }

    private String defaultReviewer(String reviewer) {
        return (reviewer == null || reviewer.isBlank()) ? "UNASSIGNED" : reviewer;
    }

    private String defaultNote(String state) {
        return switch (state) {
            case STATUS_REVIEWED -> "Human review completed";
            case STATUS_APPROVED -> "Approved for operator use";
            case STATUS_REJECTED -> "Rejected during human review";
            default -> "Awaiting human review";
        };
    }

    public record AdvisoryDecision(
            String alertId,
            String reviewStatus,
            String reviewedBy,
            String reviewedAt,
            String reviewNote,
            String evidenceId
    ) {}

    public record AdvisoryEvidence(
            String alertId,
            String deviceId,
            String sensorType,
            String severity,
            String reviewStatus,
            String evidenceId,
            String evidenceCapturedAt,
            List<String> inputTags,
            String ruleVersion,
            String modelVersion,
            String confidence,
            String reviewedBy,
            String reviewedAt,
            String reviewNote,
            String analysis,
            String recommendedAction
    ) {}
}
