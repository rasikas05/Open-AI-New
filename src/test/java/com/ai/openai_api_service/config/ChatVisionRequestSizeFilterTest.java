package com.ai.openai_api_service.config;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatVisionRequestSizeFilterTest {

    /** Small totals for oversize Content-Length / chunked tests: encodedMax(3)=4 + 10 = 14. */
    private static final long TINY_TOTAL_MAX = 14L;
    /** Room for normal text-only ChatRequest JSON in pass-through tests. */
    private static final long ROOMY_OVERHEAD = 65_536L;

    private ChatVisionRequestSizeFilter tinyFilter;
    private ChatVisionRequestSizeFilter roomyFilter;

    @BeforeEach
    void setUp() {
        // maxImages=1 keeps tiny total = encoded(3)+10 = 14 for existing oversize tests
        tinyFilter = new ChatVisionRequestSizeFilter(3L, 1, 10L);
        roomyFilter = new ChatVisionRequestSizeFilter(3L, 3, ROOMY_OVERHEAD);
        assertEquals(TINY_TOTAL_MAX, tinyFilter.maxBodyBytes());
    }

    @Test
    void productionDefaults_bodyBudgetMatchesThreeImageFormula() {
        ChatVisionRequestSizeFilter production = new ChatVisionRequestSizeFilter(
                4_194_304L, 3, 262_144L);
        assertEquals(17_039_368L, production.maxBodyBytes());
    }

    @Test
    void contentLengthOverTotalMax_returns413WithoutInvokingChain() throws Exception {
        MockHttpServletRequest request = postComprehend();
        byte[] body = new byte[(int) TINY_TOTAL_MAX + 1];
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        tinyFilter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertFalse(chainCalled.get());
        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, response.getStatus());
        assertEquals(ChatVisionRequestSizeFilter.SAFE_ERROR_JSON, response.getContentAsString());
        assertFalse(response.getContentAsString().contains("AAAA"));
    }

    @Test
    void contentLengthUnderTotalMax_passesThrough() throws Exception {
        MockHttpServletRequest request = postComprehend();
        request.setContent("{\"userMessage\":\"hi\"}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        roomyFilter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void chunkedUnknownContentLength_enforcesLimitWhileReading() throws Exception {
        MockHttpServletRequest mock = postComprehend();
        byte[] oversized = new byte[(int) TINY_TOTAL_MAX + 5];
        mock.setContent(oversized);
        // Simulate Transfer-Encoding: chunked (unknown length) while still providing a body stream.
        HttpServletRequest chunked = new HttpServletRequestWrapper(mock) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1L;
            }
        };

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Exception> fromChain = new AtomicReference<>();

        tinyFilter.doFilter(chunked, response, (req, res) -> {
            try {
                ServletInputStream in = req.getInputStream();
                byte[] buf = new byte[32];
                while (in.read(buf) != -1) {
                    // drain until limit throws
                }
            } catch (Exception e) {
                fromChain.set(e);
                throw e;
            }
        });

        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, response.getStatus());
        assertEquals(ChatVisionRequestSizeFilter.SAFE_ERROR_JSON, response.getContentAsString());
        assertTrue(
                fromChain.get() == null || fromChain.get() instanceof RequestBodyTooLargeException,
                "chain should see RequestBodyTooLargeException or filter should have handled it"
        );
    }

    @Test
    void nonComprehendPath_notFiltered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat");
        byte[] body = new byte[(int) TINY_TOTAL_MAX + 100];
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        tinyFilter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void feedbackSubpath_notFiltered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat/comprehend/feedback");
        byte[] body = new byte[(int) TINY_TOTAL_MAX + 100];
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        tinyFilter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
    }

    @Test
    void contextPathComprehend_isFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/m3bot/api/chat/comprehend");
        request.setContextPath("/m3bot");

        assertTrue(ChatVisionRequestSizeFilter.isComprehendChatPath(request));
    }

    @Test
    void contextPathFeedback_isNotFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/m3bot/api/chat/comprehend/feedback");
        request.setContextPath("/m3bot");

        assertFalse(ChatVisionRequestSizeFilter.isComprehendChatPath(request));
    }

    @Test
    void contextPathApiChat_isNotFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/m3bot/api/chat");
        request.setContextPath("/m3bot");

        assertFalse(ChatVisionRequestSizeFilter.isComprehendChatPath(request));
    }

    @Test
    void trailingSlashComprehend_isFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat/comprehend/");

        assertTrue(ChatVisionRequestSizeFilter.isComprehendChatPath(request));
    }

    @Test
    void textOnlyUnderLimit_unaffected() throws Exception {
        MockHttpServletRequest request = postComprehend();
        request.setContent("{\"userMessage\":\"how to create customer\"}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        roomyFilter.doFilter(request, response, (req, res) -> {
            chainCalled.set(true);
            assertEquals(
                    "{\"userMessage\":\"how to create customer\"}",
                    new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
            );
        });

        assertTrue(chainCalled.get());
    }

    private static MockHttpServletRequest postComprehend() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat/comprehend");
        request.setContentType("application/json");
        return request;
    }
}
