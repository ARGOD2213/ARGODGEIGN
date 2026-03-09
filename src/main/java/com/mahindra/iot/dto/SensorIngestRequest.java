package com.mahindra.iot.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorIngestRequest {

    @NotBlank(message = "deviceId is required")
    private String deviceId;

    @NotBlank(message = "sensorType is required")
    private String sensorType;

    @NotNull(message = "value is required")
    private Double value;

    private String unit;
    private String location;
    private String facilityId;
    private Double latitude;
    private Double longitude;

    // Rolling stats from edge device
    private Double minValue;
    private Double maxValue;
    private Double avgValue;
    private Double deltaFromPrevious;

    // Control flags
    private Boolean skipAiAnalysis;
    private Boolean skipWeatherCorrelation;
}
