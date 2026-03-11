package com.mahindra.iot.service;

import com.mahindra.iot.model.SensorEvent;
import com.mahindra.iot.repository.SensorEventRepository;
import com.mahindra.iot.util.AiAdvisoryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MachineOpsService {

    private final SensorEventRepository repository;
    private final AthenaAnalyticsService athenaAnalyticsService;

    @Cacheable("machines-list")
    public List<Map<String, Object>> getMachines() {
        List<SensorEvent> allEvents = repository.findAll();

        Map<String, SensorEvent> latestPerMachine = new LinkedHashMap<>();
        for (SensorEvent event : allEvents) {
            if (event.getDeviceId() == null || event.getTimestamp() == null) {
                continue;
            }

            SensorEvent current = latestPerMachine.get(event.getDeviceId());
            if (current == null || event.getTimestamp().compareTo(current.getTimestamp()) > 0) {
                latestPerMachine.put(event.getDeviceId(), event);
            }
        }

        return latestPerMachine.values().stream()
                .sorted(Comparator.comparing(SensorEvent::getDeviceId))
                .map(this::toMachineSummary)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "machine-alerts", key = "#machineId + ':' + #limit")
    public List<Map<String, Object>> getMachineAlerts(String machineId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));

        return repository.findByDeviceId(machineId).stream()
                .filter(e -> "WARNING".equalsIgnoreCase(e.getStatus())
                        || "CRITICAL".equalsIgnoreCase(e.getStatus())
                        || e.getAlertId() != null)
                .sorted(Comparator.comparing(SensorEvent::getTimestamp).reversed())
                .limit(safeLimit)
                .map(this::toAlertSummary)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "machine-trends", key = "#machineId + ':' + #hours + ':' + #machineClassHint")
    public List<Map<String, Object>> getMachineTrend(String machineId, int hours, String machineClassHint) {
        int safeHours = Math.max(1, Math.min(hours, 168));

        List<Map<String, Object>> athenaRows = athenaAnalyticsService.queryMachineTrend(machineId, safeHours, machineClassHint);
        if (!athenaRows.isEmpty()) {
            List<Map<String, Object>> output = new ArrayList<>();
            for (Map<String, Object> row : athenaRows) {
                Map<String, Object> mapped = new LinkedHashMap<>();
                mapped.put("timestamp", row.getOrDefault("event_timestamp", ""));
                mapped.put("sensorType", row.getOrDefault("sensor_type", ""));
                mapped.put("value", toDouble(row.get("value")));
                mapped.put("status", row.getOrDefault("status", ""));
                mapped.put("machineClass", row.getOrDefault("machine_class", ""));
                mapped.put("source", "ATHENA");
                output.add(mapped);
            }
            return output;
        }

        // Fallback path when Athena is not configured or fails.
        Instant cutoff = Instant.now().minusSeconds((long) safeHours * 3600);
        return repository.findByDeviceId(machineId).stream()
                .filter(e -> parseInstantSafe(e.getTimestamp()).isAfter(cutoff))
                .sorted(Comparator.comparing(SensorEvent::getTimestamp))
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("timestamp", e.getTimestamp());
                    item.put("sensorType", e.getSensorType());
                    item.put("value", e.getValue());
                    item.put("status", e.getStatus());
                    item.put("machineClass", inferMachineClass(e.getDeviceId()));
                    item.put("source", "DYNAMODB_FALLBACK");
                    return item;
                })
                .collect(Collectors.toList());
    }

    @Cacheable(value = "kpi-oee", key = "#machineId + ':' + #hours + ':' + #machineClassHint")
    public Map<String, Object> getMachineOee(String machineId, int hours, String machineClassHint) {
        int safeHours = Math.max(1, Math.min(hours, 168));

        Optional<Map<String, Object>> athena = athenaAnalyticsService.queryMachineOee(machineId, safeHours, machineClassHint);
        if (athena.isPresent()) {
            return athena.get();
        }

        List<SensorEvent> rows = repository.findByDeviceId(machineId);
        if (rows.isEmpty()) {
            return Map.of(
                    "machineId", machineId,
                    "hours", safeHours,
                    "source", "NO_DATA",
                    "oeePercent", 0.0
            );
        }

        long total = rows.size();
        long available = rows.stream().filter(e -> !"CRITICAL".equalsIgnoreCase(e.getStatus())).count();
        long qualityGood = rows.stream().filter(e -> "NORMAL".equalsIgnoreCase(e.getStatus())).count();

        double avgValue = rows.stream()
                .map(SensorEvent::getValue)
                .filter(v -> v != null && !v.isNaN())
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        double maxRef = rows.stream()
                .map(SensorEvent::getMaxValue)
                .filter(v -> v != null && !v.isNaN() && v > 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        double availability = clamp01((double) available / total);
        double quality = clamp01((double) qualityGood / total);
        double performance = maxRef <= 0 ? 1.0 : clamp01(avgValue / maxRef);
        double oee = availability * performance * quality * 100.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("machineId", machineId);
        result.put("hours", safeHours);
        result.put("availability", round4(availability));
        result.put("performance", round4(performance));
        result.put("quality", round4(quality));
        result.put("oeePercent", round2(oee));
        result.put("totalEvents", total);
        result.put("source", "DYNAMODB_FALLBACK");
        return result;
    }

    private Map<String, Object> toMachineSummary(SensorEvent event) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("machineId", event.getDeviceId());
        summary.put("status", event.getStatus());
        summary.put("lastTimestamp", event.getTimestamp());
        summary.put("lastSensorType", event.getSensorType());
        summary.put("lastValue", event.getValue());
        summary.put("location", event.getLocation());
        summary.put("machineClass", inferMachineClass(event.getDeviceId()));
        summary.put("hasActiveAlert", event.getAlertId() != null
                || "WARNING".equalsIgnoreCase(event.getStatus())
                || "CRITICAL".equalsIgnoreCase(event.getStatus()));
        return summary;
    }

    private Map<String, Object> toAlertSummary(SensorEvent event) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("machineId", event.getDeviceId());
        alert.put("alertId", event.getAlertId());
        alert.put("timestamp", event.getTimestamp());
        alert.put("sensorType", event.getSensorType());
        alert.put("value", event.getValue());
        alert.put("status", event.getStatus());
        alert.put("aiRiskScore", event.getAiRiskScore());
        alert.put("aiSummary", event.getAiIncidentSummary());
        alert.put("weatherNote", event.getWeatherCorrelationNote());
        alert.put("aiAdvisory", AiAdvisoryWrapper.fromEvent(event));
        return alert;
    }

    private Instant parseInstantSafe(String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (Exception ex) {
            return Instant.EPOCH;
        }
    }

    private String inferMachineClass(String machineId) {
        if (machineId == null || machineId.isBlank()) {
            return "unknown";
        }

        String up = machineId.toUpperCase(Locale.ROOT);
        if (up.contains("COMP")) return "compressor";
        if (up.contains("PUMP")) return "pump";
        if (up.contains("REFORM")) return "reformer";
        if (up.contains("REACT")) return "reactor";
        if (up.contains("TURB")) return "turbine";
        if (up.contains("FAN") || up.contains("BLOWER")) return "fan";
        if (up.contains("BOILER")) return "boiler";
        if (up.contains("COND")) return "condenser";
        if (up.contains("STRIP")) return "stripper";
        if (up.contains("ABSORB")) return "absorber";
        if (up.contains("SCRUB")) return "scrubber";
        if (up.contains("EVAP")) return "evaporator";
        if (up.contains("TOWER") || up.contains("TWR")) return "tower";
        if (up.contains("GRAN")) return "granulator";
        if (up.contains("DRY")) return "dryer";
        if (up.contains("STORAGE") || up.contains("TANK")) return "storage";
        if (up.contains("GEN")) return "generator";
        if (up.contains("SEC")) return "security";
        if (up.contains("ENV")) return "environmental";
        return "unknown";
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
