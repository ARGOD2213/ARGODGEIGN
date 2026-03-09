package com.mahindra.iot.config;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class ThresholdConfig {

    // WARNING thresholds
    private static final Map<String, Double> WARN = Map.ofEntries(
        Map.entry("TEMPERATURE",            35.0),
        Map.entry("HUMIDITY",               70.0),
        Map.entry("CO2",                    1000.0),
        Map.entry("AIR_QUALITY",            100.0),
        Map.entry("BAROMETRIC_PRESSURE",    1020.0),
        Map.entry("VIBRATION",              4.5),
        Map.entry("ACOUSTIC_EMISSION",      85.0),
        Map.entry("MOTOR_CURRENT",          80.0),
        Map.entry("RPM",                    3200.0),
        Map.entry("BEARING_TEMPERATURE",    70.0),
        Map.entry("GAS_LEAK",               500.0),
        Map.entry("SMOKE_DENSITY",          10.0),
        Map.entry("CHEMICAL_CONCENTRATION", 50.0),
        Map.entry("VOLTAGE",                240.0),
        Map.entry("POWER_CONSUMPTION",      80.0),
        Map.entry("LOAD_CELL",              900.0)
    );

    // CRITICAL thresholds
    private static final Map<String, Double> CRIT = Map.ofEntries(
        Map.entry("TEMPERATURE",            50.0),
        Map.entry("HUMIDITY",               90.0),
        Map.entry("CO2",                    2000.0),
        Map.entry("AIR_QUALITY",            200.0),
        Map.entry("BAROMETRIC_PRESSURE",    1040.0),
        Map.entry("VIBRATION",              10.0),
        Map.entry("ACOUSTIC_EMISSION",      100.0),
        Map.entry("MOTOR_CURRENT",          100.0),
        Map.entry("RPM",                    3600.0),
        Map.entry("BEARING_TEMPERATURE",    90.0),
        Map.entry("GAS_LEAK",               1000.0),
        Map.entry("SMOKE_DENSITY",          25.0),
        Map.entry("CHEMICAL_CONCENTRATION", 150.0),
        Map.entry("VOLTAGE",                260.0),
        Map.entry("POWER_CONSUMPTION",      95.0),
        Map.entry("LOAD_CELL",              1000.0)
    );

    // Sensors where LOW value = bad (opposite direction)
    private static final Map<String, double[]> LOW_BAD = Map.of(
        "OIL_PRESSURE",  new double[]{1.5, 0.5},   // warn=1.5bar, critical=0.5bar
        "POWER_FACTOR",  new double[]{0.85, 0.70},
        "BATTERY_LEVEL", new double[]{30.0, 10.0}
    );

    public String evaluate(String sensorType, double value) {
        String type = sensorType.toUpperCase();

        // Binary sensors
        if ("MOTION".equals(type) || "DOOR_SENSOR".equals(type) || "WATER_LEAK".equals(type)) {
            return value >= 1.0 ? "CRITICAL" : "NORMAL";
        }

        // Low-is-bad sensors
        if (LOW_BAD.containsKey(type)) {
            double[] thresholds = LOW_BAD.get(type);
            if (value <= thresholds[1]) return "CRITICAL";
            if (value <= thresholds[0]) return "WARNING";
            return "NORMAL";
        }

        // Standard sensors
        double warn = WARN.getOrDefault(type, 9999.0);
        double crit = CRIT.getOrDefault(type, 99999.0);
        if (value >= crit) return "CRITICAL";
        if (value >= warn) return "WARNING";
        return "NORMAL";
    }

    public double getWarningThreshold(String sensorType) {
        String type = sensorType.toUpperCase();
        if (LOW_BAD.containsKey(type)) return LOW_BAD.get(type)[0];
        return WARN.getOrDefault(type, 0.0);
    }

    public double getCriticalThreshold(String sensorType) {
        String type = sensorType.toUpperCase();
        if (LOW_BAD.containsKey(type)) return LOW_BAD.get(type)[1];
        return CRIT.getOrDefault(type, 0.0);
    }
}
