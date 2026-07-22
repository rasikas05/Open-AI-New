package com.ai.openai_api_service.service.api;

import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.RequestedInformationResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiCapabilityResolverTest {

    private ApiCapabilityResolver resolver;
    private IntentDefinition getCustomer;
    private IntentDefinition searchCustomerOrder;

    @BeforeEach
    void setUp() {
        InformationRequestCatalog informationRequestCatalog = new InformationRequestCatalog();
        resolver = new ApiCapabilityResolver(
                new ApiFieldCatalog(),
                new ApiCapabilityMessageBuilder(informationRequestCatalog)
        );
        IntentApiCatalog catalog = new IntentApiCatalog();
        getCustomer = catalog.find("GetCustomer").orElseThrow();
        searchCustomerOrder = catalog.find("SearchCustomerOrder").orElseThrow();
    }

    @Test
    void resolve_fullRequest_skipsCapabilityLayer() {
        ApiCapabilityResult result = resolver.resolve(getCustomer, List.of(RequestedInformationResolver.FULL));

        assertTrue(result.shouldExecuteM3());
        assertTrue(result.supportedReturnColumns().isEmpty());
        assertEquals(null, result.userMessage());
    }

    @Test
    void resolve_phoneOnGetCustomer_executesWithPhnoColumns() {
        ApiCapabilityResult result = resolver.resolve(getCustomer, List.of(RequestedInformationResolver.PHONE));

        assertTrue(result.shouldExecuteM3());
        assertEquals(List.of("PHNO"), List.copyOf(result.supportedReturnColumns()));
    }

    @Test
    void resolve_salespersonOnGetCustomer_blocksM3() {
        ApiCapabilityResult result = resolver.resolve(getCustomer, List.of("SALESPERSON"));

        assertFalse(result.shouldExecuteM3());
        assertEquals(ApiCapabilityResolver.ACTION_INFORMATION_NOT_AVAILABLE, result.actionTaken());
        assertTrue(result.userMessage().contains("salesperson"));
        assertTrue(result.userMessage().contains("Customer Basic Data"));
    }

    @Test
    void resolve_unknownInformation_blocksWithClarification() {
        ApiCapabilityResult result = resolver.resolve(
                getCustomer,
                List.of(SpecificInformationHelper.UNKNOWN_INFORMATION)
        );

        assertFalse(result.shouldExecuteM3());
        assertEquals(ApiCapabilityResolver.ACTION_INFORMATION_UNKNOWN, result.actionTaken());
    }

    @Test
    void resolve_salespersonOnSearch_executesWithReturnColumns() {
        ApiCapabilityResult result = resolver.resolve(searchCustomerOrder, List.of("SALESPERSON"));

        assertTrue(result.shouldExecuteM3());
        assertEquals(List.of("ORNO", "SMCD"), List.copyOf(result.supportedReturnColumns()));
    }

    @Test
    void resolve_phoneAndEmailOnGetCustomer_executesWithBothColumns() {
        ApiCapabilityResult result = resolver.resolve(
                getCustomer,
                List.of(RequestedInformationResolver.PHONE, RequestedInformationResolver.EMAIL)
        );

        assertTrue(result.shouldExecuteM3());
        assertEquals(List.of("PHNO", "EMAL"), List.copyOf(result.supportedReturnColumns()));
    }

    @Test
    void resolve_loyaltyTierOnGetCustomer_blocksM3() {
        ApiCapabilityResult result = resolver.resolve(getCustomer, List.of("LOYALTY_TIER"));

        assertFalse(result.shouldExecuteM3());
        assertTrue(result.userMessage().contains("loyalty tier"));
    }
}
