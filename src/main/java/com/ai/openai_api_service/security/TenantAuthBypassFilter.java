package com.ai.openai_api_service.security;

import com.ai.openai_api_service.config.RequiredM2mScope;
import com.ai.openai_api_service.service.TenantAuthBypassDecision;
import com.ai.openai_api_service.service.TenantAuthBypassService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * TEMPORARY DEMO: when no Bearer JWT is present, allowlisted ACTIVE tenants may authenticate
 * via {@code X-Tenant-Code} with a synthetic principal {@code tenant-auth-bypass}.
 * <p>
 * Success: {@code TENANT_AUTH_BYPASS_USED}. Skip reasons: {@code TENANT_AUTH_BYPASS_SKIP}
 * (never logs Authorization tokens).
 */
public class TenantAuthBypassFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantAuthBypassFilter.class);

    private final TenantAuthBypassService bypassService;
    private final RequiredM2mScope requiredM2mScope;

    public TenantAuthBypassFilter(TenantAuthBypassService bypassService, RequiredM2mScope requiredM2mScope) {
        this.bypassService = bypassService;
        this.requiredM2mScope = requiredM2mScope;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (hasBearerToken(request)) {
            log.debug(
                    "TENANT_AUTH_BYPASS_SKIP reason=bearer_present path={} method={}",
                    path,
                    method
            );
            filterChain.doFilter(request, response);
            return;
        }

        if (!bypassService.isFeatureEnabled()) {
            log.debug(
                    "TENANT_AUTH_BYPASS_SKIP reason=feature_disabled path={} method={}",
                    path,
                    method
            );
            filterChain.doFilter(request, response);
            return;
        }

        String headerName = bypassService.headerName();
        String tenantCode = request.getHeader(headerName);
        if (tenantCode == null || tenantCode.isBlank()) {
            logSkip("missing_header", path, method, headerName, null);
            filterChain.doFilter(request, response);
            return;
        }

        String trimmed = tenantCode.trim();
        TenantAuthBypassDecision decision = bypassService.evaluate(trimmed);
        if (decision != TenantAuthBypassDecision.ALLOWED) {
            String reason = decision == TenantAuthBypassDecision.NOT_ALLOWLISTED
                    ? "not_allowlisted"
                    : "tenant_missing_or_inactive";
            logSkip(reason, path, method, null, trimmed);
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            TenantAuthBypassSupport.markActive(request, trimmed);
            TenantAuthBypassAuthentication authentication = new TenantAuthBypassAuthentication(
                    trimmed,
                    List.of(new SimpleGrantedAuthority(requiredM2mScope.getAuthority()))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info(
                    "TENANT_AUTH_BYPASS_USED tenant={} path={} method={}",
                    trimmed,
                    path,
                    method
            );
        }

        filterChain.doFilter(request, response);
    }

    private static void logSkip(
            String reason,
            String path,
            String method,
            String header,
            String tenant
    ) {
        if (header != null) {
            log.info(
                    "TENANT_AUTH_BYPASS_SKIP reason={} header={} path={} method={}",
                    reason,
                    header,
                    path,
                    method
            );
        } else if (tenant != null) {
            log.info(
                    "TENANT_AUTH_BYPASS_SKIP reason={} tenant={} path={} method={}",
                    reason,
                    tenant,
                    path,
                    method
            );
        } else {
            log.info(
                    "TENANT_AUTH_BYPASS_SKIP reason={} path={} method={}",
                    reason,
                    path,
                    method
            );
        }
    }

    private static boolean hasBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    }
}
