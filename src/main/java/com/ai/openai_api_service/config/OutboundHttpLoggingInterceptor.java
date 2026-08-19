package com.ai.openai_api_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

/**
 * Logs method + path (no query, body, or headers). Does not swallow failures.
 */
public final class OutboundHttpLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger HTTP = LoggerFactory.getLogger("HTTP");

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        long start = System.currentTimeMillis();
        String target = logPath(request.getURI());
        String method = request.getMethod() != null ? request.getMethod().name() : "HTTP";
        try {
            ClientHttpResponse response = execution.execute(request, body);
            int status = response.getStatusCode().value();
            HTTP.info("[HTTP] {} {} -> {} | {}", method, target, status, formatSeconds(elapsed(start)));
            return response;
        } catch (IOException e) {
            HTTP.info("[HTTP] {} {} -> ERROR | {}", method, target, formatSeconds(elapsed(start)));
            throw e;
        } catch (RuntimeException e) {
            HTTP.info("[HTTP] {} {} -> ERROR | {}", method, target, formatSeconds(elapsed(start)));
            throw e;
        }
    }

    static String logPath(URI uri) {
        if (uri == null) {
            return "-";
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        if (host.contains("openai.com") || host.contains("openai.azure.com")) {
            return path;
        }
        return safeTarget(uri);
    }

    static String safeTarget(URI uri) {
        if (uri == null) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        if (uri.getScheme() != null) {
            builder.append(uri.getScheme()).append("://");
        }
        if (uri.getHost() != null) {
            builder.append(uri.getHost());
        }
        if (uri.getPort() > 0) {
            builder.append(':').append(uri.getPort());
        }
        if (uri.getRawPath() != null && !uri.getRawPath().isBlank()) {
            builder.append(uri.getRawPath());
        }
        return builder.length() > 0 ? builder.toString() : "-";
    }

    static String formatSeconds(long millis) {
        return String.format(Locale.ROOT, "%.2fs", Math.max(0L, millis) / 1000.0);
    }

    private static long elapsed(long start) {
        return Math.max(0L, System.currentTimeMillis() - start);
    }
}
