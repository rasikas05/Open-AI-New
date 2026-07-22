package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.QueryContext;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.service.api.SpecificInformationHelper;
import com.ai.openai_api_service.service.validation.SearchCriteriaValidator;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class M3RequestBuilder {

    private final SqryBuilder sqryBuilder;

    public M3RequestBuilder(SqryBuilder sqryBuilder) {
        this.sqryBuilder = sqryBuilder;
    }

    public LexIntentMapper.MappedM3Request buildFromCriteria(
            IntentDefinition definition,
            List<SearchCriterion> criteria
    ) {
        QueryContext context = new QueryContext(
                definition.intentName(),
                Map.of(),
                criteria != null ? criteria : List.of(),
                List.of(),
                null,
                List.of(),
                null,
                null,
                null,
                Map.of(),
                false
        );
        return build(definition, context);
    }

    public LexIntentMapper.MappedM3Request build(IntentDefinition definition, QueryContext context) {
        Map<String, Object> params = new LinkedHashMap<>();

        if (definition.requestType() == RequestType.SEARCH) {
            applySearchPrimaryParameter(definition, context, params);
        } else {
            params.putAll(ReadIntentParameterResolver.resolve(definition, context));
        }

        applyOptionalExecutionFields(definition, context, params);

        return new LexIntentMapper.MappedM3Request(
                definition.program(),
                definition.transaction(),
                Map.copyOf(params),
                actionTakenFor(definition.requestType())
        );
    }

    private void applySearchPrimaryParameter(
            IntentDefinition definition,
            QueryContext context,
            Map<String, Object> params
    ) {
        List<SearchCriterion> criteria = context.criteria();
        if (!SearchCriteriaValidator.hasUsableCriteria(criteria)) {
            return;
        }
        String sqry = sqryBuilder.build(criteria);
        if (!sqry.isBlank()) {
            params.put(definition.primaryParameter(), sqry);
        }
    }

    private static void applyOptionalExecutionFields(
            IntentDefinition definition,
            QueryContext context,
            Map<String, Object> params
    ) {
        if (context.limit() != null && context.limit() > 0) {
            params.put("maxrecs", context.limit());
        }

        String positionKey = context.positionKey();
        if (positionKey != null && !positionKey.isBlank()) {
            params.put("positionkey", positionKey.trim());
        }

        if (definition.requestType() == RequestType.READ) {
            String returncols = ReadIntentParameterResolver.formatReturncols(context);
            if (returncols != null && !returncols.isBlank()) {
                params.put("returncols", returncols);
            }
        } else if (definition.requestType() == RequestType.SEARCH
                && SpecificInformationHelper.useApiCapabilityReturncols(context.requestedInformation())) {
            String returncols = ReadIntentParameterResolver.formatReturncols(context);
            if (returncols != null && !returncols.isBlank()) {
                params.put("returncols", returncols);
            }
        }
    }

    private static String actionTakenFor(RequestType requestType) {
        return requestType == RequestType.SEARCH ? "search" : "read";
    }
}
