package com.ai.openai_api_service.service.lex;

import com.ai.openai_api_service.model.PendingLexMarker;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
    void get_afterTtlExpiry_returnsEmptyAndRemovesStale() throws InterruptedException {
        InMemoryPendingLexSessionService service = new InMemoryPendingLexSessionService(0);
        service.markPending(LEX_SESSION);
        assertTrue(service.get(LEX_SESSION).isPresent());

        Thread.sleep(5);

        assertTrue(service.get(LEX_SESSION).isEmpty());
        assertTrue(service.get(LEX_SESSION).isEmpty());
    }

    @Test
    void markPending_twice_refreshesUpdatedAt() throws InterruptedException {
        InMemoryPendingLexSessionService service = new InMemoryPendingLexSessionService(3600);

        service.markPending(LEX_SESSION);
        var first = service.get(LEX_SESSION).orElseThrow().updatedAt();

        Thread.sleep(2);
        service.markPending(LEX_SESSION);
        var second = service.get(LEX_SESSION).orElseThrow().updatedAt();

        assertNotEquals(first, second);
        assertTrue(second.isAfter(first));
    }
}
