package com.ai.openai_api_service.security;

import com.ai.openai_api_service.config.RequiredM2mScope;
import com.ai.openai_api_service.service.TenantAuthBypassDecision;
import com.ai.openai_api_service.service.TenantAuthBypassService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAuthBypassFilterTest {

    private static final String DEMO = "EM47JBNURMACJZ8F_DEM";
    private static final String SCOPE = "SCOPE_default-m2m-resource-server-5kguh6/read";

    @Mock
    private TenantAuthBypassService bypassService;

    private TenantAuthBypassFilter filter;

    @BeforeEach
    void setUp() {
        RequiredM2mScope requiredM2mScope = new RequiredM2mScope("default-m2m-resource-server-5kguh6/read");
        filter = new TenantAuthBypassFilter(bypassService, requiredM2mScope);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bypassAllowed_setsSyntheticAuthentication() throws Exception {
        when(bypassService.isFeatureEnabled()).thenReturn(true);
        when(bypassService.headerName()).thenReturn("X-Tenant-Code");
        when(bypassService.evaluate(DEMO)).thenReturn(TenantAuthBypassDecision.ALLOWED);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tenant/quota");
        request.addHeader("X-Tenant-Code", DEMO);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertInstanceOf(TenantAuthBypassAuthentication.class, SecurityContextHolder.getContext().getAuthentication());
        TenantAuthBypassAuthentication auth =
                (TenantAuthBypassAuthentication) SecurityContextHolder.getContext().getAuthentication();
        assertEquals(TenantAuthBypassSupport.PRINCIPAL_NAME, auth.getPrincipal());
        assertEquals(DEMO, auth.getTenantCode());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> SCOPE.equals(a.getAuthority())));
        assertTrue(Boolean.TRUE.equals(request.getAttribute(TenantAuthBypassSupport.ATTR_ACTIVE)));
    }

    @Test
    void missingHeader_noAuthentication() throws Exception {
        when(bypassService.isFeatureEnabled()).thenReturn(true);
        when(bypassService.headerName()).thenReturn("X-Tenant-Code");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tenant/quota");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void notAllowlisted_noAuthentication() throws Exception {
        when(bypassService.isFeatureEnabled()).thenReturn(true);
        when(bypassService.headerName()).thenReturn("X-Tenant-Code");
        when(bypassService.evaluate(DEMO)).thenReturn(TenantAuthBypassDecision.NOT_ALLOWLISTED);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tenant/quota");
        request.addHeader("X-Tenant-Code", DEMO);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void tenantMissingOrInactive_noAuthentication() throws Exception {
        when(bypassService.isFeatureEnabled()).thenReturn(true);
        when(bypassService.headerName()).thenReturn("X-Tenant-Code");
        when(bypassService.evaluate(DEMO)).thenReturn(TenantAuthBypassDecision.TENANT_MISSING_OR_INACTIVE);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tenant/quota");
        request.addHeader("X-Tenant-Code", DEMO);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void bearerPresent_skipsBypass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secured/client-info");
        request.addHeader("Authorization", "Bearer sometoken");
        request.addHeader("X-Tenant-Code", DEMO);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void featureOff_skipsBypass() throws Exception {
        when(bypassService.isFeatureEnabled()).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secured/client-info");
        request.addHeader("X-Tenant-Code", DEMO);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
