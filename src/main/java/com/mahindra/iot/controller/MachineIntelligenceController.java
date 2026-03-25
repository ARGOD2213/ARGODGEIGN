package com.mahindra.iot.controller;

import com.mahindra.iot.service.LocalPredictiveIntelligenceService;
import com.mahindra.iot.service.LocalPredictiveIntelligenceService.MachineChatResponse;
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

    public MachineIntelligenceController(LocalPredictiveIntelligenceService intelligenceService) {
        this.intelligenceService = intelligenceService;
    }

    @GetMapping("/{machineId}/summary")
    public ResponseEntity<MachineIntelligenceReport> summary(@PathVariable String machineId) {
        return ResponseEntity.ok(intelligenceService.analyzeMachine(machineId));
    }

    @PostMapping("/chat")
    public ResponseEntity<MachineChatResponse> chat(@RequestBody Map<String, String> body) {
        String machineId = body.getOrDefault("machineId", "").trim();
        String question = body.getOrDefault("question", "").trim();
        return ResponseEntity.ok(intelligenceService.answerQuestion(machineId, question));
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
            "ARGUS Failure Mode Consensus"
        ));
        payload.put("sources", List.of(
            "UCI AI4I 2020 Predictive Maintenance Dataset",
            "NASA C-MAPSS Aircraft Engine Simulator Data"
        ));
        return ResponseEntity.ok(payload);
    }
}
