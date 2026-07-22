package com.ai.openai_api_service.service.validation;

import com.ai.openai_api_service.service.SearchFieldCatalog;
import com.ai.openai_api_service.service.normalizer.FieldDefinition;
import com.ai.openai_api_service.service.normalizer.FieldDefinitionRegistry;
import com.ai.openai_api_service.service.normalizer.SlotValue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SlotValidator {

    private final SearchFieldCatalog searchFieldCatalog;
    private final FieldDefinitionRegistry fieldDefinitionRegistry;

    public SlotValidator(SearchFieldCatalog searchFieldCatalog, FieldDefinitionRegistry fieldDefinitionRegistry) {
        this.searchFieldCatalog = searchFieldCatalog;
        this.fieldDefinitionRegistry = fieldDefinitionRegistry;
    }

    public List<ValidatedSlot> validate(String intentName, Map<String, SlotValue> normalizedSlots) {
        if (normalizedSlots == null || normalizedSlots.isEmpty()) {
            return List.of();
        }

        List<ValidatedSlot> results = new ArrayList<>();
        for (Map.Entry<String, SlotValue> entry : normalizedSlots.entrySet()) {
            String lexSlot = entry.getKey();
            if (lexSlot == null) {
                continue;
            }

            SlotValue slotValue = entry.getValue();
            String value = slotValue != null ? slotValue.value() : null;
            if (value == null || value.isBlank()) {
                continue;
            }

            Optional<String> m3Field = searchFieldCatalog.findBySlot(intentName, lexSlot)
                    .map(definition -> definition.m3Field());

            if (m3Field.isEmpty()) {
                results.add(new ValidatedSlot(lexSlot, null, value, true, null));
                continue;
            }

            Optional<FieldDefinition> fieldDefinition = fieldDefinitionRegistry.get(m3Field.get());
            if (fieldDefinition.isEmpty()) {
                results.add(new ValidatedSlot(lexSlot, m3Field.get(), value, true, null));
                continue;
            }

            FieldValueValidator.ValidationOutcome result =
                    FieldValueValidator.validate(fieldDefinition.get(), value);
            results.add(new ValidatedSlot(
                    lexSlot,
                    m3Field.get(),
                    value,
                    result.passed(),
                    result.reason()
            ));
        }
        return List.copyOf(results);
    }

    public static boolean allValid(List<ValidatedSlot> validatedSlots) {
        if (validatedSlots == null || validatedSlots.isEmpty()) {
            return true;
        }
        return validatedSlots.stream().allMatch(ValidatedSlot::valid);
    }
}
