package com.mahindra.iot.service;

import com.mahindra.iot.model.SensorEvent;
import com.mahindra.iot.repository.SensorEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensorContextEnricher {

    private static final int MAINT_HISTORY_THRESHOLD = 24;

    private final AthenaAnalyticsService athenaAnalyticsService;
    private final SensorEventRepository sensorEventRepository;
    private final WeatherService weatherService;
    private final SensorHealthMonitor sensorHealthMonitor;

    public SensorContext enrich(String machineId, String sensorType) {
        String machineClass = inferMachineClass(machineId);
        List<SensorEvent> deviceEvents = sensorEventRepository.findByDeviceId(machineId);

        List<Map<String, Object>> trend = athenaAnalyticsService
                .queryMachineTrend(machineId, 24, machineClass);

        if (trend.isEmpty()) {
            trend = fallbackTrend(deviceEvents, sensorType, machineClass);
        }

        List<RecentAlert> recentAlerts = deviceEvents.stream()
                .filter(e -> "WARNING".equalsIgnoreCase(e.getStatus())
                        || "CRITICAL".equalsIgnoreCase(e.getStatus())
                        || e.getAlertId() != null)
                .sorted(Comparator.comparing(SensorEvent::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .map(e -> new RecentAlert(
                        e.getTimestamp(),
                        e.getSensorType(),
                        e.getStatus(),
                        e.getValue(),
                        e.getAiRiskScore()))
                .toList();

        int dataPoints = trend.size();
        boolean hasMaintHistory = dataPoints >= MAINT_HISTORY_THRESHOLD;
        List<SensorHealthMonitor.SensorHealthSnapshot> sensorHealth = sensorHealthMonitor.buildSensorSnapshots(machineId, deviceEvents);
        boolean hasBadSensorData = sensorHealth.stream().anyMatch(s -> "BAD".equalsIgnoreCase(s.quality().name()));
        String dataQualityNotice = hasBadSensorData
                ? "WARNING: sensor data quality BAD - analysis confidence reduced."
                : "Sensor data quality within expected range.";

        WeatherService.WeatherData weather = weatherService.getWeather(17.3850, 78.4867);
        WeatherContext weatherContext = new WeatherContext(
                weather.getTempC(),
                weather.getHumidity(),
                weather.getCondition(),
                weather.getWindSpeed(),
                weather.isAvailable()
        );

        EquipmentMaster equipmentMaster = resolveEquipmentMaster(machineId, machineClass, hasMaintHistory);

        return new SensorContext(
                machineId,
                sensorType,
                machineClass,
                trend,
                recentAlerts,
                equipmentMaster,
                weatherContext,
                sensorHealth,
                dataQualityNotice,
                dataPoints,
                hasMaintHistory,
                weatherContext.available(),
                hasBadSensorData
        );
    }

    public Map<String, Object> toPromptMap(SensorContext context) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("machineId", context.machineId());
        map.put("sensorType", context.sensorType());
        map.put("machineClass", context.machineClass());
        map.put("dataPointsAvailable", context.dataPointsAvailable());
        map.put("hasMaintHistory", context.hasMaintHistory());
        map.put("weatherContext", context.weatherContext());
        map.put("equipmentMaster", context.equipmentMaster());
        map.put("sensorDataQuality", context.sensorHealth());
        map.put("dataQualityNotice", context.dataQualityNotice());
        map.put("recentAlerts", context.recentAlerts());
        map.put("trendSample", context.trend().stream().limit(10).toList());
        return map;
    }

    private List<Map<String, Object>> fallbackTrend(List<SensorEvent> events, String sensorType, String machineClass) {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        return events.stream()
                .filter(e -> sensorType == null || sensorType.isBlank()
                        || sensorType.equalsIgnoreCase(e.getSensorType()))
                .filter(e -> parseInstantSafe(e.getTimestamp()).isAfter(cutoff))
                .sorted(Comparator.comparing(SensorEvent::getTimestamp))
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("event_timestamp", e.getTimestamp());
                    row.put("sensor_type", e.getSensorType());
                    row.put("value", e.getValue());
                    row.put("status", e.getStatus());
                    row.put("machine_class", machineClass);
                    row.put("source", "DYNAMODB_FALLBACK");
                    return row;
                })
                .toList();
    }

    private EquipmentMaster resolveEquipmentMaster(String machineId, String machineClass, boolean hasMaintHistory) {
        return new EquipmentMaster(
                machineId,
                machineClass,
                hasMaintHistory ? "HISTORY_PRESENT" : "HISTORY_LIMITED",
                "Demo master data (synthetic)"
        );
    }

    private Instant parseInstantSafe(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.EPOCH;
        }
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

    public record SensorContext(
            String machineId,
            String sensorType,
            String machineClass,
            List<Map<String, Object>> trend,
            List<RecentAlert> recentAlerts,
            EquipmentMaster equipmentMaster,
            WeatherContext weatherContext,
            List<SensorHealthMonitor.SensorHealthSnapshot> sensorHealth,
            String dataQualityNotice,
            int dataPointsAvailable,
            boolean hasMaintHistory,
            boolean hasWeatherContext,
            boolean hasBadSensorData
    ) {
    }

    public record RecentAlert(
            String timestamp,
            String sensorType,
            String severity,
            Double value,
            Integer riskScore
    ) {
    }

    public record EquipmentMaster(
            String machineId,
            String machineClass,
            String maintenanceHistory,
            String note
    ) {
    }

    public record WeatherContext(
            double tempC,
            double humidityPct,
            String condition,
            double windSpeedMs,
            boolean available
    ) {
    }
}
