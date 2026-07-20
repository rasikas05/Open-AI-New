package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.InvalidLexSlotException;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.LexFulfillmentOutcome;
import com.ai.openai_api_service.model.M3RequestDto;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import com.ai.openai_api_service.service.normalizer.SlotNormalizer;
import com.ai.openai_api_service.service.normalizer.SlotValue;
import com.ai.openai_api_service.service.repair.SlotRepairService;
import com.ai.openai_api_service.service.validation.SlotValidator;
import com.ai.openai_api_service.service.validation.ValidatedSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LexFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(LexFulfillmentService.class);

    private final LexIntentMapper lexIntentMapper;
    private final IntentApiCatalog intentApiCatalog;
    private final SearchResolver searchResolver;
    private final M3RequestBuilder m3RequestBuilder;
    private final SlotNormalizer slotNormalizer;
    private final SlotRepairService slotRepairService;
    private final SlotValidator slotValidator;

    public LexFulfillmentService(
            LexIntentMapper lexIntentMapper,
            IntentApiCatalog intentApiCatalog,
            SearchResolver searchResolver,
            M3RequestBuilder m3RequestBuilder,
            SlotNormalizer slotNormalizer,
            SlotRepairService slotRepairService,
            SlotValidator slotValidator
    ) {
        this.lexIntentMapper = lexIntentMapper;
        this.intentApiCatalog = intentApiCatalog;
        this.searchResolver = searchResolver;
        this.m3RequestBuilder = m3RequestBuilder;
        this.slotNormalizer = slotNormalizer;
        this.slotRepairService = slotRepairService;
        this.slotValidator = slotValidator;
    }

    public ChatResponse fulfill(LexRecognizeResult lexResult) {
        return fulfill(lexResult, null);
    }

    public ChatResponse fulfill(LexRecognizeResult lexResult, String userUtterance) {
        return fulfillOutcome(lexResult, userUtterance).response();
    }

    public LexFulfillmentOutcome fulfillOutcome(LexRecognizeResult lexResult, String userUtterance) {
        try {
            return fulfillOutcomeMapped(lexResult, userUtterance);
        } catch (InvalidLexSlotException e) {
            log.info(
                    "Lex fulfillment rejected invalid slot: intent='{}' message='{}'",
                    lexResult.getIntentName(),
                    e.getUserMessage()
            );
            ChatResponse chatResponse = new ChatResponse(e.getUserMessage(), false);
            chatResponse.setActionTaken("lex_invalid_slot");
            chatResponse.setLexIntent(lexResult.getIntentName());
            return new LexFulfillmentOutcome(chatResponse, List.of());
        }
    }

    private LexFulfillmentOutcome fulfillOutcomeMapped(LexRecognizeResult lexResult, String userUtterance) {
        SearchPipelineResult pipeline = resolveSearchPipeline(lexResult, userUtterance);
        LexIntentMapper.MappedM3Request mapped = pipeline.mapped();

        log.info(
                "Lex fulfillment: intent='{}' program='{}' transaction='{}' params={}",
                lexResult.getIntentName(),
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

        String reply = buildPlaceholderReply(lexResult.getIntentName(), mapped);
        ChatResponse chatResponse = new ChatResponse(reply, false);
        chatResponse.setActionTaken(mapped.actionTaken());
        chatResponse.setM3Request(m3Request);
        chatResponse.setLexIntent(lexResult.getIntentName());

        return new LexFulfillmentOutcome(chatResponse, pipeline.searchCriteria());
    }

    private SearchPipelineResult resolveSearchPipeline(LexRecognizeResult lexResult, String userUtterance) {
        Optional<IntentDefinition> definition = intentApiCatalog.find(lexResult.getIntentName());
        if (definition.isPresent() && definition.get().requestType() == RequestType.SEARCH) {
            Map<String, SlotValue> normalized = slotNormalizer.normalize(
                    definition.get().intentName(),
                    SlotNormalizer.toSlotValues(lexResult.getSlots())
            );
            Map<String, SlotValue> repaired = slotRepairService.repair(
                    definition.get().intentName(),
                    userUtterance,
                    normalized
            );
            List<SearchCriterion> criteria = searchResolver.resolve(
                    definition.get().intentName(),
                    toValidSlotStrings(definition.get().intentName(), repaired)
            );
            LexIntentMapper.MappedM3Request mapped = m3RequestBuilder.build(definition.get(), criteria);
            return new SearchPipelineResult(mapped, criteria);
        }

        LexIntentMapper.MappedM3Request mapped = lexIntentMapper.map(lexResult)
                .orElseThrow(() -> new OpenAIException(
                        "No M3 mapping for Lex intent: " + lexResult.getIntentName(),
                        400
                ));
        return new SearchPipelineResult(mapped, List.of());
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
            List<SearchCriterion> searchCriteria
    ) {
    }
}
