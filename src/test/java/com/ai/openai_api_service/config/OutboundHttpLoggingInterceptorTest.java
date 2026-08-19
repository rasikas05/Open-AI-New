package com.ai.openai_api_service.config;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OutboundHttpLoggingInterceptorTest {

    @Test
    void safeTarget_stripsQueryAndFragment() {
        URI uri = URI.create("http://localhost:8083/retrieval?customerNumber=Y00111&token=secret#frag");
        String target = OutboundHttpLoggingInterceptor.safeTarget(uri);
        assertEquals("http://localhost:8083/retrieval", target);
        assertFalse(target.contains("Y00111"));
        assertFalse(target.contains("token"));
    }

    @Test
    void logPath_openaiIsPathOnly() {
        URI uri = URI.create("https://api.openai.com/v1/chat/completions?api-version=1");
        assertEquals("/v1/chat/completions", OutboundHttpLoggingInterceptor.logPath(uri));
    }

    @Test
    void logPath_pythonKeepsHostAndPathWithoutQuery() {
        URI uri = URI.create("http://localhost:8083/retrieval?q=secret");
        assertEquals("http://localhost:8083/retrieval", OutboundHttpLoggingInterceptor.logPath(uri));
    }

    @Test
    void formatSeconds_twoDecimals() {
        assertEquals("2.41s", OutboundHttpLoggingInterceptor.formatSeconds(2410));
        assertEquals("0.32s", OutboundHttpLoggingInterceptor.formatSeconds(320));
    }
}
