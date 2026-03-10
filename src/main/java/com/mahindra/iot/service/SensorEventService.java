package com.mahindra.iot.service;

import com.mahindra.iot.config.ThresholdConfig;
import com.mahindra.iot.dto.SensorIngestRequest;
import com.mahindra.iot.enums.SensorType;
import com.mahindra.iot.model.Alert;
import com.mahindra.iot.model.SensorEvent;
import com.mahindra.iot.repository.SensorEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensorEventService {

    private final SensorEventRepository repository;
    private final ThresholdConfig thresholdConfig;
    private final SnsAlertService snsAlertService;
    private final SqsService sqsService;
    private final WeatherCorrelationService weatherCorrelationService;
    private final MultiLlmAnalysisService multiLlmAnalysisService;
    private final RedisTemplate<Object, Object> redisTemplate;

    private static final String LIVE_KEY   = "live:events";
    private static final String ALERTS_KEY = "alerts:latest";
    private static final int    MAX_LIVE   = 50;
    private final Deque<SensorEvent> liveFallback = new ConcurrentLinkedDeque<>();
    private final Map<String, Alert> alertsFallback = new ConcurrentHashMap<>();

    @CacheEvict(value = {"live-dashboard", "device-stats"}, allEntries = true)
    public SensorEvent ingestEvent(SensorIngestRequest req) {
        String timestamp = Instant.now().toString();
        String status    = thresholdConfig.evaluate(req.getSensorType(), req.getValue());
        SensorType type  = SensorType.fromString(req.getSensorType());

        SensorEvent event = SensorEvent.builder()
                .deviceId(req.getDeviceId())
                .timestamp(timestamp)
                .sensorType(req.getSensorType().toUpperCase())
                .sensorCategory(type.getCategory())
                .unit(req.getUnit() != null ? req.getUnit() : type.getUnit())
                .value(req.getValue())
                .status(status)
                .location(req.getLocation() != null ? req.getLocation() : "unknown")
                .facilityId(req.getFacilityId() != null ? req.getFacilityId() : "FACTORY-HYD-001")
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .warningThreshold(thresholdConfig.getWarningThreshold(req.getSensorType()))
                .criticalThreshold(thresholdConfig.getCriticalThreshold(req.getSensorType()))
                .minValue(req.getMinValue())
                .maxValue(req.getMaxValue())
                .avgValue(req.getAvgValue())
                .deltaFromPrevious(req.getDeltaFromPrevious())
                .processedAt(timestamp)
                .build();

        // 1. Weather correlation
        if (!Boolean.TRUE.equals(req.getSkipWeatherCorrelation())) {
            weatherCorrelationService.enrich(event);
        }

        // 2. AI / LLM analysis (only for WARNING or CRITICAL)
        if (!Boolean.TRUE.equals(req.getSkipAiAnalysis())) {
            multiLlmAnalysisService.analyze(event);
        }

        // 3. Save to DynamoDB
        repository.save(event);

        // 4. Push to cache (Redis + in-memory fallback)
        cacheLiveEvent(event);

        // 5. Fire alert if threshold breached
        if ("WARNING".equals(status) || "CRITICAL".equals(status)) {
            fireAlert(event);
        }

        log.info("Ingested: device={}, type={}, value={}, status={}",
                req.getDeviceId(), req.getSensorType(), req.getValue(), status);
        return event;
    }

    private void fireAlert(SensorEvent event) {
        String alertId = UUID.randomUUID().toString();
        Alert alert = Alert.builder()
                .alertId(alertId)
                .deviceId(event.getDeviceId())
                .sensorType(event.getSensorType())
                .value(event.getValue())
                .threshold(event.getWarningThreshold())
                .severity(event.getStatus())
                .message(String.format("[%s] %s on %s = %.2f (threshold %.2f)",
                        event.getStatus(), event.getSensorType(), event.getDeviceId(),
                        event.getValue(), event.getWarningThreshold()))
                .timestamp(event.getTimestamp())
                .weatherNote(event.getWeatherCorrelationNote())
                .aiSummary(event.getAiIncidentSummary())
                .aiRiskScore(event.getAiRiskScore())
                .build();

        String snsId = snsAlertService.publishAlert(alert);
        String sqsId = sqsService.enqueueAlert(alert);
        alert.setSnsMessageId(snsId);
        alert.setSqsMessageId(sqsId);

        event.setAlertId(alertId);
        event.setSnsMessageId(snsId);
        event.setSqsMessageId(sqsId);
        repository.save(event);

        cacheAlert(event.getDeviceId(), alert);
        log.warn("ALERT: alertId={}, device={}, severity={}, riskScore={}",
                alertId, event.getDeviceId(), event.getStatus(), event.getAiRiskScore());
    }

    @Cacheable("live-dashboard")
    public List<Object> getLiveDashboard() {
        try {
            List<Object> events = redisTemplate.opsForList().range(LIVE_KEY, 0, MAX_LIVE - 1);
            if (events != null && !events.isEmpty()) {
                return events;
            }
        } catch (Exception e) {
            log.debug("Redis live dashboard unavailable, serving in-memory fallback: {}", e.getMessage());
        }
        return liveFallback.stream().map(e -> (Object) e).toList();
    }

    @Cacheable(value = "device-stats", key = "#deviceId")
    public Map<String, Object> getDeviceStats(String deviceId) {
        List<SensorEvent> events = repository.findByDeviceId(deviceId);
        long warnings  = events.stream().filter(e -> "WARNING".equals(e.getStatus())).count();
        long criticals = events.stream().filter(e -> "CRITICAL".equals(e.getStatus())).count();
        OptionalDouble avg = events.stream().mapToDouble(SensorEvent::getValue).average();
        return Map.of(
                "deviceId", deviceId,
                "totalEvents", events.size(),
                "warnings", warnings,
                "criticals", criticals,
                "averageValue", avg.orElse(0.0),
                "latestEvent", events.isEmpty() ? "N/A" : events.get(0).getTimestamp()
        );
    }

    @Cacheable("alert-history")
    public Map<Object, Object> getAlertHistory() {
        try {
            Map<Object, Object> alerts = redisTemplate.opsForHash().entries(ALERTS_KEY);
            if (alerts != null && !alerts.isEmpty()) {
                return alerts;
            }
        } catch (Exception e) {
            log.debug("Redis alert history unavailable, serving in-memory fallback: {}", e.getMessage());
        }

        Map<Object, Object> copy = new HashMap<>();
        alertsFallback.forEach(copy::put);
        return copy;
    }

    public List<SensorEvent> getDeviceHistory(String deviceId) {
        return repository.findByDeviceId(deviceId);
    }

    private void cacheLiveEvent(SensorEvent event) {
        liveFallback.addFirst(event);
        while (liveFallback.size() > MAX_LIVE) {
            liveFallback.pollLast();
        }

        try {
            redisTemplate.opsForList().leftPush(LIVE_KEY, event);
            redisTemplate.opsForList().trim(LIVE_KEY, 0, MAX_LIVE - 1);
        } catch (Exception e) {
            log.debug("Redis live cache write failed, using in-memory fallback only: {}", e.getMessage());
        }
    }

    private void cacheAlert(String deviceId, Alert alert) {
        alertsFallback.put(deviceId, alert);
        try {
            redisTemplate.opsForHash().put(ALERTS_KEY, deviceId, alert);
        } catch (Exception e) {
            log.debug("Redis alert cache write failed, using in-memory fallback only: {}", e.getMessage());
        }
    }
}
