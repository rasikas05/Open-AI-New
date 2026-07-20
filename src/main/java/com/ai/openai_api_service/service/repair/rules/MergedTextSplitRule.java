package com.ai.openai_api_service.service.repair.rules;

import com.ai.openai_api_service.service.repair.RepairAction;
import com.ai.openai_api_service.service.repair.RepairContext;
import com.ai.openai_api_service.service.repair.RepairOutcome;
import com.ai.openai_api_service.service.repair.RepairSupport;
import com.ai.openai_api_service.service.repair.SlotKeywordRegistry;
import com.ai.openai_api_service.service.repair.SlotRepairRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class MergedTextSplitRule implements SlotRepairRule {

    private static final double CONFIDENCE = 0.7;

    private final SlotKeywordRegistry keywordRegistry;

    public MergedTextSplitRule(SlotKeywordRegistry keywordRegistry) {
        this.keywordRegistry = keywordRegistry;
    }

    @Override
    public Optional<RepairOutcome> apply(RepairContext context) {
        List<RepairAction> actions = new ArrayList<>();

        for (var entry : context.slots().entrySet()) {
            String lexSlot = entry.getKey();
            String value = RepairSupport.valueOf(context.slots(), lexSlot);
            if (RepairSupport.isBlank(value) || !value.contains(" ")) {
                continue;
            }

            String lowerValue = value.toLowerCase(Locale.ROOT);
            for (SlotKeywordRegistry.KeywordMapping mapping : keywordRegistry.keywordsForIntent(context.intentName())) {
                if (mapping.lexSlotName().equals(lexSlot)) {
                    continue;
                }

                int keywordIndex = lowerValue.indexOf(mapping.keyword());
                if (keywordIndex < 0) {
                    continue;
                }

                String before = value.substring(0, keywordIndex).trim();
                String after = value.substring(keywordIndex + mapping.keyword().length()).trim();

                if (!before.isBlank() && RepairSupport.isEmptySlot(context.slots(), mapping.lexSlotName())) {
                    actions.add(new RepairAction(
                            mapping.lexSlotName(),
                            null,
                            before.toUpperCase(Locale.ROOT),
                            "MergedTextSplit keyword " + mapping.keyword(),
                            CONFIDENCE
                    ));
                    actions.add(new RepairAction(
                            lexSlot,
                            value,
                            null,
                            "Cleared merged slot " + lexSlot,
                            CONFIDENCE
                    ));
                    break;
                }

                if (!after.isBlank() && RepairSupport.isEmptySlot(context.slots(), mapping.lexSlotName())) {
                    actions.add(new RepairAction(
                            mapping.lexSlotName(),
                            null,
                            after.toUpperCase(Locale.ROOT),
                            "MergedTextSplit keyword " + mapping.keyword(),
                            CONFIDENCE
                    ));
                    actions.add(new RepairAction(
                            lexSlot,
                            value,
                            before.isBlank() ? null : before.toUpperCase(Locale.ROOT),
                            "Trimmed merged slot " + lexSlot,
                            CONFIDENCE
                    ));
                    break;
                }
            }
        }

        return actions.isEmpty() ? Optional.empty() : Optional.of(new RepairOutcome(List.copyOf(actions)));
    }
}
