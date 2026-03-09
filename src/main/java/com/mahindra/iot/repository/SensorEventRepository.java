package com.mahindra.iot.repository;

import com.mahindra.iot.model.SensorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.*;

@Repository
@RequiredArgsConstructor
@Slf4j
public class SensorEventRepository {

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private static final String TABLE_NAME = "iot-sensor-events";

    private DynamoDbTable<SensorEvent> table() {
        return dynamoDbEnhancedClient.table(TABLE_NAME,
                TableSchema.fromBean(SensorEvent.class));
    }

    public void save(SensorEvent event) {
        try {
            table().putItem(event);
            log.debug("Saved event: device={}, type={}", event.getDeviceId(), event.getSensorType());
        } catch (DynamoDbException e) {
            log.error("DynamoDB save failed: {}", e.getMessage());
            throw new RuntimeException("Failed to save sensor event", e);
        }
    }

    public List<SensorEvent> findByDeviceId(String deviceId) {
        try {
            QueryConditional qc = QueryConditional.keyEqualTo(
                    Key.builder().partitionValue(deviceId).build());
            return table().query(qc).items().stream().toList();
        } catch (DynamoDbException e) {
            log.error("DynamoDB query failed for device {}: {}", deviceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<SensorEvent> findAll() {
        try {
            return table().scan().items().stream().toList();
        } catch (DynamoDbException e) {
            log.error("DynamoDB scan failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
