package com.mahindra.iot.service;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherService {

    private final RestTemplate restTemplate;

    @Value("${weather.api.key:}")
    private String apiKey;

    @Value("${weather.api.url:https://api.openweathermap.org/data/2.5/weather}")
    private String apiUrl;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WeatherData {
        private double tempC;
        private double humidity;
        private String condition;
        private double windSpeed;
        private double windDeg;
        private double wbgt;
        private boolean available;
    }

    @Cacheable(value = "weather", key = "#lat + ',' + #lon")
    public WeatherData getWeather(double lat, double lon) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Weather API key not configured — using defaults");
            return defaultWeather();
        }
        try {
            String url = String.format("%s?lat=%s&lon=%s&appid=%s&units=metric",
                    apiUrl, lat, lon, apiKey);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return defaultWeather();

            @SuppressWarnings("unchecked")
            Map<String, Object> main = (Map<String, Object>) response.get("main");
            @SuppressWarnings("unchecked")
            var weatherList = (java.util.List<?>) response.get("weather");
            @SuppressWarnings("unchecked")
            Map<String, Object> wind = (Map<String, Object>) response.get("wind");

            String condition = "Clear";
            if (weatherList != null && !weatherList.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> w = (Map<String, Object>) weatherList.get(0);
                condition = (String) w.getOrDefault("main", "Clear");
            }

            double tempC = ((Number) main.getOrDefault("temp", 28.0)).doubleValue();
            double humidityPct = ((Number) main.getOrDefault("humidity", 50.0)).doubleValue();

            return WeatherData.builder()
                    .tempC(tempC)
                    .humidity(humidityPct)
                    .condition(condition)
                    .windSpeed(wind != null ? ((Number) wind.getOrDefault("speed", 3.0)).doubleValue() : 3.0)
                    .windDeg(wind != null ? ((Number) wind.getOrDefault("deg", 0.0)).doubleValue() : 0.0)
                    .wbgt(computeWbgt(tempC, humidityPct))
                    .available(true)
                    .build();
        } catch (Exception e) {
            log.warn("Weather API call failed: {} — using defaults", e.getMessage());
            return defaultWeather();
        }
    }

    private WeatherData defaultWeather() {
        return WeatherData.builder()
                .tempC(28.0).humidity(50.0).condition("Clear").windSpeed(3.0).windDeg(245.0)
                .wbgt(computeWbgt(28.0, 50.0)).available(false)
                .build();
    }

    /**
     * Outdoor WBGT approximation from OpenWeatherMap fields.
     * ISO 7933:2004 - Ergonomics, heat stress.
     * RB-07 - Sprint 3.
     *
     * Tw = wet bulb temp (approx from temp + humidity)
     * Tg = globe temp (approx as temp + 2 for outdoor sunny)
     * Td = dry bulb temp
     * WBGT_outdoor = 0.7*Tw + 0.2*Tg + 0.1*Td
     */
    public double computeWbgt(double tempC, double humidityPct) {
        double td = tempC;
        double tw = tempC * Math.atan(0.151977 * Math.sqrt(humidityPct + 8.313659))
                + Math.atan(tempC + humidityPct)
                - Math.atan(humidityPct - 1.676331)
                + 0.00391838 * Math.pow(humidityPct, 1.5) * Math.atan(0.023101 * humidityPct)
                - 4.686035;
        double tg = tempC + 2.0;
        return Math.round((0.7 * tw + 0.2 * tg + 0.1 * td) * 10.0) / 10.0;
    }

    public String getWbgtWorkRestBand(double wbgt) {
        if (wbgt > 35) return "STOP_OUTDOOR_WORK";
        if (wbgt > 32) return "25_75";
        if (wbgt > 30) return "50_50";
        if (wbgt > 28) return "75_25";
        if (wbgt > 26) return "CAUTION";
        return "NORMAL";
    }

    public String getWbgtStatus(double wbgt) {
        if (wbgt > 32) return "CRITICAL";
        if (wbgt > 28) return "WARNING";
        if (wbgt > 26) return "CAUTION";
        return "NORMAL";
    }

    /**
     * Returns the cardinal evacuation direction downwind of NH3 release.
     * Wind blows FROM windDeg, so NH3 disperses TO opposite direction.
     * IEC 62443 + ALOHA dispersion model requirement - RB-02.
     */
    public String getNh3DispersionDirection(double windDeg) {
        double dispersionDeg = (windDeg + 180.0) % 360.0;
        if (dispersionDeg < 22.5 || dispersionDeg >= 337.5) return "NORTH";
        if (dispersionDeg < 67.5) return "NORTHEAST";
        if (dispersionDeg < 112.5) return "EAST";
        if (dispersionDeg < 157.5) return "SOUTHEAST";
        if (dispersionDeg < 202.5) return "SOUTH";
        if (dispersionDeg < 247.5) return "SOUTHWEST";
        if (dispersionDeg < 292.5) return "WEST";
        return "NORTHWEST";
    }
}
