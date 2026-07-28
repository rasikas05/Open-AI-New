package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import com.ai.openai_api_service.service.api.InformationRequestCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestedInformationResolverTest {

    private final RequestedInformationResolver resolver =
            new RequestedInformationResolver(new SearchFieldCatalog(), new InformationRequestCatalog());

    @Test
    void resolve_addressKeyword_returnsAddress() {
        assertEquals(
                List.of(RequestedInformationResolver.ADDRESS),
                resolver.resolve("Show address of customer", "GetCustomer", Map.of())
        );
    }

    @Test
    void resolve_phoneKeyword_returnsPhone() {
        assertEquals(
                List.of(RequestedInformationResolver.PHONE),
                resolver.resolve("Show phone number of customer", "GetCustomer", Map.of())
        );
    }

    @Test
    void resolve_emailKeyword_returnsEmail() {
        assertEquals(
                List.of(RequestedInformationResolver.EMAIL),
                resolver.resolve("Show email of customer", "GetCustomer", Map.of())
        );
    }

    @Test
    void resolve_statusKeyword_returnsStatus() {
        assertEquals(
                List.of(RequestedInformationResolver.STATUS),
                resolver.resolve("Show customer status", "GetCustomer", Map.of())
        );
    }

    @Test
    void resolve_basicKeyword_returnsBasic() {
        assertEquals(
                List.of(RequestedInformationResolver.BASIC),
                resolver.resolve("Show basic customer name", "GetCustomer", Map.of())
        );
    }

    @Test
    void resolve_detailsOrShowCustomer_returnsFull() {
        assertEquals(
                List.of(RequestedInformationResolver.FULL),
                resolver.resolve("Show customer details", "GetCustomer", Map.of())
        );
        assertEquals(
                List.of(RequestedInformationResolver.FULL),
                resolver.resolve("Show customer", "GetCustomer", Map.of())
        );
    }

    @Test
    void resolve_slotOnlyReply_usesSessionAttributes() {
        Map<String, String> attrs = Map.of(
                LexRecognizeResult.ATTR_REQUESTED_INFORMATION,
                "ADDRESS"
        );
        assertEquals(
                List.of(RequestedInformationResolver.ADDRESS),
                resolver.resolve("Y11100", "GetCustomer", attrs)
        );
    }

    @Test
    void resolve_emptyTextNoAttrs_returnsFull() {
        assertEquals(
                List.of(RequestedInformationResolver.FULL),
                resolver.resolve("Y11100", "GetCustomer", Map.of())
        );
    }

    @Test
    void resolve_specificKeywordsPreferOverSessionFull() {
        Map<String, String> attrs = Map.of(
                LexRecognizeResult.ATTR_REQUESTED_INFORMATION,
                "FULL"
        );
        assertEquals(
                List.of(RequestedInformationResolver.PHONE),
                resolver.resolve("show phone for customer", "GetCustomer", attrs)
        );
    }

    @Test
    void resolve_addressAndPhone_returnsBoth() {
        assertEquals(
                List.of(RequestedInformationResolver.ADDRESS, RequestedInformationResolver.PHONE),
                resolver.resolve("show address and phone of customer", "GetCustomer", Map.of())
        );
    }

    @Test
    void encodeDecode_roundTrip() {
        String encoded = resolver.encode(List.of("PHONE", "ADDRESS"));
        assertEquals("ADDRESS,PHONE", encoded);
        assertEquals(
                List.of("ADDRESS", "PHONE"),
                resolver.decode(Map.of(LexRecognizeResult.ATTR_REQUESTED_INFORMATION, encoded))
        );
    }

    @Test
    void differsFromSession_detectsChange() {
        assertTrue(resolver.differsFromSession(
                List.of(RequestedInformationResolver.ADDRESS),
                Map.of()
        ));
        assertFalse(resolver.differsFromSession(
                List.of(RequestedInformationResolver.ADDRESS),
                Map.of(LexRecognizeResult.ATTR_REQUESTED_INFORMATION, "ADDRESS")
        ));
    }

    @Test
    void resolveForSearch_filterStatusInCriteria_returnsFull() {
        String utterance =
                "Retrieve customer orders for customer Y11100 in facility A01 with status 33 on 2025-04-24";
        List<SearchCriterion> criteria = List.of(
                new SearchCriterion("CUNO", "Y11100"),
                new SearchCriterion("FACI", "A01"),
                new SearchCriterion("ORST", "33")
        );

        assertEquals(
                List.of(RequestedInformationResolver.FULL),
                resolver.resolveForSearch(utterance, criteria)
        );
    }

    @Test
    void resolveForSearch_explicitOrderStatusDisplay_returnsStatus() {
        String utterance = "Show customer order 1000001234 status";
        List<SearchCriterion> criteria = List.of(new SearchCriterion("ORNO", "1000001234"));

        assertEquals(
                List.of(RequestedInformationResolver.STATUS),
                resolver.resolveForSearch(utterance, criteria)
        );
    }

    @Test
    void resolveForSearch_showOrdersOnly_returnsFull() {
        assertEquals(
                List.of(RequestedInformationResolver.FULL),
                resolver.resolveForSearch("Show customer orders", List.of())
        );
    }

    @Test
    void resolveForSearch_statusDisplaySuppressedWhenOrstInCriteria_returnsFull() {
        String utterance = "Show customer order status with status 33";
        List<SearchCriterion> criteria = List.of(new SearchCriterion("ORST", "33"));

        assertEquals(
                List.of(RequestedInformationResolver.FULL),
                resolver.resolveForSearch(utterance, criteria)
        );
    }

    @Test
    void resolveForSearch_poHighestStatusInCriteria_suppressesToFull() {
        assertEquals(
                List.of(RequestedInformationResolver.FULL),
                resolver.resolveForSearch(
                        "Show last 5 purchase orders with highest status 33",
                        List.of(new SearchCriterion("PUST", "33"))
                )
        );
    }

    @Test
    void resolveForSearch_coHighestStatusInCriteria_suppressesToFull() {
        assertEquals(
                List.of(RequestedInformationResolver.FULL),
                resolver.resolveForSearch(
                        "Show customer orders with highest status 77",
                        List.of(new SearchCriterion("ORST", "77"))
                )
        );
    }

    @Test
    void resolveForSearch_moStatusInCriteria_suppressesToFull() {
        assertEquals(
                List.of(RequestedInformationResolver.FULL),
                resolver.resolveForSearch(
                        "Show manufacturing orders with status 90",
                        List.of(new SearchCriterion("WHST", "90"))
                )
        );
    }

    @Test
    void resolveForSearch_doHighestStatusInCriteria_suppressesToFull() {
        assertEquals(
                List.of(RequestedInformationResolver.FULL),
                resolver.resolveForSearch(
                        "Show distribution orders with highest status 60",
                        List.of(new SearchCriterion("TRSH", "60"))
                )
        );
    }

    @Test
    void resolveForSearch_poSupplierInCriteria_suppressesSupplierToFull() {
        assertEquals(
                List.of(RequestedInformationResolver.FULL),
                resolver.resolveForSearch(
                        "Show purchase orders with supplier ABC",
                        List.of(new SearchCriterion("SUNO", "ABC"))
                )
        );
    }

    @Test
    void resolveForSearch_genuineSupplierReturnWithoutSupplierCriteria_keepsSupplier() {
        assertEquals(
                List.of("SUPPLIER"),
                resolver.resolveForSearch("Show purchase orders and include supplier", List.of())
        );
    }

    @Test
    void normalizeBusinessGroup_highestAndLowestStatus_mapToStatus() {
        assertEquals(RequestedInformationResolver.STATUS, RequestedInformationResolver.normalizeBusinessGroup("HIGHEST_STATUS"));
        assertEquals(RequestedInformationResolver.STATUS, RequestedInformationResolver.normalizeBusinessGroup("LOWEST_STATUS"));
        assertEquals(RequestedInformationResolver.STATUS, RequestedInformationResolver.normalizeBusinessGroup("ORDER_STATUS"));
    }

    @Test
    void businessGroupByM3Field_coversSearchStatusFields() {
        assertEquals(RequestedInformationResolver.STATUS, RequestedInformationResolver.BUSINESS_GROUP_BY_M3_FIELD.get("PUST"));
        assertEquals(RequestedInformationResolver.STATUS, RequestedInformationResolver.BUSINESS_GROUP_BY_M3_FIELD.get("ORST"));
        assertEquals(RequestedInformationResolver.STATUS, RequestedInformationResolver.BUSINESS_GROUP_BY_M3_FIELD.get("WHST"));
        assertEquals(RequestedInformationResolver.STATUS, RequestedInformationResolver.BUSINESS_GROUP_BY_M3_FIELD.get("TRSH"));
        assertEquals("SUPPLIER", RequestedInformationResolver.BUSINESS_GROUP_BY_M3_FIELD.get("SUNO"));
        assertEquals("FACILITY", RequestedInformationResolver.BUSINESS_GROUP_BY_M3_FIELD.get("FACI"));
        assertEquals("DELIVERY_DATE", RequestedInformationResolver.BUSINESS_GROUP_BY_M3_FIELD.get("RLDZ"));
    }

    @Test
    void resolveForSearch_orderStatus_returnsStatus() {
        assertEquals(
                List.of(RequestedInformationResolver.STATUS),
                resolver.resolveForSearch("Show order status", List.of())
        );
    }

    @Test
    void resolveForSearch_orderAmount_returnsOrderAmount() {
        assertEquals(
                List.of("ORDER_AMOUNT"),
                resolver.resolveForSearch("Show order amount", List.of())
        );
    }

    @Test
    void resolveForSearch_orderStatusAndAmount_returnsBothInOrder() {
        assertEquals(
                List.of(RequestedInformationResolver.STATUS, "ORDER_AMOUNT"),
                resolver.resolveForSearch("Show order status and amount", List.of())
        );
    }

    @Test
    void resolveForSearch_orderAmountAndDeliveryDate_returnsBothInOrder() {
        assertEquals(
                List.of("ORDER_AMOUNT", "DELIVERY_DATE"),
                resolver.resolveForSearch("Show order amount and delivery date", List.of())
        );
    }

    @Test
    void resolveForSearch_statusAmountAndDeliveryDate_returnsAllInOrder() {
        assertEquals(
                List.of(RequestedInformationResolver.STATUS, "ORDER_AMOUNT", "DELIVERY_DATE"),
                resolver.resolveForSearch("Show order status, amount and delivery date", List.of())
        );
    }

    @Test
    void resolveForSearch_statusSalespersonAndDeliveryDate_returnsAllInOrder() {
        assertEquals(
                List.of(RequestedInformationResolver.STATUS, "SALESPERSON", "DELIVERY_DATE"),
                resolver.resolveForSearch("Show status, salesperson and delivery date", List.of())
        );
    }

    @Test
    void resolveForSearch_email_returnsEmail() {
        assertEquals(
                List.of(RequestedInformationResolver.EMAIL),
                resolver.resolveForSearch("Show email for customer orders", List.of())
        );
    }

    @Test
    void resolveForSearch_paymentTerms_returnsPaymentTerms() {
        assertEquals(
                List.of("PAYMENT_TERMS"),
                resolver.resolveForSearch("Show payment terms for customer orders", List.of())
        );
    }

    @Test
    void resolveForSearch_statusAndEmail_keepsUnsupportedEmailInRequestedInformation() {
        assertEquals(
                List.of(RequestedInformationResolver.STATUS, RequestedInformationResolver.EMAIL),
                resolver.resolveForSearch("Show order status and email", List.of())
        );
    }

    @Test
    void resolve_paymentTerms_returnsPaymentTerms() {
        assertEquals(
                List.of("PAYMENT_TERMS"),
                resolver.resolve("Show payment terms for customer", "GetCustomer", Map.of())
        );
    }

    @Test
    void resolve_creditLimit_returnsCreditLimit() {
        assertEquals(
                List.of(RequestedInformationResolver.CREDIT_LIMIT),
                resolver.resolve("Show credit limit for customer Y11100", "GetCustomerFinancial", Map.of())
        );
    }

    @Test
    void resolve_creditLimitAndPaymentTerms_returnsBothInOrder() {
        assertEquals(
                List.of(RequestedInformationResolver.CREDIT_LIMIT, "PAYMENT_TERMS"),
                resolver.resolve(
                        "Show credit limit and payment terms for customer Y11100",
                        "GetCustomerFinancial",
                        Map.of()
                )
        );
    }

    @Test
    void resolve_groupPayer_notPlainPayer() {
        assertEquals(
                List.of(RequestedInformationResolver.GROUP_PAYER),
                resolver.resolve("Show group payer for customer", "GetCustomerFinancial", Map.of())
        );
    }

    @Test
    void resolve_payerAlone_returnsPayer() {
        assertEquals(
                List.of(RequestedInformationResolver.PAYER),
                resolver.resolve("Show payer for customer", "GetCustomerFinancial", Map.of())
        );
    }

    @Test
    void resolve_paymentAndCurrency_returnsBoth() {
        assertEquals(
                List.of(RequestedInformationResolver.PAYMENT, RequestedInformationResolver.CURRENCY),
                resolver.resolve("Show payment and currency for customer", "GetCustomerFinancial", Map.of())
        );
    }
}
