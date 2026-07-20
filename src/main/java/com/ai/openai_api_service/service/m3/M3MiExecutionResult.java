package com.ai.openai_api_service.service.m3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record M3MiExecutionResult(
        boolean success,
        String program,
        String transaction,
        int recordCount,
        List<String> columns,
        List<Map<String, Object>> rows,
        String errorMessage
) {

    public static M3MiExecutionResult failure(
            String program,
            String transaction,
            String errorMessage
    ) {
        return new M3MiExecutionResult(
                false,
                program != null ? program : "",
                transaction != null ? transaction : "",
                0,
                List.of(),
                List.of(),
                errorMessage != null ? errorMessage : "Unknown error"
        );
    }

    public Map<String, Object> toM3DataMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("program", program);
        map.put("transaction", transaction);
        map.put("recordCount", recordCount);
        map.put("columns", columns);
        map.put("rows", rows);
        map.put("error", errorMessage);
        return map;
    }
}
