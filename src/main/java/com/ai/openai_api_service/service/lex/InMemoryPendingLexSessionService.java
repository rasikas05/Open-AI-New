package com.ai.openai_api_service.service.lex;

import com.ai.openai_api_service.model.PendingLexMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory pending-Lex gate state. Separate from Guided Search session state.
 * Keyed by Lex session id ({@code tenant:user:sessionId}).
 */
@Service
public class InMemoryPendingLexSessionService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPendingLexSessionService.class);

    private final long ttlSeconds;
    private final Clock clock;
    private final ConcurrentHashMap<String, PendingLexMarker> byLexSessionId = new ConcurrentHashMap<>();

    @Autowired
    public InMemoryPendingLexSessionService(
            @Value("${pending-lex.ttl-seconds:3600}") long ttlSeconds
    ) {
        this.ttlSeconds = ttlSeconds;
        this.clock = Clock.systemUTC();
    }

    public void markPending(String lexSessionId) {
        if (lexSessionId == null || lexSessionId.isBlank()) {
            return;
        }
        byLexSessionId.put(lexSessionId, new PendingLexMarker(clock.instant()));
    }

    public void clear(String lexSessionId) {
        if (lexSessionId == null || lexSessionId.isBlank()) {
            return;
        }
        byLexSessionId.remove(lexSessionId);
    }

    /**
     * Returns the marker if present and not TTL-expired. Removes stale entries on read.
     */
    public Optional<PendingLexMarker> get(String lexSessionId) {
        if (lexSessionId == null || lexSessionId.isBlank()) {
            return Optional.empty();
        }
        PendingLexMarker marker = byLexSessionId.get(lexSessionId);
        if (marker == null) {
            return Optional.empty();
        }
        if (isExpired(marker)) {
            byLexSessionId.remove(lexSessionId, marker);
            log.info(
                    "Pending Lex TTL expired; cleared marker. lexSessionId='{}' updatedAt={}",
                    lexSessionId,
                    marker.updatedAt()
            );
            return Optional.empty();
        }
        return Optional.of(marker);
    }

    private boolean isExpired(PendingLexMarker marker) {
        return marker.updatedAt().plusSeconds(ttlSeconds).isBefore(clock.instant());
    }
}
