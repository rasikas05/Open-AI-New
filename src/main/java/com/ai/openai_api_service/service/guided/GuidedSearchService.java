package com.ai.openai_api_service.service.guided;

import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.GuidedSearchPhase;
import com.ai.openai_api_service.model.GuidedSearchState;
import com.ai.openai_api_service.model.LexFulfillmentOutcome;
import com.ai.openai_api_service.model.LexFulfillmentSession;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SearchFieldDefinition;
import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.LexFulfillmentService;
import com.ai.openai_api_service.service.SearchFieldCatalog;
import com.ai.openai_api_service.service.normalizer.FieldDefinition;
import com.ai.openai_api_service.service.normalizer.FieldDefinitionRegistry;
import com.ai.openai_api_service.service.normalizer.SlotNormalizer;
import com.ai.openai_api_service.service.normalizer.SlotValue;
import com.ai.openai_api_service.service.validation.FieldValueValidator;
import com.ai.openai_api_service.service.validation.SearchCriteriaValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Metadata-driven guided collector for SEARCH intents with zero criteria.
 */
@Service
public class GuidedSearchService {

    public static final String ACTION_SELECT_FIELD = "guided_search_select_field";
    public static final String ACTION_COLLECT_VALUE = "guided_search_collect_value";
    public static final String ACTION_CANCELLED = "guided_search_cancelled";

    private static final String CANCEL_HINT = "Type cancel to exit.";
    private static final Set<String> CANCEL_WORDS = Set.of("cancel", "stop", "abort");
    private static final Pattern NEW_SEARCH_PATTERN = Pattern.compile("^(?:actually\\s+)?(?:search|show|find|list|get)\\b", Pattern.CASE_INSENSITIVE);

    private final SearchFieldCatalog searchFieldCatalog;
    private final IntentApiCatalog intentApiCatalog;
    private final SlotNormalizer slotNormalizer;
    private final FieldDefinitionRegistry fieldDefinitionRegistry;
    private final LexFulfillmentService lexFulfillmentService;
    private final InMemoryGuidedSearchSessionService guidedSearchSessionService;

    public GuidedSearchService(
            SearchFieldCatalog searchFieldCatalog,
            IntentApiCatalog intentApiCatalog,
            SlotNormalizer slotNormalizer,
            FieldDefinitionRegistry fieldDefinitionRegistry,
            LexFulfillmentService lexFulfillmentService,
            InMemoryGuidedSearchSessionService guidedSearchSessionService
    ) {
        this.searchFieldCatalog = searchFieldCatalog;
        this.intentApiCatalog = intentApiCatalog;
        this.slotNormalizer = slotNormalizer;
        this.fieldDefinitionRegistry = fieldDefinitionRegistry;
        this.lexFulfillmentService = lexFulfillmentService;
        this.guidedSearchSessionService = guidedSearchSessionService;
    }

    public ChatResponse start(String intentName, LexFulfillmentSession session) {
        if (!isSearchIntent(intentName)) {
            throw new IllegalArgumentException("Guided search only supports SEARCH intents: " + intentName);
        }
        if (guidedFields(intentName).isEmpty()) {
            ChatResponse empty = new ChatResponse(
                    "Unable to process the request because no search criteria were provided.",
                    false
            );
            empty.setActionTaken("search_criteria_missing");
            empty.setLexIntent(intentName);
            return empty;
        }
        guidedSearchSessionService.put(session, GuidedSearchState.selectField(intentName));
        return buildMenuResponse(intentName, null);
    }

    public GuidedTurnResult handleTurn(
            LexFulfillmentSession session,
            GuidedSearchState state,
            String userText
    ) {
        if (state == null) {
            return GuidedTurnResult.abandoned();
        }
        String trimmed = userText != null ? userText.trim() : "";
        if (isCancel(trimmed)) {
            guidedSearchSessionService.clear(session);
            return GuidedTurnResult.response(buildCancelResponse(state.intentName()));
        }
        if (state.phase() == GuidedSearchPhase.SELECT_FIELD) {
            return handleSelectField(session, state, trimmed);
        }
        return handleCollectValue(session, state, trimmed);
    }

    public ChatResponse buildCancelResponse(String intentName) {
        ChatResponse cancelled = new ChatResponse("Guided search cancelled. How else can I help?", false);
        cancelled.setActionTaken(ACTION_CANCELLED);
        cancelled.setLexIntent(intentName);
        return cancelled;
    }

