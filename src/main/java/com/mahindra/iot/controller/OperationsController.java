package com.mahindra.iot.controller;

import com.mahindra.iot.service.MachineOpsService;
import com.mahindra.iot.service.PlatformStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Sprint 1 Operations API", description = "Machine, platform, trend, and KPI endpoints for Sprint 1 foundation")
public class OperationsController {

    private final MachineOpsService machineOpsService;
    private final PlatformStatusService platformStatusService;

    @GetMapping("/platform/status")
    @Operation(summary = "Platform status")
    public ResponseEntity<Map<String, Object>> getPlatformStatus() {
        return ResponseEntity.ok(platformStatusService.getPlatformStatus());
    }

    @GetMapping("/admin/dlq-status")
    @Operation(summary = "DLQ queue health")
    public ResponseEntity<Map<String, Object>> getDlqStatus() {
        return ResponseEntity.ok(platformStatusService.getDlqStatus());
    }

    @GetMapping("/machines")
    @Operation(summary = "List machines with current status")
    public ResponseEntity<List<Map<String, Object>>> getMachines() {
        return ResponseEntity.ok(machineOpsService.getMachines());
    }

    @GetMapping("/machines/{id}/alerts")
    @Operation(summary = "Last alerts for a machine")
    public ResponseEntity<List<Map<String, Object>>> getMachineAlerts(@PathVariable("id") String machineId,
                                                                      @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(machineOpsService.getMachineAlerts(machineId, limit));
    }

    @GetMapping("/machines/{id}/trend")
    @Operation(summary = "Machine trend over recent hours")
    public ResponseEntity<List<Map<String, Object>>> getMachineTrend(@PathVariable("id") String machineId,
                                                                     @RequestParam(defaultValue = "24") int hours,
                                                                     @RequestParam(required = false) String machineClass) {
        return ResponseEntity.ok(machineOpsService.getMachineTrend(machineId, hours, machineClass));
    }

    @GetMapping("/kpi/oee/{machineId}")
    @Operation(summary = "Machine OEE KPI")
    public ResponseEntity<Map<String, Object>> getMachineOee(@PathVariable String machineId,
                                                             @RequestParam(defaultValue = "24") int hours,
                                                             @RequestParam(required = false) String machineClass) {
        return ResponseEntity.ok(machineOpsService.getMachineOee(machineId, hours, machineClass));
    }
}
