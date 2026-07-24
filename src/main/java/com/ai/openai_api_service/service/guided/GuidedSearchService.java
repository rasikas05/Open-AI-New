package com.ai.openai_api_service.service.guided;

import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.GuidedSearchPhase;
import com.ai.openai_api_service.model.GuidedSearchState;
import com.ai.openai_api_service.model.LexFulfillmentSession;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SearchFieldDefinition;
import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.SearchFieldCatalog;
import com.ai.openai_api_service.service.normalizer.FieldDefinition;
import com.ai.openai_api_service.service.normalizer.FieldDefinitionRegistry;
import com.ai.openai_api_service.service.validation.FieldValueValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Catalog-driven guided search menu and turn handling for SEARCH intents.
 * Does not fulfill M3 itself — callers resume via LexFulfillmentService.fulfillSearch.
 */
@Service
public class GuidedSearchService {

    public static final String ACTION_SELECT_FIELD = "guided_search_select_field";
    public static final String ACTION_COLLECT_VALUE = "guided_search_collect_value";
    public static final String ACTION_CANCELLED = "guided_search_cancelled";
    public static final String NEXT_FIELD_CHOICE = "guided_field_choice";
    private static final String CANCEL_HINT = "Type cancel to exit.";
    private static final Set<String> CANCEL_WORDS = Set.of("cancel", "stop", "abort");

    private final SearchFieldCatalog searchFieldCatalog;
    private final IntentApiCatalog intentApiCatalog;
    private final FieldDefinitionRegistry fieldDefinitionRegistry;
    private final InMemoryGuidedSearchSessionService guidedSearchSessionService;

    public GuidedSearchService(
            SearchFieldCatalog searchFieldCatalog,
            IntentApiCatalog intentApiCatalog,
            FieldDefinitionRegistry fieldDefinitionRegistry,
            InMemoryGuidedSearchSessionService guidedSearchSessionService
    ) {
        this.searchFieldCatalog = searchFieldCatalog;
        this.intentApiCatalog = intentApiCatalog;
        this.fieldDefinitionRegistry = fieldDefinitionRegistry;
        this.guidedSearchSessionService = guidedSearchSessionService;
    }

    public ChatResponse start(String intentName, LexFulfillmentSession session) {
        if (!isSearchIntent(intentName)) {
            throw new IllegalArgumentException("Guided search only supports SEARCH intents: " + intentName);
        }
        List<SearchFieldDefinition> fields = guidedFields(intentName);
        if (fields.isEmpty()) {
            ChatResponse empty = new ChatResponse(
                    "Unable to process the request because no search criteria were provided.",
                    false
            );
            empty.setActionTaken("search_criteria_missing");
            empty.setLexIntent(intentName);
            return empty;
        }
        guidedSearchSessionService.put(session, GuidedSearchState.selectField(intentName));
        return buildMenuResponse(intentName, fields);
    }

    /**
     * Process a user turn while guided search is active.
     * Returns either another guided ChatResponse, or a Resume with slots for fulfillSearch.
     */
    public GuidedTurnResult handleTurn(
            LexFulfillmentSession session,
            GuidedSearchState state,
            String userMessage
    ) {
        String trimmed = userMessage != null ? userMessage.trim() : "";
        if (isCancel(trimmed)) {
            guidedSearchSessionService.clear(session);
            ChatResponse cancelled = new ChatResponse("Guided search cancelled. How else can I help?", false);
            cancelled.setActionTaken(ACTION_CANCELLED);
            cancelled.setLexIntent(state.intentName());
            return GuidedTurnResult.response(cancelled);
        }

        if (state.phase() == GuidedSearchPhase.SELECT_FIELD) {
            return handleSelectField(session, state, trimmed);
        }
        return handleCollectValue(session, state, trimmed);
    }

    private GuidedTurnResult handleSelectField(
            LexFulfillmentSession session,
            GuidedSearchState state,
            String trimmed
    ) {
        List<SearchFieldDefinition> fields = guidedFields(state.intentName());
        Optional<SearchFieldDefinition> chosen = resolveChoice(fields, trimmed);
        if (chosen.isEmpty()) {
            ChatResponse retry = buildMenuResponse(state.intentName(), fields);
            retry.setReply(
                    "I didn't understand that choice.\n\n" + retry.getReply() + "\n\n" + CANCEL_HINT
            );
            return GuidedTurnResult.response(retry);
        }

        SearchFieldDefinition field = chosen.get();
        GuidedSearchState next = GuidedSearchState.collectValue(
                state.intentName(),
                field.m3Field(),
                field.lexSlotName()
        );
        guidedSearchSessionService.put(session, next);
        return GuidedTurnResult.response(buildCollectValueResponse(state.intentName(), field, null));
    }

