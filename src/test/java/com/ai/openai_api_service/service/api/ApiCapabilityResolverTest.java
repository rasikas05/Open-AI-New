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
    private IntentDefinition getCustomerFinancial;
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
        getCustomerFinancial = catalog.find("GetCustomerFinancial").orElseThrow();
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
        assertEquals(List.of("PHNO", "MAIL"), List.copyOf(result.supportedReturnColumns()));
    }

    @Test
    void resolve_loyaltyTierOnGetCustomer_blocksM3() {
        ApiCapabilityResult result = resolver.resolve(getCustomer, List.of("LOYALTY_TIER"));

        assertFalse(result.shouldExecuteM3());
        assertTrue(result.userMessage().contains("loyalty tier"));
    }

    @Test
    void resolve_paymentTermsOnGetCustomer_blocksAsUnsupported() {
        ApiCapabilityResult result = resolver.resolve(getCustomer, List.of("PAYMENT_TERMS"));

        assertFalse(result.shouldExecuteM3());
        assertEquals(ApiCapabilityResolver.ACTION_INFORMATION_NOT_AVAILABLE, result.actionTaken());
        assertTrue(result.userMessage().toLowerCase().contains("payment terms"));
    }

    @Test
    void resolve_paymentTermsOnSearch_executesWithTepyColumns() {
        ApiCapabilityResult result = resolver.resolve(searchCustomerOrder, List.of("PAYMENT_TERMS"));

        assertTrue(result.shouldExecuteM3());
        assertEquals(List.of("ORNO", "TEPY"), List.copyOf(result.supportedReturnColumns()));
    }

    @Test
    void resolve_creditLimitOnGetFinancial_executesWithCrlmColumns() {
        ApiCapabilityResult result = resolver.resolve(
                getCustomerFinancial,
                List.of(RequestedInformationResolver.CREDIT_LIMIT)
        );

        assertTrue(result.shouldExecuteM3());
        assertTrue(result.supportedReturnColumns().contains("CRLM"));
    }

    @Test
    void resolve_paymentAndCurrencyOnGetFinancial_executesWithBothColumns() {
        ApiCapabilityResult result = resolver.resolve(
                getCustomerFinancial,
                List.of(RequestedInformationResolver.PAYMENT, RequestedInformationResolver.CURRENCY)
        );

        assertTrue(result.shouldExecuteM3());
        assertEquals(
                java.util.Set.of("PYCD", "CUCD"),
                java.util.Set.copyOf(result.supportedReturnColumns())
        );
    }

    @Test
    void resolve_statusAndEmailOnSearch_partialExecuteKeepsUnsupportedEmail() {
        ApiCapabilityResult result = resolver.resolve(
                searchCustomerOrder,
                List.of(RequestedInformationResolver.STATUS, RequestedInformationResolver.EMAIL)
        );

        assertTrue(result.shouldExecuteM3());
        assertEquals(List.of("ORNO", "ORST"), List.copyOf(result.supportedReturnColumns()));
        assertEquals(List.of(RequestedInformationResolver.STATUS), result.supportedCodes());
        assertEquals(List.of(RequestedInformationResolver.EMAIL), result.unsupportedCodes());
        assertTrue(result.userMessage().toLowerCase().contains("email"));
    }

    @Test
    void resolve_buyerOnSearchPurchaseOrder_executesWithBuyeColumns() {
        IntentDefinition searchPo = new IntentApiCatalog().find("SearchPurchaseOrder").orElseThrow();
        ApiCapabilityResult result = resolver.resolve(searchPo, List.of("BUYER"));

        assertTrue(result.shouldExecuteM3());
        assertEquals(List.of("PUNO", "BUYE"), List.copyOf(result.supportedReturnColumns()));
    }

    @Test
    void resolve_priorityOnSearchManufacturingOrder_executesWithPrioColumns() {
        IntentDefinition searchMo = new IntentApiCatalog().find("SearchManufacturingOrder").orElseThrow();
        ApiCapabilityResult result = resolver.resolve(searchMo, List.of("PRIORITY"));

        assertTrue(result.shouldExecuteM3());
        assertEquals(List.of("MFNO", "PRIO"), List.copyOf(result.supportedReturnColumns()));
    }
}
