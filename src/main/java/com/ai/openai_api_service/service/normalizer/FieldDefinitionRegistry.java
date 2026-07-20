package com.ai.openai_api_service.service.normalizer;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
public class FieldDefinitionRegistry {

    private static final Pattern ALNUM = Pattern.compile("^[A-Z0-9]+$");
    private static final Pattern DIGITS = Pattern.compile("^[0-9]+$");
    private static final Pattern DATE_LIKE = Pattern.compile("^\\d{4}-?\\d{2}-?\\d{2}$");

    private static final Function<String, String> COLLAPSE_WHITESPACE = value -> value.replaceAll("\\s+", "");

    private final Map<String, FieldDefinition> definitionsByField;

    public FieldDefinitionRegistry() {
        Map<String, FieldDefinition> definitions = new LinkedHashMap<>();

        seedIdentifier(definitions, "CUNO", FieldRole.PARTY, 10);
        seedIdentifier(definitions, "ORNO", FieldRole.ORDER_NUMBER, 10);
        seedIdentifier(definitions, "SUNO", FieldRole.PARTY, 10);
        seedIdentifier(definitions, "SMCD", FieldRole.PERSON, 10);
        seedIdentifier(definitions, "RESP", FieldRole.PERSON, 10);
        seedIdentifier(definitions, "BUYE", FieldRole.PERSON, 10);
        seedIdentifier(definitions, "PUNO", FieldRole.ORDER_NUMBER, 10);
        seedIdentifier(definitions, "MFNO", FieldRole.ORDER_NUMBER, 10);
        seedIdentifier(definitions, "PRNO", FieldRole.GENERIC, 15);
        seedIdentifier(definitions, "TRNR", FieldRole.ORDER_NUMBER, 10);
        seedIdentifier(definitions, "PYNO", FieldRole.PARTY, 10);

        seedCode(definitions, "FACI", FieldRole.FACILITY, 3);
        seedCode(definitions, "WHLO", FieldRole.WAREHOUSE, 3);
        seedCode(definitions, "DIVI", FieldRole.DIVISION, 3);

        seedStatus(definitions, "ORST", 2);
        seedStatus(definitions, "ORSL", 2);
        seedStatus(definitions, "PUST", 2);
        seedStatus(definitions, "PUSL", 2);
        seedStatus(definitions, "TRSH", 2);
        seedStatus(definitions, "TRSL", 2);
        seedStatus(definitions, "WHST", 2);

        seedDate(definitions, "ORDT");
        seedDate(definitions, "PUDT");
        seedDate(definitions, "STDT");
        seedDate(definitions, "FIDT");
        seedDate(definitions, "RIDT");

        this.definitionsByField = Map.copyOf(definitions);
    }

    public Optional<FieldDefinition> get(String field) {
        if (field == null || field.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitionsByField.get(field));
    }

    private static void seedIdentifier(
            Map<String, FieldDefinition> definitions,
            String field,
            FieldRole role,
            int maxLength
    ) {
        definitions.put(field, new FieldDefinition(
                field,
                CaseStrategy.UPPER,
                FieldType.IDENTIFIER,
                role,
                ALNUM,
                null,
                null,
                maxLength,
                false,
                COLLAPSE_WHITESPACE
        ));
    }

    private static void seedCode(Map<String, FieldDefinition> definitions, String field, FieldRole role, int expectedLength) {
        definitions.put(field, new FieldDefinition(
                field,
                CaseStrategy.UPPER,
                FieldType.CODE,
                role,
                ALNUM,
                expectedLength,
                null,
                null,
                false,
                COLLAPSE_WHITESPACE
        ));
    }

    private static void seedStatus(Map<String, FieldDefinition> definitions, String field, int expectedLength) {
        definitions.put(field, new FieldDefinition(
                field,
                CaseStrategy.NONE,
                FieldType.STATUS,
                FieldRole.STATUS,
                DIGITS,
                expectedLength,
                null,
                null,
                false,
                COLLAPSE_WHITESPACE
        ));
    }

    private static void seedDate(Map<String, FieldDefinition> definitions, String field) {
        definitions.put(field, new FieldDefinition(
                field,
                CaseStrategy.NONE,
                FieldType.DATE,
                FieldRole.DATE,
                DATE_LIKE,
                null,
                null,
                null,
                false,
                null
        ));
    }
}
