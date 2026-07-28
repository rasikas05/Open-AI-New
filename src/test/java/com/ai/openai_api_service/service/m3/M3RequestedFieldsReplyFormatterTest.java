package com.ai.openai_api_service.service.m3;

import com.ai.openai_api_service.service.RequestedInformationResolver;
import com.ai.openai_api_service.service.api.ApiFieldCatalog;
import com.ai.openai_api_service.service.api.ApiFieldMetadata;
import com.ai.openai_api_service.service.api.M3ApiKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3RequestedFieldsReplyFormatterTest {

    private final M3RequestedFieldsReplyFormatter formatter = new M3RequestedFieldsReplyFormatter(
            new ApiFieldCatalog(),
            new ApiFieldMetadata()
    );

    private static final M3ApiKey GET_FINANCIAL = M3ApiKey.of("CRS610MI", "GetFinancial");

    @Test
    void format_creditLimit_singleField() {
        String reply = formatter.format(
                GET_FINANCIAL,
                List.of(RequestedInformationResolver.CREDIT_LIMIT),
                List.of("CRLM"),
                Map.of("CRLM", "50000")
        );

        assertEquals("Credit limit : 50000", reply);
    }

    @Test
    void format_paymentAndCurrency_multiField() {
        String reply = formatter.format(
                GET_FINANCIAL,
                List.of(RequestedInformationResolver.PAYMENT, RequestedInformationResolver.CURRENCY),
                List.of("TEPY", "CUCD"),
                Map.of("TEPY", "30", "CUCD", "EUR")
        );

        assertTrue(reply.contains("Payment terms : 30"));
        assertTrue(reply.contains("Currency : EUR"));
        assertTrue(reply.indexOf("Payment terms") < reply.indexOf("Currency"));
    }
}
