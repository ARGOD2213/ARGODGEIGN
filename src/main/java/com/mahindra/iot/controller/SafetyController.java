package com.mahindra.iot.controller;

import com.mahindra.iot.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/safety")
@Tag(name = "Safety Dashboard API", description = "Synthetic safety endpoints for Sprint 2 safety dashboard")
@RequiredArgsConstructor
public class SafetyController {

    private static final String EVACUATION_ZONE = "Muster Point B (leeward side)";

    private final WeatherService weatherService;

    @GetMapping("/nh3-zones")
    @Operation(summary = "NH3 concentration by zone")
    public ResponseEntity<List<Map<String, Object>>> getNh3Zones() {
        WeatherService.WeatherData weather = weatherService.getWeather(17.3850, 78.4867);
        double windDeg = weather.getWindDeg();
        String dispersionDirection = weatherService.getNh3DispersionDirection(windDeg);

        List<Map<String, Object>> zones = List.of(
                nh3Zone("SYNTHESIS", 8.2, 7.1, windDeg, dispersionDirection),
                nh3Zone("COMPRESSOR", 18.5, 14.2, windDeg, dispersionDirection),
                nh3Zone("STORAGE", 4.1, 3.8, windDeg, dispersionDirection),
                nh3Zone("UREA_HP", 22.3, 19.1, windDeg, dispersionDirection),
                nh3Zone("UTILITY", 2.0, 1.9, windDeg, dispersionDirection)
        );
        return ResponseEntity.ok(zones);
    }

    @GetMapping("/fatigue")
    @Operation(summary = "Worker fatigue cards")
    public ResponseEntity<List<Map<String, Object>>> getFatigue() {
        int hour = LocalTime.now().getHour();
        List<Map<String, Object>> workers = List.of(
                worker("Field Op-A1", 9, 5, hour),
                worker("Field Op-A2", 7, 3, hour),
                worker("Maint-B1", 11, 6, hour),
                worker("Maint-B2", 6, 2, hour),
                worker("Ctrl Room-C", 8, 4, 22)
        );
        return ResponseEntity.ok(workers);
    }

    @GetMapping("/ptw")
    @Operation(summary = "Permit-to-work table")
    public ResponseEntity<List<Map<String, Object>>> getPtw() {
        List<Map<String, Object>> ptw = List.of(
                Map.of("zone", "SYNTHESIS", "type", "Hot Work", "status", "ACTIVE", "expires", "18:00", "issuedTo", "Ravi K."),
                Map.of("zone", "COMPRESSOR", "type", "Confined Space", "status", "ISSUED", "expires", "16:00", "issuedTo", "Suresh M."),
                Map.of("zone", "STORAGE", "type", "Height Work", "status", "CLOSED", "expires", "-", "issuedTo", "Anil P."),
                Map.of("zone", "UREA_HP", "type", "Maintenance", "status", "NONE", "expires", "-", "issuedTo", "-"),
                Map.of("zone", "UTILITY", "type", "Electrical", "status", "SUSPENDED", "expires", "20:00", "issuedTo", "Kiran R.")
        );
        return ResponseEntity.ok(ptw);
    }

    private Map<String, Object> worker(String name, int hoursToday, int days, int shiftHour) {
        int score = computeFaid(hoursToday, days, shiftHour);
        return Map.of(
                "name", name,
                "hoursToday", hoursToday,
                "days", days,
                "shiftHour", shiftHour,
                "score", score
        );
    }

    private Map<String, Object> nh3Zone(String zone,
                                        double ppm,
                                        double twa8h,
                                        double windDeg,
                                        String dispersionDirection) {
        return Map.of(
                "zone", zone,
                "ppm", ppm,
                "twa8h", twa8h,
                "windDeg", windDeg,
                "nh3DispersionDirection", dispersionDirection,
                "evacuationZone", EVACUATION_ZONE
        );
    }

    private int computeFaid(int hoursWorkedToday, int consecutiveDays, int shiftHour) {
        int score = 0;
        score += Math.min(hoursWorkedToday * 5, 50);
        score += Math.min(consecutiveDays * 8, 30);
        if (shiftHour >= 22 || shiftHour <= 6) {
            score += 20;
        }
        return Math.min(score, 100);
    }
}
