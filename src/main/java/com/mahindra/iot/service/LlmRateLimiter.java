package com.mahindra.iot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LlmRateLimiter {

    private final Map<String, Long> lastCallTimeByMachine = new ConcurrentHashMap<>();
    private final long windowMillis;

    public LlmRateLimiter(@Value("${ai.gemini.rate.limit.minutes:5}") long windowMinutes) {
        this.windowMillis = Math.max(1, windowMinutes) * 60_000L;
    }

    public boolean shouldCallLlm(String machineId) {
        if (machineId == null || machineId.isBlank()) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long previous = lastCallTimeByMachine.putIfAbsent(machineId, now);
        if (previous == null) {
            return true;
        }

        if ((now - previous) >= windowMillis) {
            lastCallTimeByMachine.put(machineId, now);
            return true;
        }

        return false;
    }
}
