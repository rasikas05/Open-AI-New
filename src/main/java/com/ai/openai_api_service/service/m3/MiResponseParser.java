package com.ai.openai_api_service.service.m3;

import com.ai.openai_api_service.model.python_rag.M3MiCallResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Component
public class MiResponseParser {

    public M3MiExecutionResult parse(M3MiCallResponse response) {
        if (response == null) {
            return M3MiExecutionResult.failure("", "", "Empty M3 response");
        }

        String program = nullToEmpty(response.getProgram());
        String transaction = nullToEmpty(response.getTransaction());

        List<Map<String, Object>> records = response.getRecords() != null && !response.getRecords().isEmpty()
                ? response.getRecords()
                : extractRecordsFromRaw(response.getRaw());

        if (response.getError() != null && !response.getError().isBlank() && records.isEmpty()) {
            return M3MiExecutionResult.failure(program, transaction, response.getError().trim());
        }

        String errorMessage = response.getErrorMessage();
        if (errorMessage != null && !errorMessage.isBlank() && records.isEmpty()) {
            return M3MiExecutionResult.failure(program, transaction, errorMessage.trim());
        }

        if (records.isEmpty() && response.getRaw() != null) {
            String rawError = extractErrorFromRaw(response.getRaw());
            if (rawError != null && !rawError.isBlank()) {
                return M3MiExecutionResult.failure(program, transaction, rawError.trim());
            }
        }

        return buildSuccess(program, transaction, records);
    }

    private static M3MiExecutionResult buildSuccess(
            String program,
            String transaction,
            List<Map<String, Object>> records
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        TreeSet<String> columnSet = new TreeSet<>();

        for (Map<String, Object> record : records) {
            if (record == null || record.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(record);
            rows.add(row);
            columnSet.addAll(row.keySet());
        }

        return new M3MiExecutionResult(
                true,
                program,
                transaction,
                rows.size(),
                List.copyOf(columnSet),
                List.copyOf(rows),
                null
        );
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractRecordsFromRaw(Map<String, Object> raw) {
        if (raw == null) {
            return List.of();
        }
        Object resultsObj = raw.get("results");
        if (!(resultsObj instanceof List<?> results) || results.isEmpty()) {
            return List.of();
        }
        Object firstObj = results.get(0);
        if (!(firstObj instanceof Map<?, ?> first)) {
            return List.of();
        }
        Object recordsObj = first.get("records");
        if (!(recordsObj instanceof List<?> recordsList)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : recordsList) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                out.add(row);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String extractErrorFromRaw(Map<String, Object> raw) {
        Object resultsObj = raw.get("results");
        if (!(resultsObj instanceof List<?> results) || results.isEmpty()) {
            return null;
        }
        Object firstObj = results.get(0);
        if (!(firstObj instanceof Map<?, ?> first)) {
            return null;
        }
        Object msg = first.get("errorMessage");
        return msg != null ? msg.toString() : null;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
