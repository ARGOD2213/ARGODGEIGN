package com.mahindra.iot.service;

import com.mahindra.iot.model.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SnsAlertService {

    private final SnsClient snsClient;

    @Value("${aws.sns.topic.arn:}")
    private String topicArn;

    public String publishAlert(Alert alert) {
        if (topicArn == null || topicArn.isBlank()) {
            log.warn("SNS topic ARN not configured — skipping notification");
            return "SNS_SKIPPED";
        }
        try {
            String message = buildMessage(alert);
            PublishRequest request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(String.format("[%s] IoT Alert — %s on %s",
                            alert.getSeverity(), alert.getSensorType(), alert.getDeviceId()))
                    .message(message)
                    .build();

            PublishResponse response = snsClient.publish(request);
            log.info("SNS alert published: alertId={}, messageId={}", alert.getAlertId(), response.messageId());
            return response.messageId();
        } catch (Exception e) {
            log.error("SNS publish failed: {}", e.getMessage());
            return "SNS_FAILED";
        }
    }

    private String buildMessage(Alert alert) {
        return String.format("""
                🚨 IoT ALERT — %s
                
                Device   : %s
                Sensor   : %s
                Value    : %.2f
                Threshold: %.2f
                Severity : %s
                Time     : %s
                
                AI Analysis: %s
                Risk Score : %s
                
                Recommended Action: %s
                Weather Note: %s
                """,
                alert.getSeverity(),
                alert.getDeviceId(),
                alert.getSensorType(),
                alert.getValue() != null ? alert.getValue() : 0.0,
                alert.getThreshold() != null ? alert.getThreshold() : 0.0,
                alert.getSeverity(),
                alert.getTimestamp(),
                alert.getAiSummary() != null ? alert.getAiSummary() : "N/A",
                alert.getAiRiskScore() != null ? alert.getAiRiskScore() : "N/A",
                alert.getMessage(),
                alert.getWeatherNote() != null ? alert.getWeatherNote() : "N/A"
        );
    }
}
