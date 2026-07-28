package com.ai.openai_api_service.service.m3;

import com.ai.openai_api_service.service.RequestedInformationResolver;
import com.ai.openai_api_service.service.api.ApiField;
import com.ai.openai_api_service.service.api.ApiFieldCatalog;
import com.ai.openai_api_service.service.api.ApiFieldMetadata;
import com.ai.openai_api_service.service.api.M3ApiKey;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Formats MI READ responses using requested-information codes and API field metadata.
 */
@Component
public class M3RequestedFieldsReplyFormatter {

    private final ApiFieldCatalog apiFieldCatalog;
    private final ApiFieldMetadata apiFieldMetadata;

    public M3RequestedFieldsReplyFormatter(ApiFieldCatalog apiFieldCatalog, ApiFieldMetadata apiFieldMetadata) {
        this.apiFieldCatalog = apiFieldCatalog;
        this.apiFieldMetadata = apiFieldMetadata;
    }

    public String format(
            M3ApiKey apiKey,
            List<String> requestedInformation,
            List<String> returnColumns,
            M3MiExecutionResult result
    ) {
        if (result == null) {
            return "Request could not be completed.";
        }
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            return "Request could not be completed: " + result.errorMessage().trim();
        }
        if (result.rows() == null || result.rows().isEmpty()) {
            return "No data found.";
        }
        return format(apiKey, requestedInformation, returnColumns, result.rows().getFirst());
    }

    public String format(
            M3ApiKey apiKey,
            List<String> requestedInformation,
            List<String> returnColumns,
            Map<String, Object> row
    ) {
        if (row == null || row.isEmpty()) {
            return "No data found.";
        }
        if (requestedInformation == null
                || requestedInformation.isEmpty()
                || (requestedInformation.size() == 1
                && RequestedInformationResolver.FULL.equals(requestedInformation.getFirst()))) {
            return compactSummary(row);
        }

        Set<String> returnColumnFilter = returnColumns != null && !returnColumns.isEmpty()
                ? Set.copyOf(returnColumns)
                : null;
        Set<String> emittedFields = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();

        for (String code : requestedInformation) {
            if (code == null || code.isBlank() || RequestedInformationResolver.FULL.equals(code)) {
                continue;
            }
            for (ApiField apiField : apiFieldCatalog.fieldsFor(apiKey, code)) {
                if (!apiField.visible()) {
                    continue;
                }
                String field = apiField.field();
                if (field == null || field.isBlank() || emittedFields.contains(field)) {
                    continue;
                }
                if (returnColumnFilter != null && !returnColumnFilter.contains(field)) {
                    continue;
                }
                Object raw = row.get(field);
                if (raw == null) {
                    raw = row.get(field.toUpperCase(Locale.ROOT));
                }
                if (raw == null || String.valueOf(raw).isBlank()) {
                    continue;
                }
                String formatted = apiFieldMetadata.format(apiField.formatterKey(), raw);
                if (formatted.isBlank()) {
                    continue;
                }
                emittedFields.add(field);
                lines.add(apiField.label() + " : " + formatted);
            }
        }

        if (lines.isEmpty()) {
            return compactSummary(row);
        }
        return String.join("\n", lines);
    }

    private static String compactSummary(Map<String, Object> row) {
        Object cuno = firstNonBlank(row, "CUNO", "CUNM", "ORNO");
        if (cuno != null) {
            return "Record found.";
        }
        return "Record found.";
    }

    private static Object firstNonBlank(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }
}
