package com.ai.openai_api_service.service.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceholderFormatterTest {

    @Test
    void format_customerNumber_usesAngleBracketsUppercase() {
        PlaceholderFormatter formatter = new PlaceholderFormatter();
        assertEquals("<CUSTOMER_NUMBER>", formatter.format("Customer Number"));
    }

    @Test
    void format_blank_usesRedacted() {
        PlaceholderFormatter formatter = new PlaceholderFormatter();
        assertEquals("<REDACTED>", formatter.format(" "));
    }
}
