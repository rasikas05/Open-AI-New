package com.ai.openai_api_service.service.query;

import com.ai.openai_api_service.model.LexFulfillmentSession;
import com.ai.openai_api_service.model.M3ClientReportDto;
import com.ai.openai_api_service.model.QueryContext;
import com.ai.openai_api_service.model.SearchContext;
import com.ai.openai_api_service.service.LexIntentMapper;

import java.util.Optional;

/**
 * Session-scoped search pagination state (swappable persistence backend).
 */
public interface SearchContextService {

    SearchContext startOrReplaceSearch(
            LexFulfillmentSession session,
            String intentName,
            LexIntentMapper.MappedM3Request mapped,
            QueryContext queryContext
    );

    Optional<SearchContext> findActive(LexFulfillmentSession session);

    void applyClientReport(LexFulfillmentSession session, M3ClientReportDto report);

    QueryContext applyContinuation(LexFulfillmentSession session, QueryContext enrichedContext);

    Optional<LexIntentMapper.MappedM3Request> buildContinuationRequest(
            LexFulfillmentSession session,
            QueryContext enrichedContext
    );

    void clearSession(LexFulfillmentSession session);
}
