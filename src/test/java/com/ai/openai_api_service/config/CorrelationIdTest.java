package com.ai.openai_api_service.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationIdSupportTest {

    @AfterEach
    void clearMdc() {
        CorrelationIdSupport.clear();
    }

    @Test
    void resolveOrCreate_reusesInbound() {
        assertEquals("abc-123", CorrelationIdSupport.resolveOrCreate("  abc-123  "));
    }

    @Test
    void resolveOrCreate_generatesWhenBlank() {
        String a = CorrelationIdSupport.resolveOrCreate(null);
        String b = CorrelationIdSupport.resolveOrCreate("  ");
        assertNotNull(a);
        assertFalse(a.isBlank());
        assertNotNull(b);
        assertNotEquals(a, b);
    }

    @Test
    void current_isIdempotentWithinMdc() {
        String first = CorrelationIdSupport.current();
        String second = CorrelationIdSupport.current();
        assertEquals(first, second);
        assertEquals(first, MDC.get(CorrelationIdSupport.MDC_KEY));
    }

    @Test
    void current_preservesPreSetMdc() {
        String preset = UUID.randomUUID().toString();
        MDC.put(CorrelationIdSupport.MDC_KEY, preset);
        assertEquals(preset, CorrelationIdSupport.current());
        assertEquals(preset, CorrelationIdSupport.current());
    }

    @Test
    void putRequestIds_storesAndClearRemoves() {
        CorrelationIdSupport.putRequestIds("sess-1", "user-9");
        assertEquals("sess-1", MDC.get(CorrelationIdSupport.MDC_SESSION_ID));
        assertEquals("user-9", MDC.get(CorrelationIdSupport.MDC_USER_ID));
        CorrelationIdSupport.clearRequestIds();
        assertNull(MDC.get(CorrelationIdSupport.MDC_SESSION_ID));
        assertNull(MDC.get(CorrelationIdSupport.MDC_USER_ID));
    }

    @Test
    void clear_removesCorrelationAndRequestIds() {
        MDC.put(CorrelationIdSupport.MDC_KEY, "corr-1");
        CorrelationIdSupport.putRequestIds("s", "u");
        CorrelationIdSupport.clear();
        assertNull(MDC.get(CorrelationIdSupport.MDC_KEY));
        assertNull(MDC.get(CorrelationIdSupport.MDC_SESSION_ID));
        assertNull(MDC.get(CorrelationIdSupport.MDC_USER_ID));
    }

    @Test
    void callWithMdc_installsCapturedParentContext() {
        MDC.put(CorrelationIdSupport.MDC_KEY, "parent-corr");
        CorrelationIdSupport.putRequestIds("parent-sess", "parent-user");
        var parentSnapshot = CorrelationIdSupport.copyContextMap();

        MDC.clear();
        assertNull(MDC.get(CorrelationIdSupport.MDC_KEY));

        AtomicReference<String> seenCorr = new AtomicReference<>();
        AtomicReference<String> seenSession = new AtomicReference<>();
        AtomicReference<String> seenUser = new AtomicReference<>();

        String result = CorrelationIdSupport.callWithMdc(parentSnapshot, () -> {
            seenCorr.set(MDC.get(CorrelationIdSupport.MDC_KEY));
            seenSession.set(MDC.get(CorrelationIdSupport.MDC_SESSION_ID));
            seenUser.set(MDC.get(CorrelationIdSupport.MDC_USER_ID));
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals("parent-corr", seenCorr.get());
        assertEquals("parent-sess", seenSession.get());
        assertEquals("parent-user", seenUser.get());
        assertNull(MDC.get(CorrelationIdSupport.MDC_KEY));
    }
}

class CorrelationIdFilterTest {

    @AfterEach
    void clearMdc() {
        CorrelationIdSupport.clear();
    }

    @Test
    void generatesIdWhenMissingAndClearsMdc() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> during = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            during.set(MDC.get(CorrelationIdSupport.MDC_KEY));
        });

        String header = response.getHeader(CorrelationIdSupport.HEADER);
        assertNotNull(header);
        assertFalse(header.isBlank());
        assertEquals(header, during.get());
        assertNull(MDC.get(CorrelationIdSupport.MDC_KEY));
    }

    @Test
    void reusesInboundHeader() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdSupport.HEADER, "client-corr-99");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> during = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            during.set(MDC.get(CorrelationIdSupport.MDC_KEY));
        });

        assertEquals("client-corr-99", response.getHeader(CorrelationIdSupport.HEADER));
        assertEquals("client-corr-99", during.get());
        assertNull(MDC.get(CorrelationIdSupport.MDC_KEY));
    }
}

class CorrelationIdInterceptorTest {

    @AfterEach
    void clearMdc() {
        CorrelationIdSupport.clear();
    }

    @Test
    void setsHeaderFromMdcWhenAbsent() throws Exception {
        MDC.put(CorrelationIdSupport.MDC_KEY, "mdc-id-1");
        MockClientHttpRequest request = new MockClientHttpRequest();
        CorrelationIdInterceptor interceptor = new CorrelationIdInterceptor();

        interceptor.intercept(request, new byte[0], (req, body) -> {
            assertEquals("mdc-id-1", req.getHeaders().getFirst(CorrelationIdSupport.HEADER));
            return null;
        });
    }

    @Test
    void doesNotOverwriteExistingHeader() throws Exception {
        MDC.put(CorrelationIdSupport.MDC_KEY, "mdc-id-1");
        MockClientHttpRequest request = new MockClientHttpRequest();
        request.getHeaders().set(CorrelationIdSupport.HEADER, "already-set");
        CorrelationIdInterceptor interceptor = new CorrelationIdInterceptor();

        interceptor.intercept(request, new byte[0], (req, body) -> {
            assertEquals("already-set", req.getHeaders().getFirst(CorrelationIdSupport.HEADER));
            return null;
        });
    }

    @Test
    void skipsWhenMdcEmpty() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest();
        CorrelationIdInterceptor interceptor = new CorrelationIdInterceptor();

        interceptor.intercept(request, new byte[0], (req, body) -> {
            assertFalse(req.getHeaders().containsKey(CorrelationIdSupport.HEADER));
            return null;
        });
    }

    @Test
    void restTemplateFactoryRegistersCorrelationBeforeHttpLogging() {
        var restTemplate = RestTemplateFactory.create(1000);
        var interceptors = restTemplate.getInterceptors();
        assertTrue(interceptors.size() >= 2);
        assertTrue(interceptors.get(0) instanceof CorrelationIdInterceptor);
        assertTrue(interceptors.get(1) instanceof OutboundHttpLoggingInterceptor);
    }
}
