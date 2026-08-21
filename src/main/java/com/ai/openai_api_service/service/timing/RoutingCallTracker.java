package com.ai.openai_api_service.service.timing;

/**
 * Per-thread flags for whether Lex or RAG HTTP actually ran on this comprehend turn,
 * plus measured Python {@code /route} HTTP ms for the one-line [TIMING] summary.
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

    /**
     * Accumulate Python {@code /route} HTTP responseTime already measured by the client.
     */
    public static void addPythonRouteMs(long responseTimeMs) {
        Flags flags = FLAGS.get();
        if (flags != null && responseTimeMs > 0L) {
            flags.pythonRouteMs += responseTimeMs;
        }
    }

    public static long pythonRouteMs() {
        Flags flags = FLAGS.get();
        return flags != null ? flags.pythonRouteMs : 0L;
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
        private long pythonRouteMs;
    }
}
