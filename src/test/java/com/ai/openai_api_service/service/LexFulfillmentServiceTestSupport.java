package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.QueryContext;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.service.query.QueryContextAssembler;
import com.ai.openai_api_service.service.query.QueryUnderstander;
import com.ai.openai_api_service.service.query.ReturnColumnCatalog;
import com.ai.openai_api_service.service.query.InMemorySearchContextService;
import com.ai.openai_api_service.service.api.ApiCapabilityMessageBuilder;
import com.ai.openai_api_service.service.api.ApiCapabilityResolver;
import com.ai.openai_api_service.service.api.ApiFieldCatalog;
import com.ai.openai_api_service.service.api.InformationRequestCatalog;
import com.ai.openai_api_service.service.normalizer.FieldDefinitionRegistry;
import com.ai.openai_api_service.service.repair.SlotKeywordRegistry;
import com.ai.openai_api_service.service.repair.SlotRepairService;
import com.ai.openai_api_service.service.repair.rules.KeywordUtteranceRepairRule;
import com.ai.openai_api_service.service.repair.rules.MergedStatusSplitRule;
import com.ai.openai_api_service.service.repair.rules.MergedTextSplitRule;
import com.ai.openai_api_service.service.repair.rules.MisassignmentRepairRule;
import com.ai.openai_api_service.service.normalizer.SlotNormalizer;
import com.ai.openai_api_service.service.validation.SlotValidator;

/**
 * Shared wiring for {@link LexFulfillmentService} integration tests.
 */
final class LexFulfillmentServiceTestSupport {

    private LexFulfillmentServiceTestSupport() {
    }

    static LexFulfillmentService createFulfillmentService() {
        IntentApiCatalog intentApiCatalog = new IntentApiCatalog();
        SearchFieldCatalog searchFieldCatalog = new SearchFieldCatalog();
        FieldDefinitionRegistry fieldDefinitionRegistry = new FieldDefinitionRegistry();
        SearchResolver searchResolver = new SearchResolver(searchFieldCatalog);
        M3RequestBuilder m3RequestBuilder = new M3RequestBuilder(
                new SqryBuilder(new SearchValueFormatter())
        );
        SlotNormalizer slotNormalizer = new SlotNormalizer(searchFieldCatalog, fieldDefinitionRegistry);
        SlotValidator slotValidator = new SlotValidator(searchFieldCatalog, fieldDefinitionRegistry);
        SlotKeywordRegistry keywordRegistry = new SlotKeywordRegistry(searchFieldCatalog);
        SlotRepairService slotRepairService = new SlotRepairService(
                slotValidator,
                searchFieldCatalog,
                fieldDefinitionRegistry,
                new KeywordUtteranceRepairRule(keywordRegistry),
                new MisassignmentRepairRule(),
                new MergedStatusSplitRule(keywordRegistry),
                new MergedTextSplitRule(keywordRegistry)
        );
        RequestedInformationResolver requestedInformationResolver = new RequestedInformationResolver(
                searchFieldCatalog,
                new InformationRequestCatalog()
        );
        ReturnColumnCatalog returnColumnCatalog = new ReturnColumnCatalog(intentApiCatalog);
        QueryUnderstander queryUnderstander = new QueryUnderstander(
                requestedInformationResolver,
                intentApiCatalog,
                returnColumnCatalog
        );
        InformationRequestCatalog informationRequestCatalog = new InformationRequestCatalog();
        ApiFieldCatalog apiFieldCatalog = new ApiFieldCatalog();
        ApiCapabilityResolver apiCapabilityResolver = new ApiCapabilityResolver(
                apiFieldCatalog,
                new ApiCapabilityMessageBuilder(informationRequestCatalog)
        );
        return new LexFulfillmentService(
                intentApiCatalog,
                searchResolver,
                m3RequestBuilder,
                slotNormalizer,
                slotRepairService,
                slotValidator,
                new QueryContextAssembler(),
                queryUnderstander,
                new InMemorySearchContextService(intentApiCatalog, 3600),
                apiCapabilityResolver
        );
    }

    static IntentDefinition searchCustomerOrderDefinition(IntentApiCatalog catalog) {
        return catalog.find("SearchCustomerOrder").orElseThrow();
    }

    static QueryContext contextWithLimit(IntentDefinition definition, int limit) {
        return new QueryContext(
                definition.intentName(),
                java.util.Map.of(),
                java.util.List.of(new SearchCriterion("CUNO", "Y11100")),
                java.util.List.of(),
                limit,
                java.util.List.of(),
                null,
                null,
                null,
                java.util.Map.of(),
                false
        );
    }
}
