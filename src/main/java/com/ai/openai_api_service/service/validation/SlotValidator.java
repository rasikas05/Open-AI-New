package com.ai.openai_api_service.service.validation;

import com.ai.openai_api_service.service.SearchFieldCatalog;
import com.ai.openai_api_service.service.normalizer.FieldDefinition;
import com.ai.openai_api_service.service.normalizer.FieldDefinitionRegistry;
import com.ai.openai_api_service.service.normalizer.FieldType;
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

            ValidationResult result = validateValue(fieldDefinition.get(), value);
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

    private static ValidationResult validateValue(FieldDefinition definition, String value) {
        if (!definition.allowSpaces() && value.contains(" ")) {
            return ValidationResult.fail("Spaces are not allowed");
        }

        if (definition.expectedLength() != null && value.length() != definition.expectedLength()) {
            return ValidationResult.fail(
                    "Expected length=" + definition.expectedLength() + ", received=" + value.length()
            );
        }

        if (definition.minLength() != null && value.length() < definition.minLength()) {
            return ValidationResult.fail(
                    "Minimum length=" + definition.minLength() + ", received=" + value.length()
            );
        }

        if (definition.maxLength() != null && value.length() > definition.maxLength()) {
            return ValidationResult.fail(
                    "Maximum length=" + definition.maxLength() + ", received=" + value.length()
            );
        }

        if (definition.regexPattern() != null && !definition.regexPattern().matcher(value).matches()) {
            return ValidationResult.fail("Value does not match expected pattern for " + definition.fieldType());
        }

        return ValidationResult.ok();
    }

    private record ValidationResult(boolean passed, String reason) {
        static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        static ValidationResult fail(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
