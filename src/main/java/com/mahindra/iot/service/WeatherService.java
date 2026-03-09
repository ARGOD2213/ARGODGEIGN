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

            return WeatherData.builder()
                    .tempC(((Number) main.getOrDefault("temp", 28.0)).doubleValue())
                    .humidity(((Number) main.getOrDefault("humidity", 50.0)).doubleValue())
                    .condition(condition)
                    .windSpeed(wind != null ? ((Number) wind.getOrDefault("speed", 3.0)).doubleValue() : 3.0)
                    .available(true)
                    .build();
        } catch (Exception e) {
            log.warn("Weather API call failed: {} — using defaults", e.getMessage());
            return defaultWeather();
        }
    }

    private WeatherData defaultWeather() {
        return WeatherData.builder()
                .tempC(28.0).humidity(50.0).condition("Clear").windSpeed(3.0).available(false)
                .build();
    }
}
