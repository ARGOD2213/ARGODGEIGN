package com.mahindra.iot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.io.Serial;
import java.io.Serializable;

@DynamoDbBean
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String deviceId;
    private String timestamp;
    private String sensorType;
    private String sensorCategory;
    private String unit;
    private Double value;
    private String status;
    private String location;
    private String facilityId;
    private Double latitude;
    private Double longitude;
    private String processedAt;
    private String alertId;

    private Double warningThreshold;
    private Double criticalThreshold;

    private Double minValue;
    private Double maxValue;
    private Double avgValue;
    private Double deltaFromPrevious;

    private Double weatherTempC;
    private Double weatherHumidityPct;
    private String weatherCondition;
    private Double weatherWindSpeedMs;
    private String weatherCorrelationNote;
    private Boolean weatherAlertActive;

    private Integer aiRiskScore;
    private String aiRiskLevel;
    private String aiConfidence;
    private String aiIncidentSummary;
    private String aiRecommendedAction;
    private String aiPredictedFailureEta;
    private Double modelRiskScore;
    private String analysisSource;
    private Integer alertPrecisionScore;
    private String alertActionability;
    private String alertAccuracyNote;
    private String primaryFailureMode;
    private String aiReviewStatus;
    private String aiReviewedBy;
    private String aiReviewNote;
    private String aiReviewedAt;
    private String aiEvidenceId;
    private String aiEvidenceCapturedAt;
    private String aiInputTags;
    private String aiRuleVersion;
    private String aiModelVersion;
    private Double geminiRiskScore;
    private Double claudeRiskScore;
    private Double gpt4RiskScore;
    private String llmConsensus;

    private String snsMessageId;
    private String sqsMessageId;

    @DynamoDbPartitionKey
    public String getDeviceId() {
        return deviceId;
    }

    @DynamoDbSortKey
    public String getTimestamp() {
        return timestamp;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "status-index")
    public String getStatus() {
        return status;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "facilityId-index")
    public String getFacilityId() {
        return facilityId;
    }
}
