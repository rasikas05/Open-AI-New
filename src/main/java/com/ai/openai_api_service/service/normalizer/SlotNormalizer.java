package com.ai.openai_api_service.service.normalizer;

import com.ai.openai_api_service.service.SearchFieldCatalog;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class SlotNormalizer {

    private final SearchFieldCatalog searchFieldCatalog;
    private final FieldDefinitionRegistry fieldDefinitionRegistry;

    public SlotNormalizer(SearchFieldCatalog searchFieldCatalog, FieldDefinitionRegistry fieldDefinitionRegistry) {
        this.searchFieldCatalog = searchFieldCatalog;
        this.fieldDefinitionRegistry = fieldDefinitionRegistry;
    }

    public Map<String, SlotValue> normalize(String intentName, Map<String, SlotValue> slots) {
        if (slots == null) {
            return Map.of();
        }
        if (intentName == null || intentName.isBlank()) {
            return copyOf(slots);
        }

        Map<String, SlotValue> result = new LinkedHashMap<>();
        for (Map.Entry<String, SlotValue> entry : slots.entrySet()) {
            String lexSlot = entry.getKey();
            if (lexSlot == null) {
                continue;
            }

            SlotValue slotValue = entry.getValue();
            String raw = slotValue != null ? slotValue.value() : null;
            if (raw == null || raw.isBlank()) {
                result.put(lexSlot, slotValue);
                continue;
            }

            Optional<String> m3Field = searchFieldCatalog.findBySlot(intentName, lexSlot)
                    .map(definition -> definition.m3Field());

            if (m3Field.isEmpty()) {
                result.put(lexSlot, new SlotValue(raw.trim()));
                continue;
            }

            Optional<FieldDefinition> fieldDefinition = fieldDefinitionRegistry.get(m3Field.get());
            if (fieldDefinition.isEmpty()) {
                result.put(lexSlot, new SlotValue(raw.trim()));
                continue;
            }

            result.put(lexSlot, applyDefinition(fieldDefinition.get(), raw));
        }
        return Map.copyOf(result);
    }

    public static Map<String, SlotValue> toSlotValues(Map<String, String> slots) {
        if (slots == null) {
            return Map.of();
        }
        Map<String, SlotValue> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : slots.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(entry.getKey(), new SlotValue(entry.getValue()));
        }
        return result;
    }

    public static Map<String, String> toStringMap(Map<String, SlotValue> slots) {
        if (slots == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, SlotValue> entry : slots.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            SlotValue value = entry.getValue();
            result.put(entry.getKey(), value != null ? value.value() : null);
        }
        return result;
    }

    private static SlotValue applyDefinition(FieldDefinition definition, String raw) {
        String value = raw.trim();
        if (definition.caseStrategy() == CaseStrategy.UPPER) {
            value = value.toUpperCase(Locale.ROOT);
        }
        if (definition.formatter() != null) {
            value = definition.formatter().apply(value);
        }
        return new SlotValue(value);
    }

    private static Map<String, SlotValue> copyOf(Map<String, SlotValue> slots) {
        Map<String, SlotValue> copy = new LinkedHashMap<>();
        for (Map.Entry<String, SlotValue> entry : slots.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(copy);
    }
}
