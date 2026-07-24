package com.ai.openai_api_service.service.guided;

import com.ai.openai_api_service.model.GuidedSearchState;
import com.ai.openai_api_service.model.LexFulfillmentSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped guided search state (in-memory). Separate from search pagination context.
 */
@Service
public class InMemoryGuidedSearchSessionService {

    private final long ttlSeconds;
    private final ConcurrentHashMap<String, Entry> bySessionKey = new ConcurrentHashMap<>();

    public InMemoryGuidedSearchSessionService(
            @Value("${guided-search.ttl-seconds:3600}") long ttlSeconds
    ) {
        this.ttlSeconds = ttlSeconds;
    }

    public Optional<GuidedSearchState> find(LexFulfillmentSession session) {
        return activeEntry(session).map(Entry::state);
    }

    public void put(LexFulfillmentSession session, GuidedSearchState state) {
        if (session == null || !session.isComplete() || state == null) {
            return;
        }
        bySessionKey.put(sessionKey(session), new Entry(state));
    }

    public void clear(LexFulfillmentSession session) {
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

    private static String sessionKey(LexFulfillmentSession session) {
        return session.tenantCode() + ":" + session.userId() + ":" + session.sessionId();
    }

    private record Entry(GuidedSearchState state) {
        boolean isExpired(long ttlSeconds) {
            return state.updatedAt().plusSeconds(ttlSeconds).isBefore(Instant.now());
        }
    }
}
