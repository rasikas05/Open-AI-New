package com.ai.openai_api_service.service.protection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable request-scoped protection state (Decision #21, #26–#28).
 * Sole mutation owner for protected text; LLM stages read via {@link #textForLlm()} only.
 */
public final class ProtectionSession {

    private String originalText;
    private String piiSanitizedText;
    private String businessProtectedText;
    private boolean protectionEnabled;
    private final Map<String, String> replacementMap = new LinkedHashMap<>();
    private List<ProtectionAction> businessActions = List.of();
    private String replyBeforeRestore;
    private String finalResponse;

    private ProtectionSession() {
    }

    /** Phase 7B RAG: start from original text before Business → PII. */
    public static ProtectionSession fromOriginal(String originalText, boolean protectionEnabled) {
        ProtectionSession session = new ProtectionSession();
        session.originalText = originalText;
        session.protectionEnabled = protectionEnabled;
        return session;
    }

    /**
     * Legacy Phase 7A factory: input already PII-sanitized upstream; BIP may still run.
     * Does not set {@code piiSanitizedText} so {@link #textForLlm()} can surface business output.
     * @deprecated Prefer {@link #fromOriginal(String, boolean)} for RAG orchestration.
     */
    @Deprecated
    public static ProtectionSession fromPiiSanitized(String piiSanitizedText) {
        ProtectionSession session = new ProtectionSession();
        session.originalText = piiSanitizedText;
        session.protectionEnabled = true;
        return session;
    }

    public static ProtectionSession of(String originalText, String piiSanitizedText) {
        ProtectionSession session = new ProtectionSession();
        session.originalText = originalText;
        session.piiSanitizedText = piiSanitizedText;
        session.protectionEnabled = true;
        return session;
    }

    public String originalText() {
        return originalText;
    }

    public String piiSanitizedText() {
        return piiSanitizedText;
    }

    public String businessProtectedText() {
        return businessProtectedText;
    }

    public boolean protectionEnabled() {
        return protectionEnabled;
    }

    public void setProtectionEnabled(boolean protectionEnabled) {
        this.protectionEnabled = protectionEnabled;
    }

    public Map<String, String> replacementMap() {
        return Collections.unmodifiableMap(replacementMap);
    }

    /** Business protection actions (Decision #28 mutation surface). */
    public List<ProtectionAction> actions() {
        return businessActions;
    }

    public List<ProtectionAction> businessActions() {
        return businessActions;
    }

    public String replyBeforeRestore() {
        return replyBeforeRestore;
    }

    public String finalResponse() {
        return finalResponse;
    }

    /**
     * Text fed to business detect/apply.
     * Phase 7B: always the original (Business before PII).
     */
    public String inputForBusinessProtection() {
        return originalText;
    }

    /**
     * Canonical OpenAI-bound text (Decision #27).
     * After the RAG pipeline: prefer {@code piiSanitizedText}, else {@code businessProtectedText}, else original.
     * When BIP flag is off, PII still runs on original and this returns the PII result.
     */
    public String textForLlm() {
        if (piiSanitizedText != null) {
            return piiSanitizedText;
        }
        if (businessProtectedText != null) {
            return businessProtectedText;
        }
        return originalText;
    }

    /** BIP-only mutation (Decision #28). */
    public void applyBusinessResult(ProtectedText result) {
        if (result == null) {
            return;
        }
        this.businessProtectedText = result.text();
        this.businessActions = result.actions() == null ? List.of() : List.copyOf(result.actions());
        this.replacementMap.clear();
        if (result.replacementMap() != null) {
            this.replacementMap.putAll(result.replacementMap());
        }
    }

    /** When BIP is off or clears: business text equals original for PII input chaining. */
    public void clearBusinessResult() {
        this.businessProtectedText = null;
        this.businessActions = List.of();
        this.replacementMap.clear();
    }

    /** PII-only mutation (Decision #28). */
    public void applyPiiSanitizedText(String piiSanitizedText) {
        this.piiSanitizedText = piiSanitizedText;
    }

    /** 7B.2 restore artifacts (response-side; does not change textForLlm). */
    public void applyRestoredReply(String replyBeforeRestore, String finalResponse) {
        this.replyBeforeRestore = replyBeforeRestore;
        this.finalResponse = finalResponse;
    }

    public boolean businessProtectionApplied() {
        return businessProtectedText != null
                && originalText != null
                && !originalText.equals(businessProtectedText);
    }
}
