package com.ai.openai_api_service.security;

import com.ai.openai_api_service.config.RequiredM2mScope;
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
        if (hasBearerToken(request) || !bypassService.isFeatureEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerName = bypassService.headerName();
        String tenantCode = request.getHeader(headerName);
        if (tenantCode == null || tenantCode.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String trimmed = tenantCode.trim();
        if (!bypassService.isBypassAllowed(trimmed)) {
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
                    request.getRequestURI(),
                    request.getMethod()
            );
        }

        filterChain.doFilter(request, response);
    }

    private static boolean hasBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    }
}
