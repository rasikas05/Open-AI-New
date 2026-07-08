package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.InvalidLexSlotException;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class LexIntentMapper {

    public record MappedM3Request(
            String program,
            String transaction,
            Map<String, Object> params,
            String actionTaken
    ) {
    }

    public Optional<MappedM3Request> map(LexRecognizeResult lexResult) {
        if (lexResult == null || lexResult.getIntentName() == null) {
            return Optional.empty();
        }

        return switch (lexResult.getIntentName()) {
            case "GetCustomer" -> mapGetCustomer(lexResult);
            default -> Optional.empty();
        };
    }

    private Optional<MappedM3Request> mapGetCustomer(LexRecognizeResult lexResult) {
        String customerNumber = lexResult.getSlots().get("CustomerNumber");
        if (customerNumber == null || customerNumber.isBlank()) {
            throw new OpenAIException(
                    "GetCustomer is ready but CustomerNumber slot is missing",
                    400
            );
        }

        CunoValueNormalizer.Result normalized = CunoValueNormalizer.normalize(customerNumber);
        if (!normalized.valid()) {
            throw new InvalidLexSlotException(normalized.userMessage());
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("CUNO", normalized.cuno());
        return Optional.of(new MappedM3Request("CRS610MI", "GetBasicData", params, "read"));
    }
}
