package com.mahindra.iot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class AlertAcknowledgementService {

    private static final String ACK_RECORD_TYPE = "ALERT_ACKNOWLEDGEMENT";

    private final DynamoDbClient dynamo;
    private final SnsClient sns;
    private final String table;
    private final String snsTopicArn;

    public AlertAcknowledgementService(
            DynamoDbClient dynamo,
            SnsClient sns,
            @Value("${aws.dynamodb.table:iot-sensor-events}") String table,
            @Value("${aws.sns.topic.arn}") String snsTopicArn) {
        this.dynamo = dynamo;
        this.sns = sns;
        this.table = table;
        this.snsTopicArn = snsTopicArn;
    }

    /**
     * Acknowledge an alert by storing a separate operational record in DynamoDB.
     * The record uses the existing deviceId/timestamp table shape so it can
     * coexist with sensor events in the same table.
     */
    public AckResult acknowledge(String alertId, String operatorId, String note) {
        Map<String, AttributeValue> sourceAlert = findAlertEvent(alertId)
            .orElseThrow(() -> new NoSuchElementException("Alert not found: " + alertId));

        String ackedAt = Instant.now().toString();
        long ttlEpoch = Instant.now().plusSeconds(90L * 24 * 3600).getEpochSecond();
        String deviceId = strVal(sourceAlert, "deviceId");
        String sensorType = strVal(sourceAlert, "sensorType");
        String safeOperatorId = operatorId == null || operatorId.isBlank() ? "UNKNOWN" : operatorId;
        String safeNote = note == null ? "" : note;

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("deviceId", AttributeValue.fromS(deviceId));
        item.put("timestamp", AttributeValue.fromS(ackedAt));
        item.put("recordType", AttributeValue.fromS(ACK_RECORD_TYPE));
        item.put("alertId", AttributeValue.fromS("ack#" + alertId));
        item.put("originalAlertId", AttributeValue.fromS(alertId));
        item.put("ackStatus", AttributeValue.fromS("ACKNOWLEDGED"));
        item.put("acknowledgedBy", AttributeValue.fromS(safeOperatorId));
        item.put("acknowledgedAt", AttributeValue.fromS(ackedAt));
        item.put("operatorNote", AttributeValue.fromS(safeNote));
        item.put("sensorType", AttributeValue.fromS(sensorType));
        item.put("ttl", AttributeValue.fromN(String.valueOf(ttlEpoch)));

        dynamo.putItem(PutItemRequest.builder()
            .tableName(table)
            .item(item)
            .build());

        return new AckResult(alertId, "ACKNOWLEDGED", safeOperatorId, ackedAt, safeNote);
    }

    /**
     * Escalate an unacknowledged CRITICAL alert via SNS.
     * Called when an alert has not been acknowledged within 5 minutes.
     * Rule engine retains final authority; this is operator notification only.
     */
    public void escalate(String alertId, String machineId,
                         String sensorType, String value) {
        String subject = "ESCALATION - Unacknowledged CRITICAL Alert: "
                         + machineId + "/" + sensorType;
        String message = String.format(
            """
            ARGUS ESCALATION NOTICE
            -----------------------------
            Alert ID   : %s
            Machine    : %s
            Sensor     : %s
            Value      : %s
            Status     : CRITICAL - not acknowledged within 5 minutes
            Action     : Operator acknowledgement required immediately
            -----------------------------
            AI ADVISORY | Not a control action | Rule engine has final authority
            """,
            alertId, machineId, sensorType, value);

        sns.publish(PublishRequest.builder()
            .topicArn(snsTopicArn)
            .subject(subject)
            .message(message)
            .build());
    }

    /**
     * Check if an alert has been acknowledged.
     */
    public boolean isAcknowledged(String alertId) {
        if (alertId == null || alertId.isBlank()) {
            return false;
        }

        ScanResponse response = dynamo.scan(ScanRequest.builder()
            .tableName(table)
            .filterExpression("recordType = :type AND originalAlertId = :alertId")
            .expressionAttributeValues(Map.of(
                ":type", AttributeValue.fromS(ACK_RECORD_TYPE),
                ":alertId", AttributeValue.fromS(alertId)))
            .limit(1)
            .build());
        return !response.items().isEmpty();
    }

    /**
     * Get all CRITICAL alerts that are unacknowledged.
     * Uses scan with filter, which is acceptable at demo volumes.
     */
    public List<UnacknowledgedAlert> getUnacknowledged() {
        ScanResponse response = dynamo.scan(ScanRequest.builder()
            .tableName(table)
            .filterExpression(
                "attribute_exists(alertId) AND attribute_not_exists(recordType) AND #status = :sev")
            .expressionAttributeNames(Map.of("#status", "status"))
            .expressionAttributeValues(Map.of(
                ":sev", AttributeValue.fromS("CRITICAL")))
            .build());

        List<UnacknowledgedAlert> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            String alertId = strVal(item, "alertId");
            if (alertId.isEmpty() || isAcknowledged(alertId)) {
                continue;
            }

            String ts = strVal(item, "timestamp");
            long ageMinutes = 0;
            if (!ts.isEmpty()) {
                ageMinutes = (Instant.now().getEpochSecond()
                    - Instant.parse(ts).getEpochSecond()) / 60;
            }

            result.add(new UnacknowledgedAlert(
                alertId,
                strVal(item, "deviceId"),
                strVal(item, "sensorType"),
                attrAsString(item.get("value")),
                ageMinutes
            ));
        }

        result.sort(Comparator.comparingLong(UnacknowledgedAlert::ageMinutes).reversed());
        return result;
    }

    private Optional<Map<String, AttributeValue>> findAlertEvent(String alertId) {
        if (alertId == null || alertId.isBlank()) {
            return Optional.empty();
        }

        ScanResponse response = dynamo.scan(ScanRequest.builder()
            .tableName(table)
            .filterExpression(
                "attribute_exists(alertId) AND attribute_not_exists(recordType) AND alertId = :alertId")
            .expressionAttributeValues(Map.of(
                ":alertId", AttributeValue.fromS(alertId)))
            .limit(1)
            .build());

        if (response.items().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(response.items().get(0));
    }

    private String strVal(Map<String, AttributeValue> item, String key) {
        return attrAsString(item.get(key));
    }

    private String attrAsString(AttributeValue value) {
        if (value == null) {
            return "";
        }
        if (value.s() != null) {
            return value.s();
        }
        if (value.n() != null) {
            return value.n();
        }
        return "";
    }

    public record AckResult(
        String alertId,
        String status,
        String acknowledgedBy,
        String acknowledgedAt,
        String note
    ) {}

    public record UnacknowledgedAlert(
        String alertId,
        String machineId,
        String sensorType,
        String value,
        long ageMinutes
    ) {}
}
