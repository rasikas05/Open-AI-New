package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexCustomerMasterIntentGuardTest {

    @Test
    void remapsSearchCustomerOrderToGetCustomerForFetchCustomerId() {
        LexRecognizeResult lex = searchOrder("CustomerNumber", "Y11100");
        LexRecognizeResult applied = LexCustomerMasterIntentGuard.apply("fetch customer Y11100", lex);
        assertEquals("GetCustomer", applied.getIntentName());
        assertEquals("Y11100", applied.getSlots().get("CustomerNumber"));
    }

    @Test
    void remapsShowCustomerId() {
        LexRecognizeResult applied = LexCustomerMasterIntentGuard.apply(
                "show customer Y11100",
                searchOrder("CustomerOrderNumber", "Y11100")
        );
        assertEquals("GetCustomer", applied.getIntentName());
        assertEquals("Y11100", applied.getSlots().get("CustomerNumber"));
    }

    @Test
    void leavesSearchCustomerOrderWhenOrderWordsPresent() {
        LexRecognizeResult lex = searchOrder("CustomerNumber", "Y11100");
        assertSame(lex, LexCustomerMasterIntentGuard.apply("show customer orders for Y11100", lex));
        assertSame(lex, LexCustomerMasterIntentGuard.apply("fetch customer orders for Y11100", lex));
        assertSame(lex, LexCustomerMasterIntentGuard.apply("show customer order for Y11100", lex));
        assertSame(lex, LexCustomerMasterIntentGuard.apply("sales order for Y11100", lex));
        assertSame(lex, LexCustomerMasterIntentGuard.apply("order number 1000001234", lex));
        assertSame(lex, LexCustomerMasterIntentGuard.apply("show CO for Y11100", lex));
    }

    @Test
    void companyDoesNotCountAsCoToken() {
        assertTrue(LexCustomerMasterIntentGuard.isCustomerMasterUtterance("fetch customer Y11100 for company"));
        assertFalse(LexCustomerMasterIntentGuard.isCustomerMasterUtterance("fetch customer orders for Y11100"));
    }

    @Test
    void doesNotTouchGetCustomerOrOtherIntents() {
        LexRecognizeResult getCustomer = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "Y11100"),
                List.of()
        );
        assertSame(getCustomer, LexCustomerMasterIntentGuard.apply("fetch customer Y11100", getCustomer));
    }

    private static LexRecognizeResult searchOrder(String slot, String value) {
        return new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of(slot, value),
                List.of()
        );
    }
}
