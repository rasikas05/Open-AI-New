package com.ai.openai_api_service.service.timing;

/**
 * Per-request nested stage timings under {@code pii}, {@code persistence}, and {@code quota}.
 * Sub-buckets must not be added again into wall total / residual (they nest under parents).
 * Lifecycle: {@link #begin()} only resets ThreadLocal state (safe before checkBeforeChat);
 * {@link #clear()} with {@link RoutingCallTracker#clear()}.
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

    public static void addCheckTenantMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.checkTenantMs += ms;
        }
    }

    public static void addCheckQuotaMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.checkQuotaMs += ms;
        }
    }

    public static void addUsageTenantMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.usageTenantMs += ms;
        }
    }

    public static void addUsageQuotaLookupMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.usageQuotaLookupMs += ms;
        }
    }

    public static void addUsageUpdateMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.usageUpdateMs += ms;
        }
    }

    public static void addUsageBalanceLookupMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.usageBalanceLookupMs += ms;
        }
    }

    public static void addUsageTokenTxnMs(long ms) {
        Splits s = SPLITS.get();
        if (s != null && ms > 0L) {
            s.usageTokenTxnMs += ms;
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

    public static long checkTenantMs() {
        Splits s = SPLITS.get();
        return s != null ? s.checkTenantMs : 0L;
    }

    public static long checkQuotaMs() {
        Splits s = SPLITS.get();
        return s != null ? s.checkQuotaMs : 0L;
    }

    public static long usageTenantMs() {
        Splits s = SPLITS.get();
        return s != null ? s.usageTenantMs : 0L;
    }

    public static long usageQuotaLookupMs() {
        Splits s = SPLITS.get();
        return s != null ? s.usageQuotaLookupMs : 0L;
    }

    public static long usageUpdateMs() {
        Splits s = SPLITS.get();
        return s != null ? s.usageUpdateMs : 0L;
    }

    public static long usageBalanceLookupMs() {
        Splits s = SPLITS.get();
        return s != null ? s.usageBalanceLookupMs : 0L;
    }

    public static long usageTokenTxnMs() {
        Splits s = SPLITS.get();
        return s != null ? s.usageTokenTxnMs : 0L;
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
        private long checkTenantMs;
        private long checkQuotaMs;
        private long usageTenantMs;
        private long usageQuotaLookupMs;
        private long usageUpdateMs;
        private long usageBalanceLookupMs;
        private long usageTokenTxnMs;
    }
}
