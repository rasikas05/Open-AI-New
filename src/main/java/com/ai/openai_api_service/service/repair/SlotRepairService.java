package com.ai.openai_api_service.service.repair;

import com.ai.openai_api_service.model.SearchFieldDefinition;
import com.ai.openai_api_service.service.SearchFieldCatalog;
import com.ai.openai_api_service.service.normalizer.FieldDefinition;
import com.ai.openai_api_service.service.normalizer.FieldDefinitionRegistry;
import com.ai.openai_api_service.service.normalizer.SlotValue;
import com.ai.openai_api_service.service.repair.rules.KeywordUtteranceRepairRule;
import com.ai.openai_api_service.service.repair.rules.MergedStatusSplitRule;
import com.ai.openai_api_service.service.repair.rules.MergedTextSplitRule;
import com.ai.openai_api_service.service.repair.rules.MisassignmentRepairRule;
import com.ai.openai_api_service.service.validation.SlotValidator;
import com.ai.openai_api_service.service.validation.ValidatedSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SlotRepairService {

    private static final Logger log = LoggerFactory.getLogger(SlotRepairService.class);

    static final int MAX_REPAIR_ITERATIONS = 3;
    static final double MIN_CONFIDENCE = 0.5;

    private final SlotValidator slotValidator;
    private final SearchFieldCatalog searchFieldCatalog;
    private final FieldDefinitionRegistry fieldDefinitionRegistry;
    private final List<SlotRepairRule> repairRules;

    public SlotRepairService(
            SlotValidator slotValidator,
            SearchFieldCatalog searchFieldCatalog,
            FieldDefinitionRegistry fieldDefinitionRegistry,
            KeywordUtteranceRepairRule keywordUtteranceRepairRule,
            MisassignmentRepairRule misassignmentRepairRule,
            MergedStatusSplitRule mergedStatusSplitRule,
            MergedTextSplitRule mergedTextSplitRule
    ) {
        this.slotValidator = slotValidator;
        this.searchFieldCatalog = searchFieldCatalog;
        this.fieldDefinitionRegistry = fieldDefinitionRegistry;
        this.repairRules = List.of(
                keywordUtteranceRepairRule,
                misassignmentRepairRule,
                mergedStatusSplitRule,
                mergedTextSplitRule
        );
    }

    public Map<String, SlotValue> repair(String intentName, String userUtterance, Map<String, SlotValue> normalizedSlots) {
        Map<String, SlotValue> current = normalizedSlots != null
                ? new LinkedHashMap<>(normalizedSlots)
                : new LinkedHashMap<>();

        log.info("Lex slots: {}", formatSlots(current));

        for (int iteration = 1; iteration <= MAX_REPAIR_ITERATIONS; iteration++) {
            List<ValidatedSlot> validated = slotValidator.validate(intentName, current);
            logValidation(validated);

            if (SlotValidator.allValid(validated)) {
                break;
            }

            RepairContext context = new RepairContext(
                    intentName,
                    userUtterance,
                    Map.copyOf(current),
                    validated,
                    buildIntentFields(intentName)
            );

            List<RepairAction> actions = collectActions(context);
            if (actions.isEmpty()) {
                break;
            }

            current = applyActions(current, actions);
            log.info(
                    "Repair [iter={}, confidence={}]: {}",
                    iteration,
                    averageConfidence(actions),
                    formatActions(actions)
            );
        }

        log.info("Final slots: {}", formatSlots(current));
        return Map.copyOf(current);
    }

    private List<RepairAction> collectActions(RepairContext context) {
        Map<String, RepairAction> bestBySlot = new LinkedHashMap<>();

        for (SlotRepairRule rule : repairRules) {
            rule.apply(context).ifPresent(outcome -> {
                for (RepairAction action : outcome.actions()) {
                    if (action.confidence() < MIN_CONFIDENCE) {
                        continue;
                    }
                    RepairAction existing = bestBySlot.get(action.lexSlotName());
                    if (existing == null || action.confidence() > existing.confidence()) {
                        bestBySlot.put(action.lexSlotName(), action);
                    }
                }
            });
        }

        return List.copyOf(bestBySlot.values());
    }

    private static Map<String, SlotValue> applyActions(Map<String, SlotValue> slots, List<RepairAction> actions) {
        Map<String, SlotValue> updated = new LinkedHashMap<>(slots);
        for (RepairAction action : actions) {
            if (action.newValue() == null) {
                updated.remove(action.lexSlotName());
            } else {
                updated.put(action.lexSlotName(), new SlotValue(action.newValue()));
            }
        }
        return updated;
    }

    private List<IntentFieldDescriptor> buildIntentFields(String intentName) {
        List<IntentFieldDescriptor> fields = new ArrayList<>();
        for (SearchFieldDefinition field : searchFieldCatalog.fieldsFor(intentName)) {
            if (field.lexSlotName() == null || field.lexSlotName().isBlank()) {
                continue;
            }
            FieldDefinition definition = fieldDefinitionRegistry.get(field.m3Field()).orElse(null);
            if (definition == null) {
                continue;
            }
            fields.add(new IntentFieldDescriptor(field.lexSlotName(), field.m3Field(), definition));
        }
        return List.copyOf(fields);
    }

    private static void logValidation(List<ValidatedSlot> validated) {
        for (ValidatedSlot slot : validated) {
            if (!slot.valid()) {
                log.info("Validation: {} INVALID — {}", slot.lexSlotName(), slot.reason());
            }
        }
    }

    private static String formatSlots(Map<String, SlotValue> slots) {
        return slots.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + (entry.getValue() != null ? entry.getValue().value() : null))
                .collect(Collectors.joining(", "));
    }

    private static String formatActions(List<RepairAction> actions) {
        return actions.stream()
                .map(action -> action.lexSlotName() + "=" + action.newValue())
                .collect(Collectors.joining(", "));
    }

    private static double averageConfidence(List<RepairAction> actions) {
        return actions.stream().mapToDouble(RepairAction::confidence).average().orElse(0.0);
    }
}
