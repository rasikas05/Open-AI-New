package com.ai.openai_api_service.service.timing;

import java.time.Instant;

/**
 * Mutable timing snapshot attached to the HTTP request so the comprehend filter
 * can add responseSerializeMs and log an honest end-to-end residual.
 */
public class ComprehendChatTimingSnapshot {
    private Instant filterStart;
    private Instant serviceStart;
    private Instant serviceEnd;

    private long piiMs;
    private long pythonMs;
    private long routeMs;
    private long rewriteMs;
    private long retrievalSpringMs;
    private long retrievalPythonMs;
    private long groundedMs;
    private long gapFillMs;
    private long generalGptMs;
    private long persistenceMs;
    private long quotaMs;
    private long suggestionsMs;
    private long liveHistoryMs;
    private long restoreMs;
    private long preRetrievalGlueMs;
    private long serviceTotalMs;

    public Instant getFilterStart() {
        return filterStart;
    }

    public void setFilterStart(Instant filterStart) {
        this.filterStart = filterStart;
    }

    public Instant getServiceStart() {
        return serviceStart;
    }

    public void setServiceStart(Instant serviceStart) {
        this.serviceStart = serviceStart;
    }

    public Instant getServiceEnd() {
        return serviceEnd;
    }

    public void setServiceEnd(Instant serviceEnd) {
        this.serviceEnd = serviceEnd;
    }

    public long getPiiMs() {
        return piiMs;
    }

    public void setPiiMs(long piiMs) {
        this.piiMs = piiMs;
    }

    public long getPythonMs() {
        return pythonMs;
    }

    public void setPythonMs(long pythonMs) {
        this.pythonMs = pythonMs;
    }

    public long getRouteMs() {
        return routeMs;
    }

    /**
     * Planner OpenAI ms only (excludes PII). Named routeMs for filter snapshot compatibility.
     */
    public void setRouteMs(long routeMs) {
        this.routeMs = routeMs;
    }

    public long getRewriteMs() {
        return rewriteMs;
    }

    public void setRewriteMs(long rewriteMs) {
        this.rewriteMs = rewriteMs;
    }

    public long getRetrievalSpringMs() {
        return retrievalSpringMs;
    }

    public void setRetrievalSpringMs(long retrievalSpringMs) {
        this.retrievalSpringMs = retrievalSpringMs;
    }

    public long getRetrievalPythonMs() {
        return retrievalPythonMs;
    }

    public void setRetrievalPythonMs(long retrievalPythonMs) {
        this.retrievalPythonMs = retrievalPythonMs;
    }

    public long getGroundedMs() {
        return groundedMs;
    }

    public void setGroundedMs(long groundedMs) {
        this.groundedMs = groundedMs;
    }

    public long getGapFillMs() {
        return gapFillMs;
    }

    public void setGapFillMs(long gapFillMs) {
        this.gapFillMs = gapFillMs;
    }

    public long getGeneralGptMs() {
        return generalGptMs;
    }

    public void setGeneralGptMs(long generalGptMs) {
        this.generalGptMs = generalGptMs;
    }

    public long getPersistenceMs() {
        return persistenceMs;
    }

    public void setPersistenceMs(long persistenceMs) {
        this.persistenceMs = persistenceMs;
    }

    public long getQuotaMs() {
        return quotaMs;
    }

    public void setQuotaMs(long quotaMs) {
        this.quotaMs = quotaMs;
    }

    public long getSuggestionsMs() {
        return suggestionsMs;
    }

    public void setSuggestionsMs(long suggestionsMs) {
        this.suggestionsMs = suggestionsMs;
    }

    public long getLiveHistoryMs() {
        return liveHistoryMs;
    }

    public void setLiveHistoryMs(long liveHistoryMs) {
        this.liveHistoryMs = liveHistoryMs;
    }

    public long getRestoreMs() {
        return restoreMs;
    }

    public void setRestoreMs(long restoreMs) {
        this.restoreMs = restoreMs;
    }

    public long getPreRetrievalGlueMs() {
        return preRetrievalGlueMs;
    }

    public void setPreRetrievalGlueMs(long preRetrievalGlueMs) {
        this.preRetrievalGlueMs = preRetrievalGlueMs;
    }

    public long getServiceTotalMs() {
        return serviceTotalMs;
    }

    public void setServiceTotalMs(long serviceTotalMs) {
        this.serviceTotalMs = serviceTotalMs;
    }

    public long measuredSumWithoutSerialize() {
        return pythonMs
                + piiMs
                + routeMs
                + rewriteMs
                + retrievalSpringMs
                + groundedMs
                + gapFillMs
                + generalGptMs
                + quotaMs
                + persistenceMs
                + suggestionsMs
                + liveHistoryMs
                + restoreMs
                + preRetrievalGlueMs;
    }
}
