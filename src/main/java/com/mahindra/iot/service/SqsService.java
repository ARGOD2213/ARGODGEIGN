package com.mahindra.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahindra.iot.dto.SensorIngestRequest;
import com.mahindra.iot.model.Alert;
import com.mahindra.iot.model.SensorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqsService {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<SensorEventService> sensorEventServiceProvider;

    @Value("${aws.sqs.queue.url:}")
    private String queueUrl;

    @Scheduled(fixedDelay = 10000)
    public void pollQueue() {
        if (queueUrl == null || queueUrl.isBlank()) {
            log.debug("SQS queue URL not configured - skipping poll");
            return;
        }
        try {
            ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(5)
                    .visibilityTimeout(30)
                    .build();

            List<Message> messages = sqsClient.receiveMessage(request).messages();
            if (!messages.isEmpty()) {
                log.info("SQS poll: {} messages received", messages.size());
            }

            for (Message msg : messages) {
                try {
                    processMessage(msg);
                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(msg.receiptHandle())
                            .build());
                } catch (Exception e) {
                    log.error("Failed to process SQS message {}: {}", msg.messageId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("SQS poll error: {}", e.getMessage());
        }
    }

    private void processMessage(Message message) {
        try {
            SensorIngestRequest request = objectMapper.readValue(message.body(), SensorIngestRequest.class);
            log.info("SQS message received: device={}, type={}, value={}",
                    request.getDeviceId(), request.getSensorType(), request.getValue());
            sensorEventServiceProvider.getObject().ingestEvent(request);
        } catch (Exception e) {
            log.error("SQS message parse failed: {} | body={}", e.getMessage(),
                    message.body().substring(0, Math.min(100, message.body().length())));
        }
    }

    public String enqueueEvent(SensorEvent event) {
        if (queueUrl == null || queueUrl.isBlank()) return "SQS_SKIPPED";
        try {
            String body = objectMapper.writeValueAsString(event);
            SendMessageResponse response = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .messageGroupId(event.getDeviceId())
                    .messageDeduplicationId(event.getDeviceId() + "#" + event.getTimestamp())
                    .build());
            return response.messageId();
        } catch (Exception e) {
            log.error("SQS enqueue failed: {}", e.getMessage());
            return "SQS_FAILED";
        }
    }

    public String enqueueAlert(Alert alert) {
        if (queueUrl == null || queueUrl.isBlank()) return "SQS_SKIPPED";
        try {
            String body = objectMapper.writeValueAsString(alert);
            SendMessageResponse response = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .messageGroupId("ALERTS")
                    .messageDeduplicationId(alert.getAlertId())
                    .build());
            return response.messageId();
        } catch (Exception e) {
            log.error("SQS alert enqueue failed: {}", e.getMessage());
            return "SQS_FAILED";
        }
    }
}
