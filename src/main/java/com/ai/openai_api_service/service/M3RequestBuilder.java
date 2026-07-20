package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SearchCriterion;
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

    public LexIntentMapper.MappedM3Request build(IntentDefinition definition, List<SearchCriterion> criteria) {
        Map<String, Object> params = new LinkedHashMap<>();

        if (hasUsableCriteria(criteria)) {
            String sqry = sqryBuilder.build(criteria);
            if (!sqry.isBlank()) {
                params.put(definition.primaryParameter(), sqry);
            }
        }

        return new LexIntentMapper.MappedM3Request(
                definition.program(),
                definition.transaction(),
                Map.copyOf(params),
                actionTakenFor(definition.requestType())
        );
    }

    private static boolean hasUsableCriteria(List<SearchCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return false;
        }
        for (SearchCriterion criterion : criteria) {
            if (criterion == null) {
                continue;
            }
            String field = criterion.field();
            String value = criterion.value();
            if (field != null && !field.isBlank() && value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static String actionTakenFor(RequestType requestType) {
        return requestType == RequestType.SEARCH ? "search" : "read";
    }
}
