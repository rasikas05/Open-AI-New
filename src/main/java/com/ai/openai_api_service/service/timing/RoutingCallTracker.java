package com.ai.openai_api_service.service.timing;

/**
 * Per-thread flags for whether Lex or RAG HTTP actually ran on this comprehend turn.
 */
public final class RoutingCallTracker {

    private static final ThreadLocal<Flags> FLAGS = new ThreadLocal<>();

    private RoutingCallTracker() {
    }

    public static void begin() {
        FLAGS.set(new Flags());
    }

    public static void clear() {
        FLAGS.remove();
    }

    public static void markLexCalled() {
        Flags flags = FLAGS.get();
        if (flags != null) {
            flags.lexCalled = true;
        }
    }

    public static void markRagCalled() {
        Flags flags = FLAGS.get();
        if (flags != null) {
            flags.ragCalled = true;
        }
    }

    public static boolean lexCalled() {
        Flags flags = FLAGS.get();
        return flags != null && flags.lexCalled;
    }

    public static boolean ragCalled() {
        Flags flags = FLAGS.get();
        return flags != null && flags.ragCalled;
    }

    private static final class Flags {
        private boolean lexCalled;
        private boolean ragCalled;
    }
}
