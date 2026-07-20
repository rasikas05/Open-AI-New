package com.ai.openai_api_service.service.m3;

import org.springframework.stereotype.Component;

@Component
public class M3SearchReplyFormatter {

    public String format(M3MiExecutionResult result) {
        if (result == null) {
            return "Search could not be completed.";
        }
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            return "Search could not be completed: " + result.errorMessage().trim();
        }
        int count = result.recordCount();
        if (count == 0) {
            return "No records found.";
        }
        if (count == 1) {
            return "Found 1 matching record.";
        }
        return "Found " + count + " matching records.";
    }
}
