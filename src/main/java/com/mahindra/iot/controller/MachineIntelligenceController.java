package com.mahindra.iot.controller;

import com.mahindra.iot.service.AdvancedMachineIntelligenceService;
import com.mahindra.iot.service.AdvancedMachineIntelligenceService.EnhancedMachineChatResponse;
import com.mahindra.iot.service.AdvancedMachineIntelligenceService.FleetWatchlistEntry;
import com.mahindra.iot.service.AdvancedMachineIntelligenceService.MachineDeepDive;
import com.mahindra.iot.service.LocalPredictiveIntelligenceService;
import com.mahindra.iot.service.LocalPredictiveIntelligenceService.MachineIntelligenceReport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/intelligence")
public class MachineIntelligenceController {

    private final LocalPredictiveIntelligenceService intelligenceService;
    private final AdvancedMachineIntelligenceService advancedIntelligenceService;

    public MachineIntelligenceController(LocalPredictiveIntelligenceService intelligenceService,
                                        AdvancedMachineIntelligenceService advancedIntelligenceService) {
        this.intelligenceService = intelligenceService;
        this.advancedIntelligenceService = advancedIntelligenceService;
    }

    @GetMapping("/{machineId}/summary")
    public ResponseEntity<MachineIntelligenceReport> summary(@PathVariable String machineId) {
        return ResponseEntity.ok(intelligenceService.analyzeMachine(machineId));
    }

    @GetMapping("/{machineId}/deep-dive")
    public ResponseEntity<MachineDeepDive> deepDive(@PathVariable String machineId) {
        return ResponseEntity.ok(advancedIntelligenceService.deepDive(machineId));
    }

    @PostMapping("/chat")
    public ResponseEntity<EnhancedMachineChatResponse> chat(@RequestBody Map<String, String> body) {
        String machineId = body.getOrDefault("machineId", "").trim();
        String question = body.getOrDefault("question", "").trim();
        return ResponseEntity.ok(advancedIntelligenceService.answerQuestion(machineId, question));
    }

    @GetMapping("/fleet-watchlist")
    public ResponseEntity<List<FleetWatchlistEntry>> fleetWatchlist() {
        return ResponseEntity.ok(advancedIntelligenceService.fleetWatchlist());
    }

    @GetMapping("/sources")
    public ResponseEntity<Map<String, Object>> sources() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", "LOCAL_ML_PUBLIC_SAMPLE_BLEND");
        payload.put("runtimeCostNote", "No paid inference API calls. In-process scoring only.");
        payload.put("models", List.of(
            "ARGUS Atlas Similarity",
            "ARGUS Trajectory Breach",
            "ARGUS Operating Envelope",
            "ARGUS Maintenance Debt",
            "ARGUS Failure Mode Consensus",
            "ARGUS Sensor Drift Sentinel",
            "ARGUS Cascade Stress Graph",
            "ARGUS Recovery Stability",
            "ARGUS Alert Precision Engine"
        ));
        payload.put("sources", List.of(
            "UCI AI4I 2020 Predictive Maintenance Dataset",
            "NASA C-MAPSS Aircraft Engine Simulator Data"
        ));
        return ResponseEntity.ok(payload);
    }
}
