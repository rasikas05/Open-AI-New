package com.ai.openai_api_service.config;

import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

public final class RestTemplateFactory {

    private RestTemplateFactory() {
    }

    public static RestTemplate create(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        RestTemplate restTemplate = new RestTemplate(factory);
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());
        // Correlation first so outbound HTTP logs run after the header is set.
        interceptors.add(new CorrelationIdInterceptor());
        interceptors.add(new OutboundHttpLoggingInterceptor());
        restTemplate.setInterceptors(interceptors);
        return restTemplate;
    }
}
