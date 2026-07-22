package com.ai.openai_api_service.service.validation;

import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SearchFieldDefinition;
import com.ai.openai_api_service.service.LexIntentMapper;
import com.ai.openai_api_service.service.SearchFieldCatalog;
import com.ai.openai_api_service.service.normalizer.FieldDefinition;
import com.ai.openai_api_service.service.normalizer.FieldDefinitionRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates mapped M3 SEARCH requests before execution (post-build gate).
 */
@Component
public class M3RequestExecutionValidator {

    public static final String INVALID_SEARCH_MESSAGE =
            "Unable to process your request because the required search criteria could not be identified. "
                    + "Please update your request with a valid search criterion and try again.";

    public static final String ACTION_M3_SEARCH_REQUEST_INVALID = "m3_search_request_invalid";

    private static final String SQRY_CLAUSE_DELIMITER = " AND ";

    private static final Set<String> RESERVED_SQRY_VALUES = Set.of(
            "AND",
            "OR",
            "FOR",
            "THE",
            "WITH"
    );

    private final SearchFieldCatalog searchFieldCatalog;
    private final FieldDefinitionRegistry fieldDefinitionRegistry;

    public M3RequestExecutionValidator(
            SearchFieldCatalog searchFieldCatalog,
            FieldDefinitionRegistry fieldDefinitionRegistry
    ) {
        this.searchFieldCatalog = searchFieldCatalog;
        this.fieldDefinitionRegistry = fieldDefinitionRegistry;
    }

    /**
     * Returns true when the mapped request is safe to execute for the given intent.
     * READ intents and callers that skip continuation themselves should not invoke this for non-SEARCH.
     */
    public boolean isExecutable(IntentDefinition definition, LexIntentMapper.MappedM3Request mapped) {
        if (definition == null || mapped == null || definition.requestType() != RequestType.SEARCH) {
            return true;
        }
        return validateSearchRequest(definition, mapped).isEmpty();
    }

    Optional<String> validateSearchRequest(IntentDefinition definition, LexIntentMapper.MappedM3Request mapped) {
        Map<String, Object> params = mapped.params();
        if (params == null || params.isEmpty()) {
            return Optional.of("params empty");
        }

        String primaryKey = definition.primaryParameter();
        Object sqryObject = params.get(primaryKey);
        if (!(sqryObject instanceof String sqry) || sqry.isBlank()) {
            return Optional.of("missing primary search parameter");
        }

        List<SqryClause> clauses = parseSqryClauses(sqry);
        if (clauses.isEmpty()) {
            return Optional.of("no SQRY clauses");
        }

        String intentName = definition.intentName();
        for (SqryClause clause : clauses) {
            Optional<String> clauseFailure = validateClause(intentName, clause);
            if (clauseFailure.isPresent()) {
                return clauseFailure;
            }
        }
        return Optional.empty();
    }

    private Optional<String> validateClause(String intentName, SqryClause clause) {
        Optional<SearchFieldDefinition> fieldDef = searchFieldCatalog.find(intentName, clause.field());
        if (fieldDef.isEmpty()) {
            return Optional.of("unknown field: " + clause.field());
        }

        String value = clause.value();
        if (value.isBlank()) {
            return Optional.of("blank value for " + clause.field());
        }

        if (isReservedToken(value)) {
            return Optional.of("reserved token value: " + value);
        }

        if (matchesFieldKeyword(fieldDef.get(), value)) {
            return Optional.of("placeholder keyword value: " + value);
        }

        Optional<FieldDefinition> formatDef = fieldDefinitionRegistry.get(clause.field());
        if (formatDef.isPresent() && !FieldValueValidator.isValid(formatDef.get(), value)) {
            return Optional.of("invalid format for " + clause.field());
        }

        return Optional.empty();
    }

    static List<SqryClause> parseSqryClauses(String sqry) {
        if (sqry == null || sqry.isBlank()) {
            return List.of();
        }
        List<SqryClause> clauses = new ArrayList<>();
        for (String segment : sqry.split(SQRY_CLAUSE_DELIMITER)) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            int colon = segment.indexOf(':');
            if (colon <= 0 || colon >= segment.length() - 1) {
                continue;
            }
            String field = segment.substring(0, colon).trim();
            String value = segment.substring(colon + 1).trim();
            if (field.isBlank() || value.isBlank()) {
                continue;
            }
            clauses.add(new SqryClause(field, value));
        }
        return List.copyOf(clauses);
    }

    private static boolean isReservedToken(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return RESERVED_SQRY_VALUES.contains(normalizeToken(value));
    }

    private static boolean matchesFieldKeyword(SearchFieldDefinition fieldDef, String value) {
        if (fieldDef.keywords() == null || fieldDef.keywords().isEmpty()) {
            return false;
        }
        String normalizedValue = normalizeToken(value);
        for (String keyword : fieldDef.keywords()) {
            if (keyword != null && normalizeToken(keyword).equals(normalizedValue)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeToken(String text) {
        return text.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    record SqryClause(String field, String value) {
    }
}
