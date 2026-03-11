package com.mahindra.iot.util;

import com.mahindra.iot.model.SensorEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AiAdvisoryWrapper {

    private static final String LABEL = "AI ADVISORY";
    private static final String NOTICE = "Not a control action";
    private static final String AUTHORITY = "Rule engine has final authority";
    private static final String BANNER = "[AI ADVISORY | Not a control action | Rule engine has final authority]";

    private AiAdvisoryWrapper() {
    }

    public static Map<String, Object> fromEvent(SensorEvent event) {
        Map<String, Object> advisory = new LinkedHashMap<>();
        advisory.put("banner", BANNER);
        advisory.put("label", LABEL);
        advisory.put("notice", NOTICE);
        advisory.put("authority", AUTHORITY);
        advisory.put("riskScore", event.getAiRiskScore());
        advisory.put("summary", event.getAiIncidentSummary());
        advisory.put("recommendedAction", event.getAiRecommendedAction());
        advisory.put("consensus", event.getLlmConsensus());
        return advisory;
    }
}
