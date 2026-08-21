package com.ai.openai_api_service.service.timing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRequestSummaryLogTest {

    @Test
    void formatChat_includesIntentAndAction() {
        String line = ChatRequestSummaryLog.formatChat(
                "show customer orders for Y11100",
                "AUTO",
                "LIVE_M3",
                "live",
                "lex",
                "SearchCustomerOrder",
                "search"
        );
        assertTrue(line.startsWith("[CHAT]"));
        assertTrue(line.contains("mode=AUTO"));
        assertTrue(line.contains("type=LIVE_M3"));
        assertTrue(line.contains("intent=SearchCustomerOrder"));
        assertTrue(line.contains("action=search"));
    }

    @Test
    void formatTiming_usesProvidedBucketsOnly() {
        String line = ChatRequestSummaryLog.formatTiming(120, 655, 2941, 0, 0, 0, 690, 400, 5000);
        assertTrue(line.startsWith("[TIMING]"));
        assertTrue(line.contains("python=0.12s"));
        assertTrue(line.contains("pii=0.66s"));
        assertTrue(line.contains("openai=2.94s"));
        assertTrue(line.contains("suggestions=0.40s"));
        assertTrue(line.contains("total=5.00s"));
    }

    @Test
    void formatTokens_listsStages() {
        String line = ChatRequestSummaryLog.formatTokens(200, 3500, 100, 40, 3840);
        assertTrue(line.startsWith("[TOKENS]"));
        assertTrue(line.contains("router=200"));
        assertTrue(line.contains("grounding=3500"));
        assertTrue(line.contains("suggestions=40"));
        assertTrue(line.contains("total=3840"));
    }
}
