package com.ai.openai_api_service.service.repair.rules;

import com.ai.openai_api_service.service.normalizer.FieldRole;
import com.ai.openai_api_service.service.normalizer.FieldType;
import com.ai.openai_api_service.service.repair.IntentFieldDescriptor;
import com.ai.openai_api_service.service.repair.RepairAction;
import com.ai.openai_api_service.service.repair.RepairContext;
import com.ai.openai_api_service.service.repair.RepairOutcome;
import com.ai.openai_api_service.service.repair.RepairSupport;
import com.ai.openai_api_service.service.repair.SlotRepairRule;
import com.ai.openai_api_service.service.validation.ValidatedSlot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class MisassignmentRepairRule implements SlotRepairRule {

    private static final double CONFIDENCE = 0.75;

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

            FieldType sourceType = sourceField.get().definition().fieldType();
            if (sourceType != FieldType.CODE && sourceType != FieldType.STATUS) {
                continue;
            }

            if (!RepairSupport.isIdentifierLike(validated.value())) {
                continue;
            }

            FieldRole targetRole = RepairSupport.isIdentifierLike(validated.value())
                    ? FieldRole.PARTY
                    : FieldRole.PERSON;

            Optional<IntentFieldDescriptor> target = RepairSupport.firstEmptyByRole(context, targetRole);
            if (target.isEmpty() && targetRole == FieldRole.PARTY) {
                target = RepairSupport.firstEmptyByRole(context, FieldRole.PERSON);
            }
            if (target.isEmpty()) {
                continue;
            }

            actions.add(new RepairAction(
                    target.get().lexSlotName(),
                    RepairSupport.valueOf(context.slots(), target.get().lexSlotName()),
                    validated.value(),
                    "Misassignment from " + validated.lexSlotName(),
                    CONFIDENCE
            ));
            actions.add(new RepairAction(
                    validated.lexSlotName(),
                    validated.value(),
                    null,
                    "Cleared misassigned " + validated.lexSlotName(),
                    CONFIDENCE
            ));
        }

        return actions.isEmpty() ? Optional.empty() : Optional.of(new RepairOutcome(List.copyOf(actions)));
    }
}