    private GuidedTurnResult handleSelectField(
            LexFulfillmentSession session,
            GuidedSearchState state,
            String trimmedInput
    ) {
        if (trimmedInput.isBlank()) {
            return GuidedTurnResult.response(buildMenuResponse(
                    state.intentName(),
                    "Please choose a field number or name."
            ));
        }
        List<SearchFieldDefinition> fields = guidedFields(state.intentName());
        Optional<SearchFieldDefinition> selected = resolveFieldChoice(fields, trimmedInput);
        if (selected.isEmpty()) {
            if (shouldAbandonToLex(trimmedInput)) {
                guidedSearchSessionService.clear(session);
                return GuidedTurnResult.abandoned();
            }
            return GuidedTurnResult.response(buildMenuResponse(
                    state.intentName(),
                    "I couldn't match that to a searchable field."
            ));
        }
        SearchFieldDefinition field = selected.get();
        guidedSearchSessionService.put(
                session,
                GuidedSearchState.collectValue(
                        state.intentName(),
                        field.m3Field(),
                        field.lexSlotName(),
                        state.collectedCriteria()
                )
        );
        return GuidedTurnResult.response(buildCollectValueResponse(state.intentName(), field, null));
    }

    private GuidedTurnResult handleCollectValue(
            LexFulfillmentSession session,
            GuidedSearchState state,
            String trimmedInput
    ) {
        if (trimmedInput.isBlank()) {
            SearchFieldDefinition field = findSelectedField(state);
            if (field == null) {
                guidedSearchSessionService.put(session, GuidedSearchState.selectField(state.intentName()));
                return GuidedTurnResult.response(buildMenuResponse(
                        state.intentName(),
                        "Let's choose a field first."
                ));
            }
            return GuidedTurnResult.response(buildCollectValueResponse(
                    state.intentName(),
                    field,
                    "Please provide a value."
            ));
        }

        SearchFieldDefinition field = findSelectedField(state);
        if (field == null) {
            guidedSearchSessionService.put(session, GuidedSearchState.selectField(state.intentName()));
            return GuidedTurnResult.response(buildMenuResponse(
                    state.intentName(),
                    "Let's choose a field first."
            ));
        }

        ValidationResult validation = normalizeAndValidate(state.intentName(), field, trimmedInput);
        if (!validation.valid()) {
            if (shouldAbandonToLex(trimmedInput)) {
                guidedSearchSessionService.clear(session);
                return GuidedTurnResult.abandoned();
            }
            return GuidedTurnResult.response(buildCollectValueResponse(
                    state.intentName(),
                    field,
                    validation.reason()
            ));
        }

        Map<String, String> slots = new LinkedHashMap<>(state.collectedCriteria());
        slots.put(field.lexSlotName(), validation.normalizedValue());
        LexFulfillmentOutcome outcome = lexFulfillmentService.fulfillSearch(
                state.intentName(),
                Map.copyOf(slots),
                trimmedInput,
                session
        );
        ChatResponse response = outcome.response();
        if (response != null && shouldClearAfterFulfillment(outcome)) {
            guidedSearchSessionService.clear(session);
            return GuidedTurnResult.response(response);
        }

        guidedSearchSessionService.put(
                session,
                GuidedSearchState.collectValue(state.intentName(), field.m3Field(), field.lexSlotName(), slots)
        );
        return GuidedTurnResult.response(buildCollectValueResponse(
                state.intentName(),
                field,
                "I couldn't use that value yet. Please try again."
        ));
    }

    private List<SearchFieldDefinition> guidedFields(String intentName) {
        List<SearchFieldDefinition> all = searchFieldCatalog.fieldsFor(intentName);
        List<SearchFieldDefinition> result = new ArrayList<>();
        for (SearchFieldDefinition field : all) {
            if (field.description() != null && !field.description().isBlank()
                    && field.lexSlotName() != null && !field.lexSlotName().isBlank()) {
                result.add(field);
            }
        }
        result.sort(Comparator.comparingInt(field ->
                field.displayOrder() > 0 ? field.displayOrder() : Integer.MAX_VALUE));
        return result;
    }

    private ChatResponse buildMenuResponse(String intentName, String prefix) {
        List<SearchFieldDefinition> fields = guidedFields(intentName);
        StringBuilder body = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            body.append(prefix.trim()).append("\n\n");
        }
        body.append("I can search ").append(humanizeIntent(intentName)).append(".\n")
                .append("Please select a search field.\n\n");
        for (int i = 0; i < fields.size(); i++) {
            body.append(i + 1).append(". ").append(fields.get(i).description()).append("\n");
        }
        body.append("\nType the number or field name.\n")
                .append(CANCEL_HINT);

