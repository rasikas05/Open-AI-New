package com.ai.openai_api_service.model;

import java.time.Instant;

/**
 * Immutable marker that a Lex dialog is awaiting a slot value for this session.
 * Lex owns intent/slot/dialog state; Spring only tracks that the next turn must skip Python routing.
 */
public record PendingLexMarker(Instant updatedAt) {
    public PendingLexMarker {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }
}
