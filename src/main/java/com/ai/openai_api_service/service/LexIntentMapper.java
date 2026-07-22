package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.QueryContext;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import org.springframework.stereotype.Component;

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

    private final IntentApiCatalog intentApiCatalog;
    private final M3RequestBuilder m3RequestBuilder;

    public LexIntentMapper(IntentApiCatalog intentApiCatalog, M3RequestBuilder m3RequestBuilder) {
        this.intentApiCatalog = intentApiCatalog;
        this.m3RequestBuilder = m3RequestBuilder;
    }

    public Optional<MappedM3Request> map(LexRecognizeResult lexResult) {
        if (lexResult == null || lexResult.getIntentName() == null) {
            return Optional.empty();
        }

        return intentApiCatalog.find(lexResult.getIntentName())
                .filter(def -> def.intentName().equals("GetCustomer")
                        || def.intentName().equals("GetCustomerFinancial"))
                .map(def -> mapReadIntent(def, lexResult));
    }

    private MappedM3Request mapReadIntent(IntentDefinition definition, LexRecognizeResult lexResult) {
        QueryContext context = QueryContext.forRead(
                definition.intentName(),
                lexResult.getSlots()
        );
        return m3RequestBuilder.build(definition, context);
    }
}
