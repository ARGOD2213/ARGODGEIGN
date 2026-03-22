package com.mahindra.iot.service;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/**
 * Computes a combined zone risk score from three contributors:
 *   WBGT (heat stress)    — 0 to 33 points  (ISO 7933)
 *   NH3 concentration     — 0 to 33 points  (ACGIH TLV-TWA basis)
 *   Machine alert status  — 0 to 34 points  (active alerts in zone)
 * Total: 0–100.
 *
 * This is pure analysis — ADR-001 compliant.
 * No threshold evaluation. No control actions.
 */
@Service
public class ZoneRiskScoringService {

    /**
     * Score a single zone given its current conditions.
     */
    public ZoneRiskResult scoreZone(String zone,
                                   double wbgt,
                                   double nh3Ppm,
                                   int activeCritical,
                                   int activeWarning) {

        int wbgtPts;
        if      (wbgt > 35) wbgtPts = 33;
        else if (wbgt > 32) wbgtPts = 25;
        else if (wbgt > 30) wbgtPts = 18;
        else if (wbgt > 28) wbgtPts = 12;
        else if (wbgt > 26) wbgtPts =  6;
        else                wbgtPts =  0;

        int nh3Pts;
        if      (nh3Ppm > 25) nh3Pts = 33;
        else if (nh3Ppm > 15) nh3Pts = 20;
        else if (nh3Ppm > 10) nh3Pts = 12;
        else if (nh3Ppm >  5) nh3Pts =  5;
        else                  nh3Pts =  0;

        int machPts = Math.min(34, activeCritical * 17 + activeWarning * 5);

        int total = wbgtPts + nh3Pts + machPts;
        String level = total >= 70 ? "HIGH"
                     : total >= 40 ? "MEDIUM"
                     :               "LOW";

        String recommendation;
        if (total >= 70) recommendation =
            "Immediate supervisor review required. " +
            "Check WBGT, NH3 levels, and active machine alerts before allowing work.";
        else if (total >= 40) recommendation =
            "Elevated risk. Monitor closely and ensure PPE compliance.";
        else recommendation =
            "Normal operating conditions. Continue routine monitoring.";

        return new ZoneRiskResult(
            zone, total, level,
            wbgtPts, nh3Pts, machPts,
            wbgt, nh3Ppm,
            activeCritical, activeWarning,
            recommendation,
            Instant.now().toString()
        );
    }

    public PlantRiskSummary scorePlant(List<ZoneInput> zoneInputs) {
        List<ZoneRiskResult> results = zoneInputs.stream()
            .map(z -> scoreZone(
                z.zone(), z.wbgt(), z.nh3Ppm(),
                z.activeCritical(), z.activeWarning()))
            .toList();

        long highCount   = results.stream().filter(r -> "HIGH".equals(r.riskLevel())).count();
        long mediumCount = results.stream().filter(r -> "MEDIUM".equals(r.riskLevel())).count();

        String plantLevel = highCount > 0   ? "HIGH"
                          : mediumCount > 0 ? "MEDIUM"
                          :                   "LOW";

        int maxScore = results.stream()
            .mapToInt(ZoneRiskResult::riskScore)
            .max()
            .orElse(0);

        return new PlantRiskSummary(
            plantLevel,
            maxScore,
            highCount,
            mediumCount,
            results,
            Instant.now().toString()
        );
    }

    public record ZoneInput(
        String zone,
        double wbgt,
        double nh3Ppm,
        int activeCritical,
        int activeWarning
    ) {}

    public record ZoneRiskResult(
        String zone,
        int riskScore,
        String riskLevel,
        int wbgtContribution,
        int nh3Contribution,
        int machineContribution,
        double wbgt,
        double nh3Ppm,
        int activeCritical,
        int activeWarning,
        String recommendation,
        String calculatedAt
    ) {}

    public record PlantRiskSummary(
        String plantRiskLevel,
        int maxZoneScore,
        long highRiskZones,
        long mediumRiskZones,
        List<ZoneRiskResult> zones,
        String calculatedAt
    ) {}
}
