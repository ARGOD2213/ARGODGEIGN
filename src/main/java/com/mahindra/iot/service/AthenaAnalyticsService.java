package com.mahindra.iot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecutionContext;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.StopQueryExecutionRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AthenaAnalyticsService {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_\\-]+");

    private final AthenaClient athenaClient;

    @Value("${athena.enabled:true}")
    private boolean athenaEnabled;

    @Value("${athena.database:argodreign_analytics}")
    private String database;

    @Value("${athena.table:sensor_events_partitioned}")
    private String table;

    @Value("${athena.output.location:}")
    private String outputLocation;

    @Value("${athena.poll.interval.ms:1500}")
    private long pollIntervalMs;

    @Value("${athena.query.timeout.seconds:45}")
    private long queryTimeoutSeconds;

    @Value("${athena.max.results:1000}")
    private int maxResults;

    public AthenaAnalyticsService(AthenaClient athenaClient) {
        this.athenaClient = athenaClient;
    }

    public boolean isConfigured() {
        return athenaEnabled
                && outputLocation != null
                && !outputLocation.isBlank()
                && database != null
                && !database.isBlank()
                && table != null
                && !table.isBlank();
    }

    public List<Map<String, Object>> queryMachineTrend(String machineId, int hours, String machineClassHint) {
        if (!isConfigured() || !isSafeIdentifier(machineId)) {
            return List.of();
        }

        int safeHours = clamp(hours, 1, 168);
        String machineClassFilter = normalizePartitionValue(machineClassHint);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT event_timestamp, sensor_type, value, status, machine_class, year, month, day ")
                .append("FROM ").append(database).append(".").append(table).append(" ")
                .append("WHERE machine_id = '").append(escapeSqlLiteral(machineId)).append("' ")
                .append("AND try(from_iso8601_timestamp(event_timestamp)) >= date_add('hour', -")
                .append(safeHours)
                .append(", current_timestamp) ");

        if (!machineClassFilter.isBlank()) {
            sql.append("AND machine_class = '").append(escapeSqlLiteral(machineClassFilter)).append("' ");
        }

        sql.append("ORDER BY event_timestamp DESC ")
                .append("LIMIT ").append(clamp(maxResults, 100, 5000));

        return runQuery(sql.toString());
    }

    public Optional<Map<String, Object>> queryMachineOee(String machineId, int hours, String machineClassHint) {
        if (!isConfigured() || !isSafeIdentifier(machineId)) {
            return Optional.empty();
        }

        int safeHours = clamp(hours, 1, 168);
        String machineClassFilter = normalizePartitionValue(machineClassHint);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
                .append("COUNT(*) AS total_events, ")
                .append("SUM(CASE WHEN status <> 'CRITICAL' THEN 1 ELSE 0 END) AS available_events, ")
                .append("SUM(CASE WHEN status = 'NORMAL' THEN 1 ELSE 0 END) AS quality_events, ")
                .append("AVG(value) AS avg_value, ")
                .append("AVG(max_value) AS avg_max_value ")
                .append("FROM ").append(database).append(".").append(table).append(" ")
                .append("WHERE machine_id = '").append(escapeSqlLiteral(machineId)).append("' ")
                .append("AND try(from_iso8601_timestamp(event_timestamp)) >= date_add('hour', -")
                .append(safeHours)
                .append(", current_timestamp) ");

        if (!machineClassFilter.isBlank()) {
            sql.append("AND machine_class = '").append(escapeSqlLiteral(machineClassFilter)).append("' ");
        }

        List<Map<String, Object>> rows = runQuery(sql.toString());
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> row = rows.get(0);
        double total = parseDouble(row.get("total_events"));
        double available = parseDouble(row.get("available_events"));
        double qualityGood = parseDouble(row.get("quality_events"));
        double avgValue = parseDouble(row.get("avg_value"));
        double avgMax = parseDouble(row.get("avg_max_value"));

        if (total <= 0) {
            return Optional.empty();
        }

        double availability = clamp01(available / total);
        double performance = avgMax <= 0 ? 1.0 : clamp01(avgValue / avgMax);
        double quality = clamp01(qualityGood / total);
        double oee = availability * performance * quality * 100.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("machineId", machineId);
        result.put("hours", safeHours);
        result.put("availability", round4(availability));
        result.put("performance", round4(performance));
        result.put("quality", round4(quality));
        result.put("oeePercent", round2(oee));
        result.put("totalEvents", (long) total);
        result.put("source", "ATHENA");
        return Optional.of(result);
    }

    private List<Map<String, Object>> runQuery(String query) {
        try {
            StartQueryExecutionRequest request = StartQueryExecutionRequest.builder()
                    .queryString(query)
                    .queryExecutionContext(QueryExecutionContext.builder().database(database).build())
                    .resultConfiguration(ResultConfiguration.builder().outputLocation(outputLocation).build())
                    .build();

            StartQueryExecutionResponse start = athenaClient.startQueryExecution(request);
            String queryExecutionId = start.queryExecutionId();
            waitForCompletion(queryExecutionId);
            return readAllRows(queryExecutionId);
        } catch (Exception e) {
            log.warn("Athena query failed, fallback will be used: {}", e.getMessage());
            return List.of();
        }
    }

    private void waitForCompletion(String queryExecutionId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(Math.max(5, queryTimeoutSeconds)));

        while (Instant.now().isBefore(deadline)) {
            GetQueryExecutionResponse response = athenaClient.getQueryExecution(
                    GetQueryExecutionRequest.builder().queryExecutionId(queryExecutionId).build());

            QueryExecutionState state = response.queryExecution().status().state();
            if (state == QueryExecutionState.SUCCEEDED) {
                return;
            }

            if (state == QueryExecutionState.FAILED || state == QueryExecutionState.CANCELLED) {
                String reason = response.queryExecution().status().stateChangeReason();
                throw new IllegalStateException("Athena query " + queryExecutionId + " failed: " + reason);
            }

            Thread.sleep(Math.max(300, pollIntervalMs));
        }

        athenaClient.stopQueryExecution(StopQueryExecutionRequest.builder()
                .queryExecutionId(queryExecutionId)
                .build());
        throw new IllegalStateException("Athena query timed out: " + queryExecutionId);
    }

    private List<Map<String, Object>> readAllRows(String queryExecutionId) {
        List<Map<String, Object>> output = new ArrayList<>();
        String nextToken = null;
        List<String> headers = List.of();
        boolean headerCaptured = false;

        do {
            GetQueryResultsRequest req = GetQueryResultsRequest.builder()
                    .queryExecutionId(queryExecutionId)
                    .maxResults(clamp(maxResults, 100, 5000))
                    .nextToken(nextToken)
                    .build();
            GetQueryResultsResponse resp = athenaClient.getQueryResults(req);
            List<Row> rows = resp.resultSet().rows();

            if (!headerCaptured && !rows.isEmpty()) {
                headers = extractHeaders(rows.get(0));
                headerCaptured = true;
                rows = rows.subList(1, rows.size());
            }

            for (Row row : rows) {
                output.add(toMap(headers, row));
            }
            nextToken = resp.nextToken();
        } while (nextToken != null);

        return output;
    }

    private List<String> extractHeaders(Row headerRow) {
        List<String> headers = new ArrayList<>();
        headerRow.data().forEach(d -> headers.add(Optional.ofNullable(d.varCharValue()).orElse("col")));
        return headers;
    }

    private Map<String, Object> toMap(List<String> headers, Row row) {
        Map<String, Object> map = new LinkedHashMap<>();
        int limit = Math.min(headers.size(), row.data().size());
        for (int i = 0; i < limit; i++) {
            String key = headers.get(i);
            String value = row.data().get(i).varCharValue();
            map.put(key, value);
        }
        return map;
    }

    private boolean isSafeIdentifier(String text) {
        return text != null && SAFE_ID.matcher(text).matches();
    }

    private String normalizePartitionValue(String machineClassHint) {
        if (machineClassHint == null || machineClassHint.isBlank()) {
            return "";
        }
        String normalized = machineClassHint.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return normalized;
    }

    private String escapeSqlLiteral(String input) {
        return input.replace("'", "''");
    }

    private double parseDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
