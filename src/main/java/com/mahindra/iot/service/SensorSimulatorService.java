package com.mahindra.iot.service;

import com.mahindra.iot.dto.SensorIngestRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true", matchIfMissing = false)
public class SensorSimulatorService {

    private final SensorEventService sensorEventService;
    private final Random random = new Random();

    private static final List<Object[]> DEVICES = List.of(
        // {deviceId, sensorType, normalMin, normalMax, warnMin, critMin, lat, lon, location}
        new Object[]{"ENV-TEMP-ZONE-A-001",    "TEMPERATURE",         20.0, 32.0, 35.0, 50.0, 17.385, 78.487, "Zone-A/Production"},
        new Object[]{"ENV-HUMID-ZONE-A-001",   "HUMIDITY",            40.0, 65.0, 70.0, 90.0, 17.385, 78.487, "Zone-A/Production"},
        new Object[]{"ENV-CO2-ZONE-A-001",     "CO2",                400.0,800.0,1000.0,2000.0,17.385,78.487, "Zone-A/Air"},
        new Object[]{"ENV-AQI-ROOF-001",       "AIR_QUALITY",         20.0, 80.0,100.0, 200.0, 17.387, 78.489, "Rooftop"},
        new Object[]{"ENV-BARO-MAIN-001",      "BAROMETRIC_PRESSURE",1008.0,1018.0,1020.0,1040.0,17.385,78.487,"Main-Hall"},
        new Object[]{"MCH-VIBR-MOTOR-LINE1",   "VIBRATION",           0.5,  3.5,  4.5,  10.0, 17.384, 78.486, "Line-1/Motor"},
        new Object[]{"MCH-ACST-BEARING-001",   "ACOUSTIC_EMISSION",  45.0, 75.0, 85.0, 100.0, 17.384, 78.486, "Line-1/Bearing"},
        new Object[]{"MCH-CURR-MOTOR-LINE1",   "MOTOR_CURRENT",      45.0, 72.0, 80.0, 100.0, 17.384, 78.486, "Line-1/Motor"},
        new Object[]{"MCH-RPM-SPINDLE-001",    "RPM",               2800.0,3100.0,3200.0,3600.0,17.384,78.486, "CNC-7/Spindle"},
        new Object[]{"MCH-OILP-HYDRAULIC-001", "OIL_PRESSURE",       3.5,  5.5,  1.5,   0.5, 17.384, 78.486, "Hydraulic-Bay3"},
        new Object[]{"MCH-BRNG-TEMP-001",      "BEARING_TEMPERATURE",35.0, 60.0, 70.0,  90.0, 17.384, 78.486, "Line-1/Bearing"},
        new Object[]{"SAF-GAS-BOILER-ROOM",    "GAS_LEAK",            0.0, 50.0,500.0,1000.0, 17.383, 78.485, "Boiler-Room"},
        new Object[]{"SAF-SMOK-ZONE-A-001",    "SMOKE_DENSITY",       0.0,  5.0, 10.0,  25.0, 17.385, 78.487, "Zone-A/Safety"},
        new Object[]{"SAF-CHEM-STORAGE-001",   "CHEMICAL_CONCENTRATION",0.0,30.0,50.0,150.0, 17.383,78.485,  "Chemical-Wing-B"},
        new Object[]{"SAF-WLEAK-PIPE-001",     "WATER_LEAK",          0.0,  0.0,  1.0,   1.0, 17.385, 78.488, "Pipe-Junction-C"},
        new Object[]{"PWR-VOLT-MAIN-PANEL",    "VOLTAGE",           218.0,232.0,240.0, 260.0, 17.386, 78.489, "Main-Panel"},
        new Object[]{"PWR-CONS-ZONE-A-001",    "POWER_CONSUMPTION",  30.0, 70.0, 80.0,  95.0, 17.385, 78.487, "Zone-A/Meter"},
        new Object[]{"PWR-PFAC-LINE1-001",     "POWER_FACTOR",       0.88, 0.98, 0.85,  0.70, 17.384, 78.486, "Line-1/Power"},
        new Object[]{"PWR-BATT-UPS-001",       "BATTERY_LEVEL",      70.0,100.0, 30.0,  10.0, 17.386, 78.489, "UPS-Room"},
        new Object[]{"SEC-MOTN-GATE-001",      "MOTION",              0.0,  0.0,  1.0,   1.0, 17.387, 78.490, "Main-Gate"},
        new Object[]{"SEC-DOOR-SERVER-001",    "DOOR_SENSOR",         0.0,  0.0,  1.0,   1.0, 17.386, 78.489, "Server-Room"},
        new Object[]{"SEC-LOAD-CONVEYOR-001",  "LOAD_CELL",         400.0,800.0,900.0,1000.0, 17.384, 78.486, "Conveyor-Line2"},
        new Object[]{"MCH-VIBR-COMPRESSOR-003","VIBRATION",           0.8,  4.0,  4.5,  10.0, 17.383, 78.485, "Compressor-Room"},
        new Object[]{"MCH-VIBR-PUMP-002",      "VIBRATION",           0.3,  2.5,  4.5,  10.0, 17.384, 78.486, "Pump-Room"},
        new Object[]{"ENV-TEMP-ZONE-B-002",    "TEMPERATURE",        18.0, 30.0, 35.0,  50.0, 17.386, 78.488, "Zone-B/Assembly"}
    );

    @Scheduled(fixedDelay = 30000)
    public void simulate() {
        log.info("Simulator: firing {} sensor readings...", DEVICES.size());
        for (Object[] d : DEVICES) {
            try {
                SensorIngestRequest req = buildRequest(d);
                sensorEventService.ingestEvent(req);
            } catch (Exception e) {
                log.warn("Simulator error for {}: {}", d[0], e.getMessage());
            }
        }
    }

    private SensorIngestRequest buildRequest(Object[] d) {
        String sensorType = (String) d[1];
        double nMin = (double) d[2];
        double nMax = (double) d[3];

        double value = generateValue(sensorType, nMin, nMax, d);

        return SensorIngestRequest.builder()
                .deviceId((String) d[0])
                .sensorType(sensorType)
                .value(Math.round(value * 100.0) / 100.0)
                .location((String) d[8])
                .facilityId("FACTORY-HYD-001")
                .latitude((double) d[6])
                .longitude((double) d[7])
                .skipWeatherCorrelation(false)
                .skipAiAnalysis(false)
                .build();
    }

    private double generateValue(String type, double nMin, double nMax, Object[] d) {
        // Binary sensors
        if ("MOTION".equals(type) || "DOOR_SENSOR".equals(type) || "WATER_LEAK".equals(type)) {
            return random.nextDouble() < 0.05 ? 1.0 : 0.0;
        }
        // Low-is-bad sensors — stay in normal range mostly
        if ("OIL_PRESSURE".equals(type) || "POWER_FACTOR".equals(type) || "BATTERY_LEVEL".equals(type)) {
            double rand = random.nextDouble();
            if (rand < 0.03) return nMin * 0.5; // critical
            if (rand < 0.08) return nMin * 0.8; // warning
            return nMin + random.nextDouble() * (nMax - nMin);
        }
        // Standard sensors — 80% normal, 15% warn, 5% critical
        double rand = random.nextDouble();
        double warnThreshold = (double) d[4];
        double critThreshold = (double) d[5];
        if (rand < 0.05) return critThreshold + random.nextDouble() * (critThreshold * 0.2);
        if (rand < 0.20) return warnThreshold + random.nextDouble() * (critThreshold - warnThreshold);
        return nMin + random.nextDouble() * (nMax - nMin);
    }
}
