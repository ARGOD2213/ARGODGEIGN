package com.mahindra.iot.service;

import com.mahindra.iot.model.SensorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherCorrelationService {

    private final WeatherService weatherService;

    public void enrich(SensorEvent event) {
        try {
            double lat = event.getLatitude() != null ? event.getLatitude() : 17.385;
            double lon = event.getLongitude() != null ? event.getLongitude() : 78.487;

            WeatherService.WeatherData weather = weatherService.getWeather(lat, lon);

            event.setWeatherTempC(weather.getTempC());
            event.setWeatherHumidityPct(weather.getHumidity());
            event.setWeatherCondition(weather.getCondition());
            event.setWeatherWindSpeedMs(weather.getWindSpeed());

            String note = buildCorrelationNote(event, weather);
            event.setWeatherCorrelationNote(note);
            event.setWeatherAlertActive(isWeatherAlert(weather));

            log.debug("Weather enriched for {}: {}°C, {}", event.getDeviceId(), weather.getTempC(), weather.getCondition());
        } catch (Exception e) {
            log.warn("Weather enrichment failed for {}: {}", event.getDeviceId(), e.getMessage());
            event.setWeatherCorrelationNote("Weather data unavailable");
        }
    }

    private String buildCorrelationNote(SensorEvent event, WeatherService.WeatherData w) {
        String type = event.getSensorType();
        String condition = w.getCondition();
        StringBuilder note = new StringBuilder();

        if (w.getTempC() > 40 && "TEMPERATURE".equals(type)) {
            note.append(String.format("Heatwave (%.1f°C outdoor) likely causing elevated indoor temp. ", w.getTempC()));
        }
        if (w.getHumidity() > 80 && "HUMIDITY".equals(type)) {
            note.append(String.format("Monsoon conditions (%.0f%% outdoor humidity) affecting indoor levels. ", w.getHumidity()));
        }
        if ("Thunderstorm".equals(condition) && "VOLTAGE".equals(type)) {
            note.append("Thunderstorm detected — voltage fluctuations expected from grid instability. ");
        }
        if ("Thunderstorm".equals(condition) && "GAS_LEAK".equals(type)) {
            note.append("Storm pressure changes can affect gas line integrity — heightened monitoring advised. ");
        }
        if (w.getTempC() > 38 && "BEARING_TEMPERATURE".equals(type)) {
            note.append(String.format("Ambient heatwave (%.1f°C) contributing to bearing thermal stress. ", w.getTempC()));
        }
        if (w.getTempC() > 38 && "POWER_CONSUMPTION".equals(type)) {
            note.append("High ambient temperature causing increased HVAC load. ");
        }

        if (note.length() == 0) {
            note.append(String.format("Weather: %.1f°C, %.0f%% humidity, %s. No direct weather correlation detected.",
                    w.getTempC(), w.getHumidity(), condition));
        }
        return note.toString().trim();
    }

    private boolean isWeatherAlert(WeatherService.WeatherData w) {
        return w.getTempC() > 42
                || "Thunderstorm".equals(w.getCondition())
                || w.getHumidity() > 90;
    }
}
