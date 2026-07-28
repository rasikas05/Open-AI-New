package com.ai.openai_api_service.service.lex;

import com.ai.openai_api_service.model.PendingLexMarker;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPendingLexSessionServiceTest {

    private static final String LEX_SESSION = "tenant1:user1:session1";

    @Test
    void markPending_thenGet_returnsPresent() {
        InMemoryPendingLexSessionService service = new InMemoryPendingLexSessionService(3600);

        service.markPending(LEX_SESSION);

        Optional<PendingLexMarker> marker = service.get(LEX_SESSION);
        assertTrue(marker.isPresent());
    }

    @Test
    void clear_removesMarker() {
        InMemoryPendingLexSessionService service = new InMemoryPendingLexSessionService(3600);
        service.markPending(LEX_SESSION);

        service.clear(LEX_SESSION);

        assertTrue(service.get(LEX_SESSION).isEmpty());
    }

    @Test
    void get_afterTtlExpiry_returnsEmptyAndRemovesStale() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-28T10:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        InMemoryPendingLexSessionService service = new InMemoryPendingLexSessionService(60, clock);
        service.markPending(LEX_SESSION);
        assertTrue(service.get(LEX_SESSION).isPresent());

        now.set(Instant.parse("2026-07-28T10:02:00Z"));

        assertTrue(service.get(LEX_SESSION).isEmpty());
        assertTrue(service.get(LEX_SESSION).isEmpty());
    }

    @Test
    void markPending_twice_refreshesUpdatedAt() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-28T10:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        InMemoryPendingLexSessionService service = new InMemoryPendingLexSessionService(3600, clock);

        service.markPending(LEX_SESSION);
        Instant first = service.get(LEX_SESSION).orElseThrow().updatedAt();

        now.set(Instant.parse("2026-07-28T10:00:05Z"));
        service.markPending(LEX_SESSION);
        Instant second = service.get(LEX_SESSION).orElseThrow().updatedAt();

        assertNotEquals(first, second);
        assertTrue(second.isAfter(first));
        assertEquals(Instant.parse("2026-07-28T10:00:05Z"), second);
    }
}
