package com.ai.openai_api_service.service.timing;

/**
 * Per-request nested stage timings under {@code pii} and {@code persistence}.
 * Sub-buckets must not be added again into wall total / residual (they nest under parents).
 * Lifecycle: {@link #begin()} with {@link RoutingCallTracker#begin()}, {@link #clear()} with clear.
 */
public final class ChatStageSplitTracker {

    private static final ThreadLocal<Splits> SPLITS = new ThreadLocal<>();

    private ChatStageSplitTracker() {
    }

    public static void begin() {
        SPLITS.set(new Splits());
    }

    public static void clear() {
        SPLITS.remove();
    }

    public static void addBusinessProtectMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.businessProtectMs += ms;
        }
    }

    public static void addComprehendMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.comprehendMs += ms;
        }
    }

    public static void addPresidioMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.presidioMs += ms;
        }
    }

    public static void addTenantLookupMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.tenantLookupMs += ms;
        }
    }

    public static void addUserLookupMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.userLookupMs += ms;
        }
    }

    public static void addSessionLookupMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.sessionLookupMs += ms;
        }
    }

    public static void addTitleMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.titleMs += ms;
        }
    }

    public static void addSessionSaveMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.sessionSaveMs += ms;
        }
    }

    public static void addRequestLogSaveMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.requestLogSaveMs += ms;
        }
    }

    public static long businessProtectMs() {
        Splits s = SPLITS.get();
        return s != null ? s.businessProtectMs : 0L;
    }

    public static long comprehendMs() {
        Splits s = SPLITS.get();
        return s != null ? s.comprehendMs : 0L;
    }

    public static long presidioMs() {
        Splits s = SPLITS.get();
        return s != null ? s.presidioMs : 0L;
    }

    public static long tenantLookupMs() {
        Splits s = SPLITS.get();
        return s != null ? s.tenantLookupMs : 0L;
    }

    public static long userLookupMs() {
        Splits s = SPLITS.get();
        return s != null ? s.userLookupMs : 0L;
    }

    public static long sessionLookupMs() {
        Splits s = SPLITS.get();
        return s != null ? s.sessionLookupMs : 0L;
    }

    public static long titleMs() {
        Splits s = SPLITS.get();
        return s != null ? s.titleMs : 0L;
    }

    public static long sessionSaveMs() {
        Splits s = SPLITS.get();
        return s != null ? s.sessionSaveMs : 0L;
    }

    public static long requestLogSaveMs() {
        Splits s = SPLITS.get();
        return s != null ? s.requestLogSaveMs : 0L;
    }

    private static final class Splits {
        private long businessProtectMs;
        private long comprehendMs;
        private long presidioMs;
        private long tenantLookupMs;
        private long userLookupMs;
        private long sessionLookupMs;
        private long titleMs;
        private long sessionSaveMs;
        private long requestLogSaveMs;
    }
}
