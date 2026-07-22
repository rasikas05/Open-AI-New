package com.ai.openai_api_service.service.query;

import com.ai.openai_api_service.model.LexFulfillmentSession;
import com.ai.openai_api_service.model.M3ClientReportDto;
import com.ai.openai_api_service.model.QueryContext;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SearchContext;
import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.LexIntentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, session-scoped {@link SearchContext} store (v1). Cleared on session close or TTL expiry.
 */
@Service
public class InMemorySearchContextService implements SearchContextService {

    private final IntentApiCatalog intentApiCatalog;
    private final long ttlSeconds;

    private final ConcurrentHashMap<String, Entry> bySessionKey = new ConcurrentHashMap<>();

    public InMemorySearchContextService(
            IntentApiCatalog intentApiCatalog,
            @Value("${m3.search-context.ttl-seconds:3600}") long ttlSeconds
    ) {
        this.intentApiCatalog = intentApiCatalog;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public SearchContext startOrReplaceSearch(
            LexFulfillmentSession session,
            String intentName,
            LexIntentMapper.MappedM3Request mapped,
            QueryContext queryContext
    ) {
        if (session == null || !session.isComplete()) {
            return null;
        }
        if (!isSearchIntent(intentName)) {
            return null;
        }

        String fingerprint = buildFingerprint(mapped);
        SearchContext context = new SearchContext(
                UUID.randomUUID().toString(),
                intentName,
                mapped.program(),
                mapped.transaction(),
                fingerprint,
                stripPositionKey(mapped.params()),
                null,
                queryContext.limit(),
                1L,
                Instant.now()
        );
        bySessionKey.put(sessionKey(session), new Entry(context));
        return context;
    }

    @Override
    public Optional<SearchContext> findActive(LexFulfillmentSession session) {
        return activeEntry(session).map(Entry::context);
    }

    @Override
    public void applyClientReport(LexFulfillmentSession session, M3ClientReportDto report) {
        if (session == null || !session.isComplete() || report == null) {
            return;
        }
        Entry entry = bySessionKey.get(sessionKey(session));
        if (entry == null || entry.isExpired(ttlSeconds)) {
            return;
        }
        SearchContext current = entry.context();
        if (report.getSearchContextId() != null
                && !report.getSearchContextId().equals(current.searchContextId())) {
            return;
        }
        String positionKey = report.getPositionkey();
        if (positionKey == null || positionKey.isBlank()) {
            return;
        }
        SearchContext updated = new SearchContext(
                current.searchContextId(),
                current.intentName(),
                current.program(),
                current.transaction(),
                current.queryFingerprint(),
                current.recordParams(),
                positionKey.trim(),
                current.pageSize(),
                current.version() + 1,
                Instant.now()
        );
        bySessionKey.put(sessionKey(session), new Entry(updated));
    }

    @Override
    public QueryContext applyContinuation(LexFulfillmentSession session, QueryContext enrichedContext) {
        if (session == null || !session.isComplete() || enrichedContext == null) {
            return enrichedContext;
        }
        if (!enrichedContext.continuationRequested()) {
            return enrichedContext;
        }

        Optional<SearchContext> active = findActive(session);
        if (active.isEmpty()) {
            return enrichedContext;
        }

        SearchContext ctx = active.get();
        String positionKey = ctx.positionKey();
        if (positionKey == null || positionKey.isBlank()) {
            return enrichedContext;
        }

        return enrichedContext.withPositionKey(positionKey);
    }

    @Override
    public Optional<LexIntentMapper.MappedM3Request> buildContinuationRequest(
            LexFulfillmentSession session,
            QueryContext enrichedContext
    ) {
        if (session == null || !session.isComplete() || enrichedContext == null) {
            return Optional.empty();
        }
        if (!enrichedContext.continuationRequested()) {
            return Optional.empty();
        }
        Optional<SearchContext> active = findActive(session);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        SearchContext ctx = active.get();
        String positionKey = ctx.positionKey();
        if (positionKey == null || positionKey.isBlank()) {
            return Optional.empty();
        }

        Map<String, Object> params = new LinkedHashMap<>(ctx.recordParams());
        params.put("positionkey", positionKey);
        if (enrichedContext.limit() != null && enrichedContext.limit() > 0) {
            params.put("maxrecs", enrichedContext.limit());
        }

        return Optional.of(new LexIntentMapper.MappedM3Request(
                ctx.program(),
                ctx.transaction(),
                Map.copyOf(params),
                "search"
        ));
    }

    @Override
    public void clearSession(LexFulfillmentSession session) {
        if (session == null || !session.isComplete()) {
            return;
        }
        bySessionKey.remove(sessionKey(session));
    }

    private Optional<Entry> activeEntry(LexFulfillmentSession session) {
        if (session == null || !session.isComplete()) {
            return Optional.empty();
        }
        Entry entry = bySessionKey.get(sessionKey(session));
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired(ttlSeconds)) {
            bySessionKey.remove(sessionKey(session));
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    private boolean isSearchIntent(String intentName) {
        return intentApiCatalog.find(intentName)
                .filter(def -> def.requestType() == RequestType.SEARCH)
                .isPresent();
    }

    static String buildFingerprint(LexIntentMapper.MappedM3Request mapped) {
        Object sqry = mapped.params().get("SQRY");
        return mapped.program() + "|" + mapped.transaction() + "|" + (sqry != null ? sqry : "");
    }

    private static String sessionKey(LexFulfillmentSession session) {
        return session.tenantCode() + ":" + session.userId() + ":" + session.sessionId();
    }

    private static Map<String, Object> stripPositionKey(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(params);
        copy.remove("positionkey");
        return Map.copyOf(copy);
    }

    private record Entry(SearchContext context) {
        boolean isExpired(long ttlSeconds) {
            return context.updatedAt().plusSeconds(ttlSeconds).isBefore(Instant.now());
        }
    }
}
