package com.ai.openai_api_service.service.repair.rules;

import com.ai.openai_api_service.service.normalizer.FieldRole;
import com.ai.openai_api_service.service.repair.IntentFieldDescriptor;
import com.ai.openai_api_service.service.repair.RepairAction;
import com.ai.openai_api_service.service.repair.RepairContext;
import com.ai.openai_api_service.service.repair.RepairOutcome;
import com.ai.openai_api_service.service.repair.RepairSupport;
import com.ai.openai_api_service.service.repair.SlotKeywordRegistry;
import com.ai.openai_api_service.service.repair.SlotRepairRule;
import com.ai.openai_api_service.service.validation.ValidatedSlot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class MergedStatusSplitRule implements SlotRepairRule {

    private static final double CONFIDENCE = 0.8;
    private static final int MAX_IMPLICIT_ORDER_PART_LENGTH = 5;
    private static final Pattern DIGITS_ONLY = Pattern.compile("^[0-9]+$");

    private final SlotKeywordRegistry keywordRegistry;

    public MergedStatusSplitRule(SlotKeywordRegistry keywordRegistry) {
        this.keywordRegistry = keywordRegistry;
    }

    @Override
    public Optional<RepairOutcome> apply(RepairContext context) {
        List<RepairAction> actions = new ArrayList<>();

        for (ValidatedSlot validated : context.validatedSlots()) {
            if (validated.valid()) {
                continue;
            }

            Optional<IntentFieldDescriptor> sourceField = RepairSupport.fieldByLexSlot(context, validated.lexSlotName());
            if (sourceField.isEmpty()) {
                continue;
            }

            if (sourceField.get().definition().repairRole() != FieldRole.STATUS) {
                continue;
            }

            Integer expectedLength = sourceField.get().definition().expectedLength();
            if (expectedLength == null || validated.value().length() <= expectedLength) {
                continue;
            }

            String value = validated.value();
            if (!DIGITS_ONLY.matcher(value).matches()) {
                continue;
            }

            String statusPart = value.substring(value.length() - expectedLength);
            String orderPart = value.substring(0, value.length() - expectedLength);
            if (orderPart.isBlank()) {
                continue;
            }

            Optional<IntentFieldDescriptor> orderTarget = RepairSupport.firstEmptyByRole(context, FieldRole.ORDER_NUMBER);
            if (orderTarget.isEmpty()) {
                continue;
            }

            actions.add(new RepairAction(
                    validated.lexSlotName(),
                    validated.value(),
                    statusPart,
                    "StatusSplit tail",
                    CONFIDENCE
            ));

            if (shouldAssignOrderPart(context, orderTarget.get(), orderPart)) {
                actions.add(new RepairAction(
                        orderTarget.get().lexSlotName(),
                        RepairSupport.valueOf(context.slots(), orderTarget.get().lexSlotName()),
                        orderPart,
                        "StatusSplit head to order number",
                        CONFIDENCE
                ));
            }
        }

        return actions.isEmpty() ? Optional.empty() : Optional.of(new RepairOutcome(List.copyOf(actions)));
    }

    private boolean shouldAssignOrderPart(
            RepairContext context,
            IntentFieldDescriptor orderTarget,
            String orderPart
    ) {
        if (RepairSupport.hasNonEmptyOrderNumberSlot(context)) {
            return true;
        }
        if (RepairSupport.utteranceMentionsOrderSlot(
                context.userUtterance(),
                orderTarget.lexSlotName(),
                keywordRegistry.keywordsForIntent(context.intentName())
        )) {
            return true;
        }
        return orderPart.length() <= MAX_IMPLICIT_ORDER_PART_LENGTH && DIGITS_ONLY.matcher(orderPart).matches();
    }
}
