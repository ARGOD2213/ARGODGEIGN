package com.mahindra.iot.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Computes 8-hour Time-Weighted Average NH3 exposure per worker.
 * ACGIH TLV-TWA = 25 ppm. Factories Act 1948 Schedule 8.
 * RB-06 - Sprint 3.
 */
@Service
public class WorkerExposureService {

    /**
     * TWA = SUM(concentration_i * duration_i) / 8 hours
     * exposures = list of {ppm, durationMinutes} for current shift
     */
    public double computeTwa(List<ExposureEntry> exposures) {
        double weightedSum = exposures.stream()
                .mapToDouble(e -> e.ppm() * e.durationMinutes())
                .sum();
        return weightedSum / 480.0;
    }

    public String getTwaStatus(double twa) {
        if (twa > 25.0) return "RED";
        if (twa > 15.0) return "YELLOW";
        return "GREEN";
    }

    public record ExposureEntry(double ppm, double durationMinutes) {}
}
