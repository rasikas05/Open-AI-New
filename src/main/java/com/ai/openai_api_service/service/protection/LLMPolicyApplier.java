package com.ai.openai_api_service.service.protection;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure policy application. Does not query the catalog.
 * REVIEW is treated as BLOCK until Decision Log promotes the row.
 */
@Component
public class LLMPolicyApplier {

    private final PlaceholderFormatter placeholderFormatter;

    public LLMPolicyApplier(PlaceholderFormatter placeholderFormatter) {
        this.placeholderFormatter = placeholderFormatter;
    }

    public ProtectedText apply(String text, List<SpanClassification> spans, ProtectionContext context) {
        if (text == null) {
            return new ProtectedText(null, List.of(), Map.of());
        }
        if (spans == null || spans.isEmpty()) {
            return new ProtectedText(text, List.of(), Map.of());
        }

        List<PendingAction> pending = new ArrayList<>();
        for (SpanClassification item : spans) {
            DetectedSpan span = item.span();
            if (span.start() < 0 || span.end() > text.length() || span.start() >= span.end()) {
                continue;
            }
            LlmExposurePolicy effective = resolveEffectivePolicy(item, context);
            String placeholderType = item.classification()
                    .map(FieldClassification::placeholderType)
                    .orElse(null);
            String originalValue = text.substring(span.start(), span.end());
            pending.add(new PendingAction(span, effective, placeholderType, originalValue));
        }

        Map<String, Integer> typeTotals = new HashMap<>();
        for (PendingAction p : pending) {
            if (needsToken(p.policy())) {
                String key = tokenKey(p.placeholderType(), p.policy());
                typeTotals.merge(key, 1, Integer::sum);
            }
        }
        Map<String, Integer> typeSeq = new HashMap<>();
        Map<String, String> replacementMap = new LinkedHashMap<>();
        List<ProtectionAction> actions = new ArrayList<>();

        List<PendingAction> byStart = new ArrayList<>(pending);
        byStart.sort(Comparator.comparingInt((PendingAction p) -> p.span().start()));

        List<Replacement> replacements = new ArrayList<>();
        for (PendingAction p : byStart) {
            if (!needsToken(p.policy())) {
                actions.add(new ProtectionAction(p.span(), p.policy(), null, null, p.originalValue()));
                continue;
            }
            String typeKey = tokenKey(p.placeholderType(), p.policy());
            int total = typeTotals.getOrDefault(typeKey, 1);
            int seq = typeSeq.merge(typeKey, 1, Integer::sum);
            String baseType = p.placeholderType() != null && !p.placeholderType().isBlank()
                    ? p.placeholderType()
                    : typeKey;
            String formatInput = total > 1 ? baseType + "_" + seq : baseType;
            String token = placeholderFormatter.format(formatInput);
            replacementMap.put(token, p.originalValue());
            actions.add(new ProtectionAction(p.span(), p.policy(), p.placeholderType(), token, p.originalValue()));
            replacements.add(new Replacement(p.span().start(), p.span().end(), token));
        }

        replacements.sort(Comparator.comparingInt(Replacement::start).reversed());
        StringBuilder mutable = new StringBuilder(text);
        for (Replacement r : replacements) {
            mutable.replace(r.start(), r.end(), r.token());
        }

        return new ProtectedText(mutable.toString(), actions, replacementMap);
    }

    private static boolean needsToken(LlmExposurePolicy policy) {
        return policy == LlmExposurePolicy.MASK
                || policy == LlmExposurePolicy.REPLACE
                || policy == LlmExposurePolicy.BLOCK
                || policy == LlmExposurePolicy.REVIEW;
    }

    private static String tokenKey(String placeholderType, LlmExposurePolicy policy) {
        if (placeholderType != null && !placeholderType.isBlank()) {
            return placeholderType.trim().replaceAll("\\s+", "_").toUpperCase();
        }
        if (policy == LlmExposurePolicy.BLOCK || policy == LlmExposurePolicy.REVIEW) {
            return "BLOCKED";
        }
        return "REDACTED";
    }

    LlmExposurePolicy resolveEffectivePolicy(SpanClassification item, ProtectionContext context) {
        if (item.classification().isEmpty()) {
            return LlmExposurePolicy.BLOCK;
        }
        FieldClassification classification = item.classification().get();
        LlmExposurePolicy catalogPolicy = classification.llmExposurePolicy();
        if (catalogPolicy == LlmExposurePolicy.REVIEW) {
            return LlmExposurePolicy.BLOCK;
        }

        ProtectionPurpose purpose = context != null ? context.purpose() : ProtectionPurpose.ANSWER;
        if (purpose == ProtectionPurpose.REWRITE) {
            InformationCategory category = classification.category();
            if (category == InformationCategory.BDI || category == InformationCategory.OMD) {
                return LlmExposurePolicy.ALLOW;
            }
        }
        return catalogPolicy;
    }

    private record PendingAction(
            DetectedSpan span,
            LlmExposurePolicy policy,
            String placeholderType,
            String originalValue
    ) {
    }

    private record Replacement(int start, int end, String token) {
    }
}