        ChatResponse response = new ChatResponse(body.toString().trim(), false);
        response.setActionTaken(ACTION_SELECT_FIELD);
        response.setLexIntent(intentName);
        response.setCollectingTool(intentName);
        response.setNextFieldOptional(false);
        return response;
    }

    private Optional<SearchFieldDefinition> resolveFieldChoice(List<SearchFieldDefinition> fields, String input) {
        try {
            int index = Integer.parseInt(input);
            if (index >= 1 && index <= fields.size()) {
                return Optional.of(fields.get(index - 1));
            }
        } catch (NumberFormatException ignored) {
            // fall through to alias/name matching
        }

        String normalized = normalizeText(input);
        for (SearchFieldDefinition field : fields) {
            if (normalized.equals(normalizeText(field.description()))) {
                return Optional.of(field);
            }
            for (String alias : field.aliases()) {
                if (normalized.equals(normalizeText(alias))) {
                    return Optional.of(field);
                }
            }
            for (String keyword : field.keywords()) {
                if (normalized.equals(normalizeText(keyword))) {
                    return Optional.of(field);
                }
            }
        }
        return Optional.empty();
    }

    private ChatResponse buildCollectValueResponse(String intentName, SearchFieldDefinition field, String prefix) {
        String prompt = field.prompt() != null && !field.prompt().isBlank()
                ? field.prompt()
                : "Please enter " + field.description() + ".";
        StringBuilder body = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            body.append(prefix.trim()).append("\n\n");
        }
        body.append(prompt);
        if (field.example() != null && !field.example().isBlank()) {
            body.append("\nExample: ").append(field.example());
        }
        body.append("\n").append(CANCEL_HINT);

        ChatResponse response = new ChatResponse(body.toString().trim(), false);
        response.setActionTaken(ACTION_COLLECT_VALUE);
        response.setLexIntent(intentName);
        response.setCollectingTool(intentName);
        response.setNextField(field.m3Field());
        response.setNextFieldOptional(false);
        return response;
    }

    private SearchFieldDefinition findSelectedField(GuidedSearchState state) {
        if (state.selectedM3Field() == null || state.selectedM3Field().isBlank()) {
            return null;
        }
        return searchFieldCatalog.find(state.intentName(), state.selectedM3Field()).orElse(null);
    }

    private ValidationResult normalizeAndValidate(String intentName, SearchFieldDefinition field, String rawValue) {
        Map<String, SlotValue> normalizedSlots = slotNormalizer.normalize(
                intentName,
                SlotNormalizer.toSlotValues(Map.of(field.lexSlotName(), rawValue))
        );
        String normalizedValue = SlotNormalizer.toStringMap(normalizedSlots)
                .getOrDefault(field.lexSlotName(), rawValue != null ? rawValue.trim() : "");

        Optional<FieldDefinition> fieldDefinition = fieldDefinitionRegistry.get(field.m3Field());
        if (fieldDefinition.isPresent()) {
            FieldValueValidator.ValidationOutcome outcome =
                    FieldValueValidator.validate(fieldDefinition.get(), normalizedValue);
            if (!outcome.passed()) {
                return ValidationResult.invalid("Invalid value: " + outcome.reason());
            }
        }
        if (normalizedValue.isBlank()) {
            return ValidationResult.invalid("Value cannot be empty.");
        }
        return ValidationResult.valid(normalizedValue);
    }

    private static boolean shouldClearAfterFulfillment(LexFulfillmentOutcome outcome) {
        if (SearchCriteriaValidator.hasSearchableCriteria(outcome.searchCriteria())) {
            return true;
        }
        ChatResponse response = outcome.response();
        return response != null
                && "search".equals(response.getActionTaken())
                && response.getM3Request() != null;
    }

    private static boolean shouldAbandonToLex(String trimmedInput) {
        if (trimmedInput == null || trimmedInput.isBlank()) {
            return false;
        }
        if (trimmedInput.split("\\s+").length < 2) {
            return false;
        }
        return NEW_SEARCH_PATTERN.matcher(trimmedInput).find();
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private boolean isSearchIntent(String intentName) {
        return intentApiCatalog.find(intentName)
                .filter(def -> def.requestType() == RequestType.SEARCH)
                .isPresent();
    }

    public static boolean isCancel(String trimmed) {
        if (trimmed == null || trimmed.isBlank()) {
            return false;
        }
        return CANCEL_WORDS.contains(trimmed.toLowerCase(Locale.ROOT));
    }

    static String humanizeIntent(String intentName) {
        if (intentName == null || intentName.isBlank()) {
            return "records";
        }
        String name = intentName.startsWith("Search") ? intentName.substring("Search".length()) : intentName;
        String spaced = name.replaceAll("([a-z])([A-Z])", "$1 $2").trim();
        if (spaced.isBlank()) {
            return "records";
        }
        return spaced.toLowerCase(Locale.ROOT);
    }

    public record GuidedTurnResult(ChatResponse response, boolean abandonToLex) {
        static GuidedTurnResult response(ChatResponse response) {
            return new GuidedTurnResult(response, false);
        }

        static GuidedTurnResult abandoned() {
            return new GuidedTurnResult(null, true);
        }
    }

    private record ValidationResult(boolean valid, String normalizedValue, String reason) {
        static ValidationResult valid(String value) {
            return new ValidationResult(true, value, null);
        }

        static ValidationResult invalid(String reason) {
            return new ValidationResult(false, null, reason);
        }
    }
}
