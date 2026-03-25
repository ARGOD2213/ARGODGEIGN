package com.mahindra.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictiveAnalysisCacheService {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${ai.analysis.cache.bucket:iot-alert-engine-mahindra}")
    private String bucket;

    @Value("${ai.analysis.cache.prefix:predictive-analysis-cache}")
    private String prefix;

    @Value("${ai.analysis.cache.ttl.minutes:30}")
    private long ttlMinutes;

    private final Map<String, CachedAnalysis> memoryFallback = new ConcurrentHashMap<>();

    public Optional<CachedAnalysis> readFresh(String machineId) {
        if (machineId == null || machineId.isBlank()) {
            return Optional.empty();
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(buildKey(machineId))
                    .build();

            try (ResponseInputStream<?> in = s3Client.getObject(request)) {
                CachedAnalysis analysis = objectMapper.readValue(in, CachedAnalysis.class);
                if (analysis == null || analysis.cachedAt() == null) {
                    return Optional.empty();
                }

                Instant freshnessLimit = Instant.now().minus(Math.max(1, ttlMinutes), ChronoUnit.MINUTES);
                Instant cachedAt = Instant.parse(analysis.cachedAt());
                if (cachedAt.isBefore(freshnessLimit)) {
                    return Optional.empty();
                }

                return Optional.of(analysis);
            }
        } catch (NoSuchKeyException e) {
            return readMemoryFallback(machineId);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return readMemoryFallback(machineId);
            }
            log.debug("S3 cache read failed for {}: {}", machineId, e.awsErrorDetails() != null
                    ? e.awsErrorDetails().errorMessage() : e.getMessage());
            return readMemoryFallback(machineId);
        } catch (Exception e) {
            log.debug("Cache parse/read failed for {}: {}", machineId, e.getMessage());
            return readMemoryFallback(machineId);
        }
    }

    public void write(String machineId, CachedAnalysis analysis) {
        if (machineId == null || machineId.isBlank() || analysis == null) {
            return;
        }

        memoryFallback.put(machineId, analysis);

        try {
            byte[] payload = objectMapper.writeValueAsBytes(analysis);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(buildKey(machineId))
                    .contentType("application/json")
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(payload));
        } catch (Exception e) {
            log.debug("S3 cache write failed for {}: {}", machineId, e.getMessage());
        }
    }

    private Optional<CachedAnalysis> readMemoryFallback(String machineId) {
        CachedAnalysis memory = memoryFallback.get(machineId);
        if (memory == null || memory.cachedAt() == null) {
            return Optional.empty();
        }
        try {
            Instant freshnessLimit = Instant.now().minus(Math.max(1, ttlMinutes), ChronoUnit.MINUTES);
            Instant cachedAt = Instant.parse(memory.cachedAt());
            if (cachedAt.isBefore(freshnessLimit)) {
                return Optional.empty();
            }
            return Optional.of(memory);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public CachedAnalysis fromEvent(String source, Integer riskScore, String riskLevel,
                                    String analysis, String recommendedAction,
                                    String predictedFailureEta, String confidence) {
        return new CachedAnalysis(
                source,
                riskScore,
                riskLevel,
                analysis,
                recommendedAction,
                predictedFailureEta,
                confidence,
                Instant.now().toString()
        );
    }

    private String buildKey(String machineId) {
        return String.format("%s/%s/latest.json", prefix, machineId);
    }

    public record CachedAnalysis(
            String source,
            Integer riskScore,
            String riskLevel,
            String analysis,
            String recommendedAction,
            String predictedFailureEta,
            String confidence,
            String cachedAt
    ) {
    }
}
