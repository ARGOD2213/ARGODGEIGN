package com.mahindra.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahindra.iot.model.SensorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MultiLlmAnalysisService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.gemini.api.key:}")
    private String geminiKey;

    @Value("${ai.gemini.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent}")
    private String geminiUrl;

    @Value("${ai.analysis.enabled:true}")
    private boolean analysisEnabled;

    public void analyze(SensorEvent event) {
        if (!analysisEnabled || !"WARNING".equals(event.getStatus()) && !"CRITICAL".equals(event.getStatus())) {
            applyRuleBasedAnalysis(event);
            return;
        }
        try {
            if (geminiKey != null && !geminiKey.isBlank()) {
                callGemini(event);
                event.setLlmConsensus("GEMINI_ANALYSIS");
                log.info("Gemini analysis complete for {} — riskScore={}", event.getDeviceId(), event.getAiRiskScore());
            } else {
                log.debug("Gemini key not configured — using rule-based analysis");
                applyRuleBasedAnalysis(event);
            }
        } catch (Exception e) {
            log.warn("LLM analysis failed for {}: {} — falling back to rule-based", event.getDeviceId(), e.getMessage());
            applyRuleBasedAnalysis(event);
        }
    }

    private void callGemini(SensorEvent event) {
        String prompt = buildPrompt(event);
        String url = geminiUrl + "?key=" + geminiKey;

        Map<String, Object> body = Map.of(
            "contents", new Object[]{
                Map.of("parts", new Object[]{Map.of("text", prompt)})
            },
            "generationConfig", Map.of(
                "temperature", 0.3,
                "maxOutputTokens", 500
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        parseGeminiResponse(event, response.getBody());
    }

    private void parseGeminiResponse(SensorEvent event, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            // Try to parse structured JSON from Gemini response
            int jsonStart = text.indexOf('{');
            int jsonEnd   = text.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                JsonNode json = objectMapper.readTree(text.substring(jsonStart, jsonEnd + 1));
                event.setAiRiskScore(json.path("riskScore").asInt(50));
                event.setAiRiskLevel(json.path("riskLevel").asText("MEDIUM"));
                event.setAiIncidentSummary(json.path("incidentSummary").asText("AI analysis completed"));
                event.setAiRecommendedAction(json.path("recommendedAction").asText("Monitor closely"));
                event.setAiPredictedFailureEta(json.path("predictedFailureEta").asText("Unknown"));
                event.setGeminiRiskScore((double) event.getAiRiskScore());
            } else {
                // Gemini gave free text, parse it manually
                event.setAiIncidentSummary(text.length() > 300 ? text.substring(0, 300) : text);
                event.setAiRiskScore("CRITICAL".equals(event.getStatus()) ? 85 : 60);
                event.setAiRiskLevel("CRITICAL".equals(event.getStatus()) ? "HIGH" : "MEDIUM");
                event.setAiRecommendedAction("Review sensor readings and inspect equipment");
                event.setGeminiRiskScore((double) event.getAiRiskScore());
            }
        } catch (Exception e) {
            log.warn("Gemini response parse failed: {}", e.getMessage());
            applyRuleBasedAnalysis(event);
        }
    }

    private String buildPrompt(SensorEvent event) {
        return String.format("""
            You are an industrial IoT expert. Analyze this sensor alert and respond ONLY in JSON format.
            
            Sensor: %s | Device: %s | Value: %.2f %s | Status: %s
            Location: %s | Category: %s
            Weather: %.1f°C, %s
            Weather Note: %s
            
            Respond with ONLY this JSON (no markdown):
            {
              "riskScore": <0-100 integer>,
              "riskLevel": "<LOW|MEDIUM|HIGH|CRITICAL>",
              "incidentSummary": "<2 sentence summary>",
              "recommendedAction": "<specific action to take>",
              "predictedFailureEta": "<time estimate or N/A>"
            }
            """,
                event.getSensorType(), event.getDeviceId(),
                event.getValue(), event.getUnit() != null ? event.getUnit() : "",
                event.getStatus(),
                event.getLocation() != null ? event.getLocation() : "Unknown",
                event.getSensorCategory() != null ? event.getSensorCategory() : "Unknown",
                event.getWeatherTempC() != null ? event.getWeatherTempC() : 28.0,
                event.getWeatherCondition() != null ? event.getWeatherCondition() : "Clear",
                event.getWeatherCorrelationNote() != null ? event.getWeatherCorrelationNote() : "None"
        );
    }

    private void applyRuleBasedAnalysis(SensorEvent event) {
        int score = "CRITICAL".equals(event.getStatus()) ? 85 : "WARNING".equals(event.getStatus()) ? 55 : 20;
        String level = "CRITICAL".equals(event.getStatus()) ? "HIGH" : "WARNING".equals(event.getStatus()) ? "MEDIUM" : "LOW";

        event.setAiRiskScore(score);
        event.setAiRiskLevel(level);
        event.setGeminiRiskScore((double) score);
        event.setLlmConsensus("RULE_BASED");

        String type = event.getSensorType();
        event.setAiIncidentSummary(String.format(
                "%s sensor on device %s is %s with value %.2f. Threshold exceeded — immediate attention required.",
                type, event.getDeviceId(), event.getStatus(), event.getValue()));

        event.setAiRecommendedAction(switch (type) {
            case "TEMPERATURE", "BEARING_TEMPERATURE" -> "Check cooling system and reduce load. Inspect bearings for lubrication.";
            case "VIBRATION" -> "Inspect motor/pump for mechanical imbalance or bearing wear.";
            case "GAS_LEAK" -> "EVACUATE area immediately. Shut off gas supply. Contact safety team.";
            case "SMOKE_DENSITY" -> "Activate fire suppression system. Check for ignition sources.";
            case "WATER_LEAK" -> "Shut off water supply to affected line. Inspect pipe junction.";
            case "MOTOR_CURRENT" -> "Check for mechanical overload or electrical fault. Reduce load.";
            case "OIL_PRESSURE" -> "Check oil pump and supply lines. Top up oil level immediately.";
            case "VOLTAGE" -> "Check power supply unit. Inspect for grid fluctuations.";
            case "BATTERY_LEVEL" -> "Replace or recharge UPS battery. Test backup power immediately.";
            default -> "Investigate sensor reading. Inspect equipment and notify maintenance team.";
        });

        event.setAiPredictedFailureEta("CRITICAL".equals(event.getStatus()) ? "Within 2-4 hours" : "Within 24 hours if unaddressed");
    }
}
