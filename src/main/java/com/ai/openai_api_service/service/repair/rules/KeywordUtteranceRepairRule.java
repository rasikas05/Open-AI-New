package com.ai.openai_api_service.service.repair.rules;

import com.ai.openai_api_service.service.repair.RepairAction;
import com.ai.openai_api_service.service.repair.RepairContext;
import com.ai.openai_api_service.service.repair.RepairOutcome;
import com.ai.openai_api_service.service.repair.RepairSupport;
import com.ai.openai_api_service.service.repair.SlotKeywordRegistry;
import com.ai.openai_api_service.service.repair.SlotRepairRule;
import com.ai.openai_api_service.service.validation.ValidatedSlot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KeywordUtteranceRepairRule implements SlotRepairRule {

    private static final double CONFIDENCE = 0.9;

    private static final Set<String> CAPTURE_STOPWORDS = Set.of(
            "for", "of", "with", "on", "in", "at", "to", "from", "the", "a", "an",
            "customer", "order", "orders", "status"
    );

    private static final Pattern LEADING_TRAILING_PUNCTUATION = Pattern.compile(
            "^[\\.,;:!?'\"]+|[\\.,;:!?'\"]+$"
    );

    private final SlotKeywordRegistry keywordRegistry;

    public KeywordUtteranceRepairRule(SlotKeywordRegistry keywordRegistry) {
        this.keywordRegistry = keywordRegistry;
    }

    @Override
    public Optional<RepairOutcome> apply(RepairContext context) {
        if (context.userUtterance() == null || context.userUtterance().isBlank()) {
            return Optional.empty();
        }

        String utterance = context.userUtterance().toLowerCase(Locale.ROOT);
        Set<String> reservedKeywordTexts = keywordRegistry.keywordTextsForIntent(context.intentName());
        List<RepairAction> actions = new ArrayList<>();
        List<ConsumedSpan> consumedKeywordSpans = new ArrayList<>();
        Set<String> slotsFilled = new HashSet<>();

        for (SlotKeywordRegistry.KeywordMapping mapping : keywordRegistry.keywordsForIntent(context.intentName())) {
            if (slotsFilled.contains(mapping.lexSlotName())) {
                continue;
            }

            Pattern pattern = Pattern.compile(
                    "\\b" + Pattern.quote(mapping.keyword()) + "\\s+([A-Za-z0-9][A-Za-z0-9\\-]*)",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = pattern.matcher(utterance);
            if (!matcher.find()) {
                continue;
            }

            int keywordStart = matcher.start();
            int keywordEnd = keywordStart + mapping.keyword().length();
            if (isInsideConsumedSpan(keywordStart, consumedKeywordSpans)) {
                continue;
            }

            String extracted = matcher.group(1).trim();
            if (extracted.isBlank()) {
                continue;
            }

            String normalizedCapture = normalizeForStopwordCheck(extracted);
            if (normalizedCapture.isBlank() || CAPTURE_STOPWORDS.contains(normalizedCapture)) {
                continue;
            }

            if (reservedKeywordTexts.contains(normalizedCapture)) {
                continue;
            }

            String current = RepairSupport.valueOf(context.slots(), mapping.lexSlotName());
            boolean shouldApply = RepairSupport.isBlank(current)
                    || RepairSupport.validatedFor(context, mapping.lexSlotName())
                    .map(ValidatedSlot::valid)
                    .orElse(true) == false;

            if (!shouldApply) {
                continue;
            }

            actions.add(new RepairAction(
                    mapping.lexSlotName(),
                    current,
                    extracted.toUpperCase(Locale.ROOT),
                    "KeywordUtterance from user text",
                    CONFIDENCE
            ));
            consumedKeywordSpans.add(new ConsumedSpan(keywordStart, keywordEnd));
            slotsFilled.add(mapping.lexSlotName());
        }

        return actions.isEmpty() ? Optional.empty() : Optional.of(new RepairOutcome(List.copyOf(actions)));
    }

    private static String normalizeForStopwordCheck(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String trimmed = token.trim().toLowerCase(Locale.ROOT);
        return LEADING_TRAILING_PUNCTUATION.matcher(trimmed).replaceAll("");
    }

    private static boolean isInsideConsumedSpan(int keywordStart, List<ConsumedSpan> consumedSpans) {
        for (ConsumedSpan span : consumedSpans) {
            if (keywordStart >= span.start() && keywordStart < span.end()) {
                return true;
            }
        }
        return false;
    }

    private record ConsumedSpan(int start, int end) {
    }
}