    private GuidedTurnResult handleCollectValue(
            LexFulfillmentSession session,
            GuidedSearchState state,
            String trimmed
    ) {
        SearchFieldDefinition field = searchFieldCatalog.find(state.intentName(), state.selectedM3Field())
                .orElse(null);
        String label = field != null && field.description() != null && !field.description().isBlank()
                ? field.description()
                : state.selectedM3Field();

        if (trimmed.isBlank()) {
            return GuidedTurnResult.response(buildCollectValueResponse(
                    state.intentName(),
                    field != null ? field : syntheticField(state),
                    "A value is required. " + CANCEL_HINT
            ));
        }

        Optional<FieldDefinition> definition = fieldDefinitionRegistry.get(state.selectedM3Field());
        if (definition.isPresent()) {
            FieldValueValidator.ValidationOutcome outcome =
                    FieldValueValidator.validate(definition.get(), trimmed);
            if (!outcome.passed()) {
                String reason = outcome.reason() != null ? outcome.reason() : "Invalid value";
                return GuidedTurnResult.response(buildCollectValueResponse(
                        state.intentName(),
                        field != null ? field : syntheticField(state),
                        reason + ". Please try again. " + CANCEL_HINT
                ));
            }
        }

        Map<String, String> slots = new LinkedHashMap<>();
        String slotKey = state.selectedLexSlot() != null && !state.selectedLexSlot().isBlank()
                ? state.selectedLexSlot()
                : state.selectedM3Field();
        slots.put(slotKey, trimmed);

        guidedSearchSessionService.clear(session);
        return GuidedTurnResult.resume(state.intentName(), slots);
    }

    private SearchFieldDefinition syntheticField(GuidedSearchState state) {
        return new SearchFieldDefinition(
                state.intentName(),
                state.selectedM3Field(),
                List.of(),
                state.selectedM3Field(),
                state.selectedLexSlot()
        );
    }

    private ChatResponse buildMenuResponse(String intentName, List<SearchFieldDefinition> fields) {
        StringBuilder reply = new StringBuilder();
        reply.append("I can search ").append(humanizeIntent(intentName)).append(".\n\n");
        reply.append("How would you like to search?\n\n");
        List<Map<String, Object>> options = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            SearchFieldDefinition field = fields.get(i);
            int index = i + 1;
            String label = field.description() != null ? field.description() : field.m3Field();
            reply.append(index).append(". ").append(label).append('\n');
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("index", index);
            option.put("field", field.m3Field());
            option.put("label", label);
            options.add(option);
        }
        reply.append('\n').append(CANCEL_HINT);

        ChatResponse response = new ChatResponse(reply.toString().trim(), false);
        response.setActionTaken(ACTION_SELECT_FIELD);
        response.setLexIntent(intentName);
        response.setCollectingTool(intentName);
        response.setNextField(NEXT_FIELD_CHOICE);
        response.setNextFieldOptional(false);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("options", options);
        response.setCollectedArgs(args);
        return response;
    }

    private ChatResponse buildCollectValueResponse(
            String intentName,
            SearchFieldDefinition field,
            String errorPrefix
    ) {
        String label = field.description() != null && !field.description().isBlank()
                ? field.description()
                : field.m3Field();
        String prompt = "Please enter the " + label.toLowerCase(Locale.ROOT) + ".";
        String reply = errorPrefix != null && !errorPrefix.isBlank()
                ? errorPrefix + "\n\n" + prompt
                : prompt;

        ChatResponse response = new ChatResponse(reply, false);
        response.setActionTaken(ACTION_COLLECT_VALUE);
        response.setLexIntent(intentName);
        response.setCollectingTool(intentName);
        response.setNextField(field.m3Field());
        response.setNextFieldOptional(false);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("field", field.m3Field());
        args.put("label", label);
        response.setCollectedArgs(args);
        return response;
    }

    private Optional<SearchFieldDefinition> resolveChoice(List<SearchFieldDefinition> fields, String input) {
        if (input == null || input.isBlank() || fields.isEmpty()) {
            return Optional.empty();
        }
        String normalized = input.trim();
        if (normalized.matches("\\d+")) {
            int index = Integer.parseInt(normalized);
            if (index >= 1 && index <= fields.size()) {
                return Optional.of(fields.get(index - 1));
            }
            return Optional.empty();
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        for (SearchFieldDefinition field : fields) {
            if (field.description() != null && field.description().equalsIgnoreCase(normalized)) {
                return Optional.of(field);
            }
            if (field.m3Field() != null && field.m3Field().equalsIgnoreCase(normalized)) {
                return Optional.of(field);
            }
            if (field.lexSlotName() != null && field.lexSlotName().equalsIgnoreCase(normalized)) {
                return Optional.of(field);
            }
            if (field.keywords() != null) {
                for (String keyword : field.keywords()) {
                    if (keyword != null && keyword.equalsIgnoreCase(lower)) {
                        return Optional.of(field);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private List<SearchFieldDefinition> guidedFields(String intentName) {
        List<SearchFieldDefinition> all = searchFieldCatalog.fieldsFor(intentName);
        List<SearchFieldDefinition> result = new ArrayList<>();
        for (SearchFieldDefinition field : all) {
            if (field.description() != null && !field.description().isBlank()) {
                result.add(field);
            }
        }
        return result;
    }

    private boolean isSearchIntent(String intentName) {
        return intentApiCatalog.find(intentName)
                .filter(def -> def.requestType() == RequestType.SEARCH)
                .isPresent();
    }

    static boolean isCancel(String trimmed) {
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

    public record GuidedTurnResult(ChatResponse response, String intentName, Map<String, String> slots) {
        public static GuidedTurnResult response(ChatResponse response) {
            return new GuidedTurnResult(response, null, null);
        }

        public static GuidedTurnResult resume(String intentName, Map<String, String> slots) {
            return new GuidedTurnResult(null, intentName, Map.copyOf(slots));
        }

        public boolean shouldResumeFulfillment() {
            return intentName != null && slots != null && !slots.isEmpty();
        }
    }
}
