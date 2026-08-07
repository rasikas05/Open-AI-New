package com.ai.openai_api_service.config;

import com.ai.openai_api_service.service.timing.ComprehendChatTimingSnapshot;
import com.ai.openai_api_service.service.timing.RequestTimingLog;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Phase 4: measures response serialization / flush after ComprehendChatService returns,
 * and logs the end-to-end residual including that bucket.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class ComprehendChatTimingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ComprehendChatTimingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        // Exact chat endpoint only (not /history, /sessions, …)
        return !uri.endsWith("/api/chat/comprehend") && !uri.endsWith("/api/chat/comprehend/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Instant filterStart = Instant.now();
        ComprehendChatTimingSnapshot snapshot = new ComprehendChatTimingSnapshot();
        snapshot.setFilterStart(filterStart);
        request.setAttribute(RequestTimingLog.REQUEST_ATTR, snapshot);

        try {
            filterChain.doFilter(request, response);
        } finally {
            Instant filterEnd = Instant.now();
            long filterTotalMs = RequestTimingLog.durationMs(filterStart, filterEnd);

            Instant serviceEnd = snapshot.getServiceEnd();
            long responseSerializeMs = serviceEnd != null
                    ? RequestTimingLog.durationMs(serviceEnd, filterEnd)
                    : 0L;
            RequestTimingLog.logStage("responseSerialize", serviceEnd != null ? serviceEnd : filterEnd, filterEnd);

            long controllerPreServiceMs = 0L;
            if (snapshot.getServiceStart() != null) {
                controllerPreServiceMs = RequestTimingLog.durationMs(filterStart, snapshot.getServiceStart());
                RequestTimingLog.logStage("controllerPreService", filterStart, snapshot.getServiceStart());
            }

            long measuredSum = snapshot.measuredSumWithoutSerialize()
                    + responseSerializeMs
                    + controllerPreServiceMs;
            // Prefer filter-wall as honest E2E total (includes serialization)
            RequestTimingLog.Residual residual = RequestTimingLog.computeResidual(measuredSum, filterTotalMs);
            RequestTimingLog.logResidual(residual, "filterWall");

            log.info(
                    "Request Timing Summary E2E | pii={}ms | route={}ms | rewrite={}ms | retrieval={}ms | "
                            + "grounded={}ms | gapFill={}ms | generalGPT={}ms | persistence={}ms | "
                            + "suggestions={}ms | liveHistory={}ms | restore={}ms | preRetrievalGlue={}ms | "
                            + "controllerPreService={}ms | responseSerialize={}ms | "
                            + "serviceTotal={}ms | filterTotal={}ms",
                    snapshot.getPiiMs(),
                    snapshot.getRouteMs(),
                    snapshot.getRewriteMs(),
                    snapshot.getRetrievalSpringMs(),
                    snapshot.getGroundedMs(),
                    snapshot.getGapFillMs(),
                    snapshot.getGeneralGptMs(),
                    snapshot.getPersistenceMs(),
                    snapshot.getSuggestionsMs(),
                    snapshot.getLiveHistoryMs(),
                    snapshot.getRestoreMs(),
                    snapshot.getPreRetrievalGlueMs(),
                    controllerPreServiceMs,
                    responseSerializeMs,
                    snapshot.getServiceTotalMs(),
                    filterTotalMs
            );
        }
    }
}
