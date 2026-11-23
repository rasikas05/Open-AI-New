package com.ai.openai_api_service.config;

import com.ai.openai_api_service.service.vision.VisionRequestSizeLimits;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Enforces the <b>total HTTP body</b> limit for {@code POST /api/chat/comprehend}.
 *
 * <p>Distinct from image limits in {@link VisionRequestSizeLimits}:
 * <ul>
 *   <li>Decoded / encoded image caps — {@code ImageAttachmentValidator}</li>
 *   <li>Total body = encoded max + {@code chat.vision.max-json-overhead-bytes} — this filter</li>
 * </ul>
 *
 * <p>When Content-Length is known and over the limit, rejects immediately with 413.
 * Otherwise wraps the input stream so chunked / unknown-length bodies are still capped while read.
 * Never logs request body or Base64.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class ChatVisionRequestSizeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ChatVisionRequestSizeFilter.class);

    static final String SAFE_ERROR_JSON =
            "{\"error\":\"" + RequestBodyTooLargeException.SAFE_MESSAGE + "\",\"status\":413}";

    private final long maxBodyBytes;

    public ChatVisionRequestSizeFilter(
            @Value("${chat.vision.max-bytes:4194304}") long maxBytes,
            @Value("${chat.vision.max-images:3}") int maxImages,
            @Value("${chat.vision.max-json-overhead-bytes:262144}") long jsonOverheadBytes
    ) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("chat.vision.max-bytes must be positive");
        }
        if (maxImages <= 0) {
            throw new IllegalArgumentException("chat.vision.max-images must be positive");
        }
        if (jsonOverheadBytes < 0) {
            throw new IllegalArgumentException("chat.vision.max-json-overhead-bytes must be non-negative");
        }
        this.maxBodyBytes = VisionRequestSizeLimits.maxHttpBodyBytes(maxImages, maxBytes, jsonOverheadBytes);
    }

    long maxBodyBytes() {
        return maxBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !isComprehendChatPath(request);
    }

    /**
     * Matches only the chat turn endpoint after stripping servlet context-path.
     * Does not match {@code /api/chat}, {@code /api/chat/comprehend/feedback}, or other suffixes.
     */
    static boolean isComprehendChatPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        String contextPath = request.getContextPath();
        String path = uri;
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.isEmpty()) {
            return false;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return "/api/chat/comprehend".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBodyBytes) {
            log.info(
                    "Rejected comprehend chat request: Content-Length={} exceeds maxBodyBytes={}",
                    contentLength,
                    maxBodyBytes
            );
            writePayloadTooLarge(response);
            return;
        }

        HttpServletRequest wrapped = new LimitedContentLengthRequestWrapper(request, maxBodyBytes);
        try {
            filterChain.doFilter(wrapped, response);
        } catch (RequestBodyTooLargeException tooLarge) {
            handleBodyTooLarge(response, tooLarge);
        } catch (ServletException | IOException ex) {
            if (findBodyTooLarge(ex) != null) {
                handleBodyTooLarge(response, findBodyTooLarge(ex));
                return;
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (findBodyTooLarge(ex) != null) {
                handleBodyTooLarge(response, findBodyTooLarge(ex));
                return;
            }
            throw ex;
        }
    }

    private void handleBodyTooLarge(HttpServletResponse response, RequestBodyTooLargeException cause)
            throws IOException {
        if (response.isCommitted()) {
            throw cause;
        }
        log.info("Rejected comprehend chat request: body read exceeded maxBodyBytes={}", maxBodyBytes);
        writePayloadTooLarge(response);
    }

    private static void writePayloadTooLarge(HttpServletResponse response) throws IOException {
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        byte[] bytes = SAFE_ERROR_JSON.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }

    private static RequestBodyTooLargeException findBodyTooLarge(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof RequestBodyTooLargeException tooLarge) {
                return tooLarge;
            }
            current = current.getCause();
        }
        return null;
    }
}
