package com.ai.openai_api_service.service.slots;

import com.ai.openai_api_service.service.normalizer.SlotValue;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GenericSlotInterpreter {

    private final GenericSlotInterpretationCatalog interpretationCatalog;

    public GenericSlotInterpreter(GenericSlotInterpretationCatalog interpretationCatalog) {
        this.interpretationCatalog = interpretationCatalog;
    }

    public Map<String, SlotValue> interpret(String intentName, Map<String, SlotValue> slots) {
        if (slots == null || slots.isEmpty() || intentName == null || intentName.isBlank()) {
            return slots != null ? Map.copyOf(slots) : Map.of();
        }

        Map<String, SlotValue> interpreted = new LinkedHashMap<>(slots);
        List<GenericSlotInterpretationCatalog.InterpretationRule> rules = interpretationCatalog.rulesFor(intentName);
        for (GenericSlotInterpretationCatalog.InterpretationRule rule : rules) {
            if (rule.type() == GenericSlotInterpretationCatalog.InterpretationType.RANGE) {
                applyRangeRule(interpreted, rule);
            } else if (rule.type() == GenericSlotInterpretationCatalog.InterpretationType.ALIAS) {
                applyAliasRule(interpreted, rule);
            }
        }

        return Map.copyOf(interpreted);
    }

    private static void applyRangeRule(
            Map<String, SlotValue> slots,
            GenericSlotInterpretationCatalog.InterpretationRule rule
    ) {
        SlotValue generic = slots.get(rule.genericSlot());
        String genericValue = valueOf(generic);
        if (isBlank(genericValue)) {
            return;
        }

        String upperSlot = rule.primaryTargetSlot();
        String lowerSlot = rule.secondaryTargetSlot();
        String upperValue = valueOf(slots.get(upperSlot));
        String lowerValue = valueOf(slots.get(lowerSlot));

        if (!isBlank(upperValue) && !isBlank(lowerValue)) {
            slots.remove(rule.genericSlot());
            return;
        }

        if (isBlank(upperValue) && !isBlank(lowerValue)) {
            slots.put(upperSlot, new SlotValue(genericValue));
            slots.remove(rule.genericSlot());
            return;
        }

        if (!isBlank(upperValue) && isBlank(lowerValue)) {
            slots.put(lowerSlot, new SlotValue(genericValue));
            slots.remove(rule.genericSlot());
            return;
        }

        // Default RANGE behavior: generic Status maps to upper bound when both are absent.
        slots.put(upperSlot, new SlotValue(genericValue));
        slots.remove(rule.genericSlot());
    }

    private static void applyAliasRule(
            Map<String, SlotValue> slots,
            GenericSlotInterpretationCatalog.InterpretationRule rule
    ) {
        SlotValue generic = slots.get(rule.genericSlot());
        String genericValue = valueOf(generic);
        if (isBlank(genericValue)) {
            return;
        }

        String targetSlot = rule.primaryTargetSlot();
        String targetValue = valueOf(slots.get(targetSlot));
        if (isBlank(targetValue)) {
            slots.put(targetSlot, new SlotValue(genericValue));
        }
        slots.remove(rule.genericSlot());
    }

    private static String valueOf(SlotValue value) {
        return value != null ? value.value() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
