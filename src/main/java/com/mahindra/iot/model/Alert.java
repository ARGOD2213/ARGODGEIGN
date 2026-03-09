package com.mahindra.iot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String alertId;
    private String deviceId;
    private String sensorType;
    private Double value;
    private Double threshold;
    private String severity;
    private String message;
    private String timestamp;
    private String snsMessageId;
    private String sqsMessageId;
    private String weatherNote;
    private String aiSummary;
    private Integer aiRiskScore;
}
