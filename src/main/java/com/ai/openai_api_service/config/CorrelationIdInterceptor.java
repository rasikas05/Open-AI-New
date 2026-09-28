package com.ai.openai_api_service.config;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Propagates MDC correlation id as {@code X-Correlation-Id} on outbound RestTemplate calls.
 * Does not overwrite an existing header or touch {@code x-api-key}.
 */
public final class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        String id = MDC.get(CorrelationIdSupport.MDC_KEY);
        if (id != null && !id.isBlank()
                && !request.getHeaders().containsKey(CorrelationIdSupport.HEADER)) {
            request.getHeaders().set(CorrelationIdSupport.HEADER, id.trim());
        }
        return execution.execute(request, body);
    }
}
