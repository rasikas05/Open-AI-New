package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.InvalidLexSlotException;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.QueryContext;
import com.ai.openai_api_service.model.RequestType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves READ intent transaction record fields from {@link QueryContext} slots.
 */
public final class ReadIntentParameterResolver {

    private ReadIntentParameterResolver() {
    }

    public static Map<String, Object> resolve(IntentDefinition definition, QueryContext context) {
        if (definition.requestType() != RequestType.READ) {
            throw new OpenAIException("ReadIntentParameterResolver requires READ intent", 500);
        }

        return switch (definition.intentName()) {
            case "GetCustomer", "GetCustomerFinancial" -> mapCunoRead(definition, context);
            default -> throw new OpenAIException(
                    "No READ parameter mapping for intent: " + definition.intentName(),
                    400
            );
        };
    }

    private static Map<String, Object> mapCunoRead(IntentDefinition definition, QueryContext context) {
        String customerNumber = context.slots().get("CustomerNumber");
        if (customerNumber == null || customerNumber.isBlank()) {
            throw new OpenAIException(
                    definition.intentName() + " is ready but CustomerNumber slot is missing",
                    400
            );
        }

        CunoValueNormalizer.Result normalized = CunoValueNormalizer.normalize(customerNumber);
        if (!normalized.valid()) {
            throw new InvalidLexSlotException(normalized.userMessage());
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put(definition.primaryParameter(), normalized.cuno());
        return Map.copyOf(params);
    }

    public static String formatReturncols(QueryContext context) {
        if (context.returnColumns() == null || context.returnColumns().isEmpty()) {
            return null;
        }
        return context.returnColumns().stream()
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.joining(","));
    }
}
