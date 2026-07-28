package com.ai.openai_api_service.service.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InformationRequestCatalogTest {

    private final InformationRequestCatalog catalog = new InformationRequestCatalog();

    @Test
    void matchCodes_netOrderValue() {
        assertEquals(List.of("NET_ORDER_VALUE"), catalog.matchCodesFromUtterance("Show net order value for order"));
    }

    @Test
    void matchCodes_deliveryMethod() {
        assertEquals(List.of("DELIVERY_METHOD"), catalog.matchCodesFromUtterance("Show delivery method"));
    }

    @Test
    void matchCodes_buyerOnPurchaseContext() {
        List<String> codes = catalog.matchCodesFromUtterance("Show buyer for purchase order");
        assertFalse(codes.isEmpty());
        assertEquals("BUYER", codes.getFirst());
    }

    @Test
    void matchCodes_referenceOrderNumber() {
        assertEquals(
                List.of("REFERENCE_ORDER_NUMBER"),
                catalog.matchCodesFromUtterance("Show reference order number CO12345")
        );
    }

    @Test
    void matchCodes_countryOnCustomer() {
        assertEquals(List.of("COUNTRY"), catalog.matchCodesFromUtterance("Show country for customer"));
    }
}
