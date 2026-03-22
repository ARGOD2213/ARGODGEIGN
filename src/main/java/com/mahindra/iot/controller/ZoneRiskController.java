package com.mahindra.iot.controller;

import com.mahindra.iot.service.ZoneRiskScoringService;
import com.mahindra.iot.service.ZoneRiskScoringService.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/zones")
public class ZoneRiskController {

    private final ZoneRiskScoringService riskService;

    public ZoneRiskController(ZoneRiskScoringService riskService) {
        this.riskService = riskService;
    }

    @GetMapping("/risk")
    public ResponseEntity<PlantRiskSummary> getZoneRisk() {
        List<ZoneInput> zones = List.of(
            new ZoneInput("SYNTHESIS",    29.0, 18.0, 1, 2),
            new ZoneInput("REFORMER",     27.5,  5.0, 0, 1),
            new ZoneInput("UREA_SECTION", 28.0,  3.0, 0, 0),
            new ZoneInput("UTILITIES",    26.5,  1.0, 0, 1),
            new ZoneInput("STORAGE",      27.0, 22.0, 0, 1)
        );
        return ResponseEntity.ok(riskService.scorePlant(zones));
    }

    @GetMapping("/risk/{zone}")
    public ResponseEntity<ZoneRiskResult> getZoneRiskSingle(
            @PathVariable String zone,
            @RequestParam(defaultValue = "27.0") double wbgt,
            @RequestParam(defaultValue = "0.0")  double nh3Ppm,
            @RequestParam(defaultValue = "0")    int    activeCritical,
            @RequestParam(defaultValue = "0")    int    activeWarning) {
        return ResponseEntity.ok(
            riskService.scoreZone(zone, wbgt, nh3Ppm,
                                  activeCritical, activeWarning));
    }
}
