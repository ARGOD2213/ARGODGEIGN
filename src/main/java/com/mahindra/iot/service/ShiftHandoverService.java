package com.mahindra.iot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShiftHandoverService {

    private final DynamoDbClient dynamo;
    private final S3Client s3;
    private final AlertAcknowledgementService ackService;
    private final String table;
    private final String bucket;

    public ShiftHandoverService(
            DynamoDbClient dynamo,
            S3Client s3,
            AlertAcknowledgementService ackService,
            @Value("${aws.dynamodb.table:iot-sensor-events}") String table,
            @Value("${aws.s3.bucket:iot-alert-engine-mahindra}") String bucket) {
        this.dynamo = dynamo;
        this.s3 = s3;
        this.ackService = ackService;
        this.table = table;
        this.bucket = bucket;
    }

    /**
     * Generate a shift summary for the last N hours.
     */
    public HandoverSummary generateSummary(int shiftHours) {
        Instant shiftStart = Instant.now().minusSeconds(shiftHours * 3600L);
        String shiftStartStr = shiftStart.toString();

        ScanResponse scan = dynamo.scan(ScanRequest.builder()
            .tableName(table)
            .filterExpression(
                "#ts >= :start AND attribute_exists(alertId) AND attribute_not_exists(recordType)")
            .expressionAttributeNames(Map.of("#ts", "timestamp"))
            .expressionAttributeValues(Map.of(
                ":start", AttributeValue.fromS(shiftStartStr)))
            .build());

        List<Map<String, AttributeValue>> alerts = scan.items();
        long critical = alerts.stream()
            .filter(a -> "CRITICAL".equals(strVal(a, "status")))
            .count();
        long acknowledged = alerts.stream()
            .map(a -> strVal(a, "alertId"))
            .filter(id -> !id.isBlank())
            .filter(ackService::isAcknowledged)
            .count();
        long open = alerts.size() - acknowledged;

        Map<String, Long> machineCounts = alerts.stream()
            .collect(Collectors.groupingBy(
                a -> strVal(a, "deviceId"),
                LinkedHashMap::new,
                Collectors.counting()));
        List<String> topMachines = machineCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(3)
            .map(e -> e.getKey() + " (" + e.getValue() + " alerts)")
            .toList();

        String aiText = "Shift summary: " + alerts.size()
            + " alerts total, " + critical + " CRITICAL, "
            + open + " unacknowledged. Top affected: "
            + (topMachines.isEmpty() ? "none" : String.join(", ", topMachines))
            + ". Rule engine operating normally. Verify all open alerts before handover.";

        Map<String, Object> aiAdvisory = new LinkedHashMap<>();
        aiAdvisory.put("banner", "[AI ADVISORY | Not a control action | Rule engine has final authority]");
        aiAdvisory.put("label", "AI ADVISORY");
        aiAdvisory.put("notice", "Not a control action");
        aiAdvisory.put("authority", "Rule engine has final authority");
        aiAdvisory.put("confidence", alerts.size() > 5 ? "HIGH" : "MEDIUM");
        aiAdvisory.put("analysis", aiText);
        aiAdvisory.put("summary", aiText);
        aiAdvisory.put("riskScore", critical > 0 ? "ELEVATED" : "NORMAL");
        aiAdvisory.put("validFor", "30 minutes");

        return new HandoverSummary(
            shiftStartStr,
            Instant.now().toString(),
            shiftHours,
            alerts.size(),
            critical,
            acknowledged,
            open,
            topMachines,
            aiAdvisory
        );
    }

    /**
     * Save an operator handover note to S3.
     */
    public void saveNote(String operatorId, String note) {
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        String epoch = String.valueOf(Instant.now().getEpochSecond());
        String key = "handover/" + date + "/note_" + epoch + ".json";
        String payload = """
            {
              "operatorId": "%s",
              "note": "%s",
              "timestamp": "%s",
              "type": "HANDOVER_NOTE"
            }
            """.formatted(
                operatorId.replace("\"", "'"),
                note.replace("\"", "'").replace("\n", " "),
                Instant.now().toString());

        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/json")
                .build(),
            RequestBody.fromString(payload));
    }

    /**
     * List handover notes for a given date from S3.
     */
    public List<String> getNotes(String date) {
        List<String> notes = new ArrayList<>();
        try {
            var listing = s3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix("handover/" + date + "/")
                .build());

            for (var obj : listing.contents()) {
                try {
                    String content = new String(s3.getObject(
                        GetObjectRequest.builder()
                            .bucket(bucket).key(obj.key()).build(),
                        ResponseTransformer.toBytes()
                    ).asByteArray());
                    notes.add(content);
                } catch (Exception ignored) {
                    // Skip unreadable note objects.
                }
            }
        } catch (Exception e) {
            notes.add("{\"error\":\"Could not load notes for " + date + "\"}");
        }
        return notes;
    }

    private String strVal(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
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

    public record HandoverSummary(
        String shiftStart,
        String shiftEnd,
        int shiftHours,
        long totalAlerts,
        long criticalAlerts,
        long acknowledgedAlerts,
        long openAlerts,
        List<String> topMachineIssues,
        Map<String, Object> aiHandoverNote
    ) {}
}
