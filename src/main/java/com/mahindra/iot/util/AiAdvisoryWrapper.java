package com.mahindra.iot.util;

import com.mahindra.iot.model.SensorEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AiAdvisoryWrapper {

    private static final String LABEL = "AI ADVISORY";
    private static final String NOTICE = "Not a control action";
    private static final String AUTHORITY = "Rule engine has final authority";
    private static final String BANNER = "[AI ADVISORY | Not a control action | Rule engine has final authority]";
    private static final String VALID_FOR = "30 minutes";

    private AiAdvisoryWrapper() {
    }

    public static Map<String, Object> fromEvent(SensorEvent event) {
        Map<String, Object> advisory = new LinkedHashMap<>();
        advisory.put("banner", BANNER);
        advisory.put("label", LABEL);
        advisory.put("notice", NOTICE);
        advisory.put("authority", AUTHORITY);
        advisory.put("confidence", event.getAiConfidence() != null ? event.getAiConfidence() : "LOW");
        advisory.put("riskScore", event.getAiRiskScore());
        advisory.put("analysis", event.getAiIncidentSummary());
        advisory.put("summary", event.getAiIncidentSummary());
        advisory.put("recommendedAction", event.getAiRecommendedAction());
        advisory.put("dataSource", event.getAnalysisSource());
        advisory.put("validFor", VALID_FOR);
        advisory.put("calibrated", false);
        advisory.put("consensus", event.getAnalysisSource());
        return advisory;
    }
}
