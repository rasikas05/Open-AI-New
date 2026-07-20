package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestedInformationResolverTest {

    private final RequestedInformationResolver resolver =
            new RequestedInformationResolver(new SearchFieldCatalog());

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
}
