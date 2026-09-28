package com.ai.openai_api_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures every request has an {@code X-Correlation-Id} in MDC and on the response.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String id = CorrelationIdSupport.resolveOrCreate(request.getHeader(CorrelationIdSupport.HEADER));
        MDC.put(CorrelationIdSupport.MDC_KEY, id);
        response.setHeader(CorrelationIdSupport.HEADER, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationIdSupport.clear();
        }
    }
}
