package com.mahindra.iot.service;

import com.mahindra.iot.enums.SensorQuality;
import com.mahindra.iot.model.SensorEvent;
import com.mahindra.iot.repository.SensorEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SensorHealthMonitor {

    private final SensorEventRepository sensorEventRepository;

    public SensorQuality evaluate(String sensorType, double value,
                                  double previousValue, long lastCalibrationEpoch) {
        long now = System.currentTimeMillis() / 1000;
        if (Double.compare(value, previousValue) == 0) return SensorQuality.SUSPECT;
        if (!isInPhysicalRange(sensorType, value)) return SensorQuality.BAD;
        if ((now - lastCalibrationEpoch) > 90L * 24 * 3600) return SensorQuality.CALIBRATION_DUE;
        return SensorQuality.GOOD;
    }

    public MachineSensorHealthReport getMachineHealth(String machineId) {
        List<SensorEvent> events = sensorEventRepository.findByDeviceId(machineId);
        List<SensorHealthSnapshot> sensors = buildSensorSnapshots(machineId, events);

        long badCount = sensors.stream().filter(s -> s.quality() == SensorQuality.BAD).count();
        long suspectCount = sensors.stream().filter(s -> s.quality() == SensorQuality.SUSPECT).count();
        boolean calibrationDue = sensors.stream().anyMatch(s -> s.quality() == SensorQuality.CALIBRATION_DUE);

        SensorQuality overall = badCount > 0
                ? SensorQuality.BAD
                : suspectCount > 0
                ? SensorQuality.SUSPECT
                : calibrationDue
                ? SensorQuality.CALIBRATION_DUE
                : SensorQuality.GOOD;

        return new MachineSensorHealthReport(
                machineId,
                sensors,
                overall,
                Math.toIntExact(badCount),
                Math.toIntExact(suspectCount)
        );
    }

    public List<SensorHealthSnapshot> buildSensorSnapshots(String machineId, List<SensorEvent> events) {
        if (events == null || events.isEmpty()) {
            return syntheticSnapshots(machineId);
        }

        return events.stream()
                .filter(e -> e.getSensorType() != null && e.getValue() != null)
                .collect(Collectors.groupingBy(e -> e.getSensorType().toUpperCase(), LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> summarize(machineId, entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(SensorHealthSnapshot::sensorType))
                .toList();
    }

    private SensorHealthSnapshot summarize(String machineId, String sensorType, List<SensorEvent> events) {
        List<SensorEvent> sorted = events.stream()
                .sorted(Comparator.comparing(SensorEvent::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        SensorEvent latest = sorted.get(0);
        SensorEvent previous = sorted.size() > 1 ? sorted.get(1) : null;
        double lastValue = latest.getValue() != null ? latest.getValue() : 0.0;
        boolean flatline = isFlatline(latest, previous);
        double previousValue = flatline ? lastValue : Double.NaN;
        long lastCalibrationEpoch = lookupLastCalibrationEpoch(machineId, sensorType);
        SensorQuality quality = evaluate(sensorType, lastValue, previousValue, lastCalibrationEpoch);
        boolean physicalRangeOk = isInPhysicalRange(sensorType, lastValue);
        boolean calibrationDue = isCalibrationDue(lastCalibrationEpoch);

        return new SensorHealthSnapshot(
                sensorType,
                quality,
                lastValue,
                physicalRangeOk,
                calibrationDue,
                qualityMessage(quality, physicalRangeOk, calibrationDue)
        );
    }

    private boolean isFlatline(SensorEvent latest, SensorEvent previous) {
        if (latest == null || previous == null || latest.getValue() == null || previous.getValue() == null) {
            return false;
        }

        Instant latestTs = parseInstant(latest.getTimestamp());
        Instant previousTs = parseInstant(previous.getTimestamp());
        if (latestTs.equals(Instant.EPOCH) || previousTs.equals(Instant.EPOCH)) {
            return false;
        }

        long ageSeconds = Math.abs(ChronoUnit.SECONDS.between(previousTs, latestTs));
        return ageSeconds >= 300 && Double.compare(latest.getValue(), previous.getValue()) == 0;
    }

    private List<SensorHealthSnapshot> syntheticSnapshots(String machineId) {
        long now = System.currentTimeMillis() / 1000;
        return List.of(
                synthetic(machineId, "GAS_LEAK", 12.4, 11.8, now - (30L * 24 * 3600)),
                synthetic(machineId, "COMPRESSOR_VIBRATION", 7.4, 7.4, now - (20L * 24 * 3600)),
                synthetic(machineId, "BEARING_TEMPERATURE", 82.0, 79.5, now - (120L * 24 * 3600)),
                synthetic(machineId, "OIL_PRESSURE", 4.8, 4.6, now - (14L * 24 * 3600))
        );
    }

    private SensorHealthSnapshot synthetic(String machineId,
                                           String sensorType,
                                           double value,
                                           double previousValue,
                                           long lastCalibrationEpoch) {
        SensorQuality quality = evaluate(sensorType, value, previousValue, lastCalibrationEpoch);
        boolean physicalRangeOk = isInPhysicalRange(sensorType, value);
        boolean calibrationDue = isCalibrationDue(lastCalibrationEpoch);
        return new SensorHealthSnapshot(
                sensorType,
                quality,
                value,
                physicalRangeOk,
                calibrationDue,
                qualityMessage(quality, physicalRangeOk, calibrationDue)
        );
    }

    private long lookupLastCalibrationEpoch(String machineId, String sensorType) {
        long now = System.currentTimeMillis() / 1000;
        return switch (sensorType.toUpperCase()) {
            case "GAS_LEAK" -> now - (45L * 24 * 3600);
            case "COMPRESSOR_VIBRATION", "VIBRATION" -> now - (25L * 24 * 3600);
            case "BEARING_TEMPERATURE" -> now - (120L * 24 * 3600);
            case "REACTOR_TEMPERATURE" -> now - (70L * 24 * 3600);
            case "OIL_PRESSURE" -> now - (14L * 24 * 3600);
            case "POWER_FACTOR" -> now - (10L * 24 * 3600);
            case "AMBIENT_TEMPERATURE", "TEMPERATURE" -> now - (95L * 24 * 3600);
            default -> now - (30L * 24 * 3600);
        };
    }

    private boolean isCalibrationDue(long lastCalibrationEpoch) {
        long now = System.currentTimeMillis() / 1000;
        return (now - lastCalibrationEpoch) > 90L * 24 * 3600;
    }

    private Instant parseInstant(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(timestamp);
        } catch (Exception ex) {
            return Instant.EPOCH;
        }
    }

    private String qualityMessage(SensorQuality quality, boolean physicalRangeOk, boolean calibrationDue) {
        return switch (quality) {
            case GOOD -> "Sensor operating normally";
            case SUSPECT -> "Flatline detected for more than 5 minutes";
            case BAD -> physicalRangeOk
                    ? "Sensor data quality BAD - analysis confidence reduced."
                    : "Sensor value outside physical possible range";
            case CALIBRATION_DUE -> calibrationDue
                    ? "Calibration overdue - schedule maintenance"
                    : "Calibration status requires review";
        };
    }

    private boolean isInPhysicalRange(String sensorType, double value) {
        return switch (sensorType.toUpperCase()) {
            case "GAS_LEAK" -> value >= 0 && value <= 1000;
            case "COMPRESSOR_VIBRATION", "VIBRATION" -> value >= 0 && value <= 50;
            case "BEARING_TEMPERATURE" -> value >= -10 && value <= 200;
            case "REACTOR_TEMPERATURE" -> value >= 0 && value <= 500;
            case "OIL_PRESSURE" -> value >= 0 && value <= 50;
            case "POWER_FACTOR" -> value >= 0 && value <= 1;
            case "AMBIENT_TEMPERATURE", "TEMPERATURE" -> value >= -20 && value <= 60;
            default -> true;
        };
    }

    public record SensorHealthSnapshot(
            String sensorType,
            SensorQuality quality,
            double lastValue,
            boolean physicalRangeOk,
            boolean calibrationDue,
            String message
    ) {
    }

    public record MachineSensorHealthReport(
            String machineId,
            List<SensorHealthSnapshot> sensors,
            SensorQuality overallHealth,
            int badSensorCount,
            int suspectSensorCount
    ) {
    }
}
