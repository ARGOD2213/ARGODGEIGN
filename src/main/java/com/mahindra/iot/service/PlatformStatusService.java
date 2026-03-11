package com.mahindra.iot.service;

import com.mahindra.iot.model.SensorEvent;
import com.mahindra.iot.repository.SensorEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformStatusService {

    private final SensorEventRepository repository;
    private final SqsClient sqsClient;
    private final AthenaAnalyticsService athenaAnalyticsService;

    @Value("${aws.region:ap-south-1}")
    private String region;

    @Value("${aws.sqs.queue.url:}")
    private String queueUrl;

    @Value("${aws.sqs.dlq.url:}")
    private String dlqUrl;

    @Value("${aws.ec2.instance.id:}")
    private String ec2InstanceId;

    @Value("${platform.rule.engine.mode:LAMBDA}")
    private String ruleEngineMode;

    @Value("${platform.environment:demo}")
    private String environment;

    @Value("${ai.gemini.api.key:}")
    private String geminiApiKey;

    @Cacheable("platform-status")
    public Map<String, Object> getPlatformStatus() {
        List<SensorEvent> allEvents = repository.findAll();
        String lastAlertTs = allEvents.stream()
                .filter(e -> "WARNING".equalsIgnoreCase(e.getStatus()) || "CRITICAL".equalsIgnoreCase(e.getStatus()))
                .map(SensorEvent::getTimestamp)
                .max(String::compareTo)
                .orElse("N/A");

        String llmStatus = (geminiApiKey != null && !geminiApiKey.isBlank()) ? "AVAILABLE" : "UNAVAILABLE";
        Map<String, Object> dlq = getDlqStatus();

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("platform", "RUNNING");
        status.put("environment", environment);
        status.put("timestamp", Instant.now().toString());
        status.put("awsRegion", region);
        status.put("ruleEngine", ruleEngineMode.toUpperCase());
        status.put("llm", llmStatus);
        status.put("athena", athenaAnalyticsService.isConfigured() ? "CONFIGURED" : "NOT_CONFIGURED");
        status.put("queueConfigured", queueUrl != null && !queueUrl.isBlank());
        status.put("dlqConfigured", dlqUrl != null && !dlqUrl.isBlank());
        status.put("dlqDepth", dlq.get("approximateVisibleMessages"));
        status.put("lastAlert", lastAlertTs);
        status.put("eventCount", allEvents.size());
        status.put("ec2InstanceId", ec2InstanceId == null || ec2InstanceId.isBlank() ? "N/A" : ec2InstanceId);
        status.put("ec2State", "UNKNOWN_FROM_APP");
        return status;
    }

    @Cacheable("dlq-status")
    public Map<String, Object> getDlqStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("timestamp", Instant.now().toString());
        status.put("dlqUrl", dlqUrl == null || dlqUrl.isBlank() ? "N/A" : dlqUrl);

        if (dlqUrl == null || dlqUrl.isBlank()) {
            status.put("configured", false);
            status.put("approximateVisibleMessages", -1);
            status.put("approximateMessagesNotVisible", -1);
            status.put("oldestMessageAgeSeconds", -1);
            status.put("note", "Set aws.sqs.dlq.url to enable DLQ status checks.");
            return status;
        }

        try {
            GetQueueAttributesResponse response = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(dlqUrl)
                    .attributeNames(
                            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                    .build());

            Map<QueueAttributeName, String> attrs = response.attributes();
            status.put("configured", true);
            status.put("approximateVisibleMessages", toLong(attrs.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)));
            status.put("approximateMessagesNotVisible", toLong(attrs.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)));
            status.put("oldestMessageAgeSeconds", -1);
            status.put("oldestMessageAgeNote", "Not available via SQS queue attributes; use CloudWatch metric ApproximateAgeOfOldestMessage.");
            status.put("health", "OK");
        } catch (Exception e) {
            log.warn("DLQ status check failed: {}", e.getMessage());
            status.put("configured", true);
            status.put("health", "ERROR");
            status.put("error", e.getMessage());
            status.put("approximateVisibleMessages", -1);
            status.put("approximateMessagesNotVisible", -1);
            status.put("oldestMessageAgeSeconds", -1);
        }
        return status;
    }

    private long toLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
