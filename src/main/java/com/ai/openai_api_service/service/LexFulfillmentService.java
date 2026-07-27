package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.InvalidLexSlotException;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.LexFulfillmentOutcome;
import com.ai.openai_api_service.model.LexFulfillmentSession;
import com.ai.openai_api_service.model.M3RequestDto;
import com.ai.openai_api_service.model.PaginationMetadataDto;
import com.ai.openai_api_service.model.QueryContext;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SearchContext;
import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import com.ai.openai_api_service.service.normalizer.SlotNormalizer;
import com.ai.openai_api_service.service.normalizer.SlotValue;
import com.ai.openai_api_service.service.query.QueryContextAssembler;
import com.ai.openai_api_service.service.query.QueryUnderstander;
import com.ai.openai_api_service.service.query.SearchContextService;
import com.ai.openai_api_service.service.api.ApiCapabilityResolver;
import com.ai.openai_api_service.service.api.ApiCapabilityResult;
import com.ai.openai_api_service.service.api.SpecificInformationHelper;
import com.ai.openai_api_service.service.slots.GenericSlotInterpreter;
import com.ai.openai_api_service.service.repair.SlotRepairService;
import com.ai.openai_api_service.service.validation.SlotValidator;
import com.ai.openai_api_service.service.validation.SearchCriteriaValidator;
import com.ai.openai_api_service.service.validation.M3RequestExecutionValidator;
import com.ai.openai_api_service.service.validation.ValidatedSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LexFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(LexFulfillmentService.class);

    private final IntentApiCatalog intentApiCatalog;
    private final SearchResolver searchResolver;
    private final M3RequestBuilder m3RequestBuilder;
    private final SlotNormalizer slotNormalizer;
    private final SlotRepairService slotRepairService;
    private final GenericSlotInterpreter genericSlotInterpreter;
    private final SlotValidator slotValidator;
    private final QueryContextAssembler queryContextAssembler;
    private final QueryUnderstander queryUnderstander;
    private final SearchContextService searchContextService;
    private final ApiCapabilityResolver apiCapabilityResolver;
    private final M3RequestExecutionValidator m3RequestExecutionValidator;

    public LexFulfillmentService(
            IntentApiCatalog intentApiCatalog,
            SearchResolver searchResolver,
            M3RequestBuilder m3RequestBuilder,
            SlotNormalizer slotNormalizer,
            SlotRepairService slotRepairService,
            GenericSlotInterpreter genericSlotInterpreter,
            SlotValidator slotValidator,
            QueryContextAssembler queryContextAssembler,
            QueryUnderstander queryUnderstander,
            SearchContextService searchContextService,
            ApiCapabilityResolver apiCapabilityResolver,
            M3RequestExecutionValidator m3RequestExecutionValidator
    ) {
        this.intentApiCatalog = intentApiCatalog;
        this.searchResolver = searchResolver;
        this.m3RequestBuilder = m3RequestBuilder;
        this.slotNormalizer = slotNormalizer;
        this.slotRepairService = slotRepairService;
        this.genericSlotInterpreter = genericSlotInterpreter;
        this.slotValidator = slotValidator;
        this.queryContextAssembler = queryContextAssembler;
        this.queryUnderstander = queryUnderstander;
        this.searchContextService = searchContextService;
        this.apiCapabilityResolver = apiCapabilityResolver;
        this.m3RequestExecutionValidator = m3RequestExecutionValidator;
    }

    public ChatResponse fulfill(LexRecognizeResult lexResult) {
        return fulfill(lexResult, null);
    }

    public ChatResponse fulfill(LexRecognizeResult lexResult, String userUtterance) {
        return fulfillOutcome(lexResult, userUtterance).response();
    }

    public LexFulfillmentOutcome fulfillOutcome(LexRecognizeResult lexResult, String userUtterance) {
        return fulfillOutcome(lexResult, userUtterance, null);
    }

    public LexFulfillmentOutcome fulfillOutcome(
            LexRecognizeResult lexResult,
            String userUtterance,
            LexFulfillmentSession session
    ) {
        try {
            return fulfillOutcomeMapped(lexResult, userUtterance, session);
        } catch (InvalidLexSlotException e) {
            log.info(
                    "Lex fulfillment rejected invalid slot: intent='{}' message='{}'",
                    lexResult.getIntentName(),
                    e.getUserMessage()
            );
            ChatResponse chatResponse = new ChatResponse(e.getUserMessage(), false);
            chatResponse.setActionTaken("lex_invalid_slot");
            chatResponse.setLexIntent(lexResult.getIntentName());
            return new LexFulfillmentOutcome(chatResponse, List.of(), null, null);
        }
    }

    /**
     * Resume SEARCH fulfillment with an explicit slot map (guided search).
     * Reuses the same SEARCH pipeline as Lex ReadyForFulfillment — no LexRecognizeResult required.
     */
    public LexFulfillmentOutcome fulfillSearch(
            String intentName,
            Map<String, String> slots,
            String userUtterance,
            LexFulfillmentSession session
    ) {
        try {
            SearchPipelineResult pipeline = resolveSearchPipeline(
                    intentName,
                    slots != null ? slots : Map.of(),
                    Map.of(),
                    userUtterance,
                    session
            );
            return toFulfillmentOutcome(intentName, pipeline);
        } catch (InvalidLexSlotException e) {
            log.info(
                    "Guided search fulfillment rejected invalid slot: intent='{}' message='{}'",
                    intentName,
                    e.getUserMessage()
            );
            ChatResponse chatResponse = new ChatResponse(e.getUserMessage(), false);
            chatResponse.setActionTaken("lex_invalid_slot");
            chatResponse.setLexIntent(intentName);
            return new LexFulfillmentOutcome(chatResponse, List.of(), null, null);
        }
    }

    private LexFulfillmentOutcome fulfillOutcomeMapped(
            LexRecognizeResult lexResult,
            String userUtterance,
            LexFulfillmentSession session
    ) {
        SearchPipelineResult pipeline = resolveSearchPipeline(
                lexResult.getIntentName(),
                lexResult.getSlots(),
                lexResult.getSessionAttributes(),
                userUtterance,
                session
        );
        return toFulfillmentOutcome(lexResult.getIntentName(), pipeline);
    }

    private LexFulfillmentOutcome toFulfillmentOutcome(String intentName, SearchPipelineResult pipeline) {
        if (pipeline.blocked()) {
            ChatResponse chatResponse = new ChatResponse(pipeline.blockedMessage(), false);
            chatResponse.setActionTaken(pipeline.blockedActionTaken());
            chatResponse.setLexIntent(intentName);
            return new LexFulfillmentOutcome(
                    chatResponse,
                    pipeline.searchCriteria(),
                    pipeline.queryContext(),
                    null
            );
        }

        LexIntentMapper.MappedM3Request mapped = pipeline.mapped();

        log.info(
                "Lex fulfillment: intent='{}' program='{}' transaction='{}' params={}",
                intentName,
                mapped.program(),
                mapped.transaction(),
                mapped.params()
        );

        M3RequestDto m3Request = new M3RequestDto(
                true,
                mapped.program(),
                mapped.transaction(),
                mapped.params()
        );

        String reply = buildPlaceholderReply(intentName, mapped);
        if (pipeline.capabilityUserMessage() != null && !pipeline.capabilityUserMessage().isBlank()) {
            reply = reply + " " + pipeline.capabilityUserMessage();
        }
        ChatResponse chatResponse = new ChatResponse(reply, false);
        chatResponse.setActionTaken(mapped.actionTaken());
        chatResponse.setM3Request(m3Request);
        chatResponse.setLexIntent(intentName);

        SearchContext searchContext = pipeline.searchContext();
        if (searchContext != null) {
            chatResponse.setSearchContextId(searchContext.searchContextId());
            PaginationMetadataDto pagination = new PaginationMetadataDto();
            pagination.setPageSize(searchContext.pageSize());
            pagination.setSupportsContinuation(true);
            chatResponse.setPagination(pagination);
        }

        return new LexFulfillmentOutcome(
                chatResponse,
                pipeline.searchCriteria(),
                pipeline.queryContext(),
                searchContext
        );
    }

    private SearchPipelineResult resolveSearchPipeline(
            String intentName,
            Map<String, String> slots,
            Map<String, String> sessionAttributes,
            String userUtterance,
            LexFulfillmentSession session
    ) {
        Optional<IntentDefinition> definition = intentApiCatalog.find(intentName);
        if (definition.isEmpty()) {
            throw new OpenAIException(
                    "No M3 mapping for Lex intent: " + intentName,
                    400
            );
        }

        IntentDefinition intentDefinition = definition.get();
        QueryContext baseContext;
        List<SearchCriterion> criteria;
        Map<String, String> slotMap = slots != null ? slots : Map.of();

        if (intentDefinition.requestType() == RequestType.SEARCH) {
            Map<String, SlotValue> normalized = slotNormalizer.normalize(
                    intentDefinition.intentName(),
                    SlotNormalizer.toSlotValues(slotMap)
            );
            Map<String, SlotValue> repaired = slotRepairService.repair(
                    intentDefinition.intentName(),
                    userUtterance,
                    normalized
            );
            Map<String, SlotValue> interpreted = genericSlotInterpreter.interpret(
                    intentDefinition.intentName(),
                    repaired
            );
            Map<String, SlotValue> normalizedInterpreted = slotNormalizer.normalize(
                    intentDefinition.intentName(),
                    interpreted
            );
            Map<String, String> validSlots = toValidSlotStrings(intentDefinition.intentName(), normalizedInterpreted);
            criteria = searchResolver.resolve(intentDefinition.intentName(), validSlots);
            baseContext = queryContextAssembler.assembleSearch(
                    intentDefinition.intentName(),
                    validSlots,
                    criteria
            );
        } else {
            criteria = List.of();
            baseContext = queryContextAssembler.assembleRead(
                    intentName,
                    slotMap
            );
        }

        QueryContext enriched = queryUnderstander.enrich(
                baseContext,
                userUtterance,
                sessionAttributes != null ? sessionAttributes : Map.of()
        );
        QueryContext buildContext = searchContextService.applyContinuation(session, enriched);

        Optional<LexIntentMapper.MappedM3Request> continuationMapped =
                searchContextService.buildContinuationRequest(session, buildContext);

        if (continuationMapped.isEmpty()
                && intentDefinition.requestType() == RequestType.SEARCH
                && !SearchCriteriaValidator.hasUsableCriteria(buildContext.criteria())) {
            return SearchPipelineResult.blocked(
                    SearchCriteriaValidator.NO_CRITERIA_MESSAGE,
                    SearchCriteriaValidator.ACTION_SEARCH_CRITERIA_MISSING,
                    criteria,
                    buildContext
            );
        }

        String capabilityUserMessage = null;
        if (continuationMapped.isEmpty()
                && SpecificInformationHelper.isSpecificInformationRequest(buildContext.requestedInformation())) {
            ApiCapabilityResult capability = apiCapabilityResolver.resolve(intentDefinition, buildContext.requestedInformation());
            if (!capability.shouldExecuteM3()) {
                return SearchPipelineResult.blocked(
                        capability.userMessage(),
                        capability.actionTaken(),
                        criteria,
                        buildContext
                );
            }
            if (!capability.supportedReturnColumns().isEmpty()) {
                buildContext = buildContext.withReturnColumns(
                        new ArrayList<>(capability.supportedReturnColumns())
                );
            }
            capabilityUserMessage = capability.userMessage();
        }

        final QueryContext contextForBuild = buildContext;
        LexIntentMapper.MappedM3Request mapped = continuationMapped.orElseGet(
                () -> m3RequestBuilder.build(intentDefinition, contextForBuild)
        );

        if (continuationMapped.isEmpty()
                && intentDefinition.requestType() == RequestType.SEARCH
                && !m3RequestExecutionValidator.isExecutable(intentDefinition, mapped)) {
            return SearchPipelineResult.blocked(
                    M3RequestExecutionValidator.INVALID_SEARCH_MESSAGE,
                    M3RequestExecutionValidator.ACTION_M3_SEARCH_REQUEST_INVALID,
                    criteria,
                    contextForBuild
            );
        }

        SearchContext searchContext = null;
        if (intentDefinition.requestType() == RequestType.SEARCH
                && !contextForBuild.continuationRequested()
                && continuationMapped.isEmpty()) {
            searchContext = searchContextService.startOrReplaceSearch(
                    session,
                    intentDefinition.intentName(),
                    mapped,
                    contextForBuild
            );
        }

        return new SearchPipelineResult(
                mapped,
                criteria,
                contextForBuild,
                searchContext,
                capabilityUserMessage,
                null,
                null
        );
    }

    private Map<String, String> toValidSlotStrings(String intentName, Map<String, SlotValue> slots) {
        if (slots == null || slots.isEmpty()) {
            return Map.of();
        }
        Set<String> validLexSlots = slotValidator.validate(intentName, slots).stream()
                .filter(ValidatedSlot::valid)
                .map(ValidatedSlot::lexSlotName)
                .collect(Collectors.toSet());

        Map<String, String> all = SlotNormalizer.toStringMap(slots);
        Map<String, String> validOnly = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            if (validLexSlots.contains(entry.getKey())) {
                validOnly.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(validOnly);
    }

    private String buildPlaceholderReply(String intentName, LexIntentMapper.MappedM3Request mapped) {
        if ("GetCustomer".equals(intentName)) {
            Object cuno = mapped.params().get("CUNO");
            return "Looking up customer " + cuno + "...";
        }
        return "Processing your request...";
    }

    private record SearchPipelineResult(
            LexIntentMapper.MappedM3Request mapped,
            List<SearchCriterion> searchCriteria,
            QueryContext queryContext,
            SearchContext searchContext,
            String capabilityUserMessage,
            String blockedMessage,
            String blockedActionTaken
    ) {
        static SearchPipelineResult blocked(
                String message,
                String actionTaken,
                List<SearchCriterion> searchCriteria,
                QueryContext queryContext
        ) {
            return new SearchPipelineResult(
                    null,
                    searchCriteria,
                    queryContext,
                    null,
                    null,
                    message,
                    actionTaken
            );
        }

        boolean blocked() {
            return blockedMessage != null;
        }
    }
}
