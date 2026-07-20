package com.ai.openai_api_service.service.repair;

import com.ai.openai_api_service.service.normalizer.FieldRole;
import com.ai.openai_api_service.service.normalizer.FieldType;
import com.ai.openai_api_service.service.normalizer.SlotValue;
import com.ai.openai_api_service.service.validation.ValidatedSlot;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class RepairSupport {

    private static final Pattern LETTERS_ONLY = Pattern.compile("^[A-Z]+$");

    private RepairSupport() {
    }

    public static String valueOf(Map<String, SlotValue> slots, String lexSlot) {
        if (slots == null || lexSlot == null) {
            return null;
        }
        SlotValue slotValue = slots.get(lexSlot);
        return slotValue != null ? slotValue.value() : null;
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean isEmptySlot(Map<String, SlotValue> slots, String lexSlot) {
        return isBlank(valueOf(slots, lexSlot));
    }

    public static Optional<IntentFieldDescriptor> firstEmptyByRole(
            RepairContext context,
            FieldRole role
    ) {
        return context.intentFields().stream()
                .filter(field -> field.definition().repairRole() == role)
                .filter(field -> isEmptySlot(context.slots(), field.lexSlotName()))
                .findFirst();
    }

    public static Optional<IntentFieldDescriptor> fieldByLexSlot(RepairContext context, String lexSlot) {
        return context.intentFields().stream()
                .filter(field -> lexSlot.equals(field.lexSlotName()))
                .findFirst();
    }

    public static Optional<ValidatedSlot> validatedFor(RepairContext context, String lexSlot) {
        return context.validatedSlots().stream()
                .filter(slot -> lexSlot.equals(slot.lexSlotName()))
                .findFirst();
    }

    public static boolean isIdentifierLike(String value) {
        if (isBlank(value)) {
            return false;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return LETTERS_ONLY.matcher(upper).matches() && upper.length() > 3;
    }

    public static boolean isInvalid(ValidatedSlot slot) {
        return slot != null && !slot.valid();
    }

    public static boolean hasNonEmptyOrderNumberSlot(RepairContext context) {
        for (IntentFieldDescriptor field : context.intentFields()) {
            if (field.definition().repairRole() != FieldRole.ORDER_NUMBER) {
                continue;
            }
            if (!isEmptySlot(context.slots(), field.lexSlotName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean utteranceMentionsOrderSlot(
            String userUtterance,
            String orderLexSlotName,
            List<SlotKeywordRegistry.KeywordMapping> keywordMappings
    ) {
        if (isBlank(userUtterance) || isBlank(orderLexSlotName) || keywordMappings == null) {
            return false;
        }
        String utterance = userUtterance.toLowerCase(Locale.ROOT);
        for (SlotKeywordRegistry.KeywordMapping mapping : keywordMappings) {
            if (!orderLexSlotName.equals(mapping.lexSlotName())) {
                continue;
            }
            Pattern pattern = Pattern.compile(
                    "\\b" + Pattern.quote(mapping.keyword()) + "\\b",
                    Pattern.CASE_INSENSITIVE
            );
            if (pattern.matcher(utterance).find()) {
                return true;
            }
        }
        return false;
    }
}
