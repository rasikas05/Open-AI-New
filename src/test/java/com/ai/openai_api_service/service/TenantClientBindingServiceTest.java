package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.repository.TenantRepository;
import com.ai.openai_api_service.security.TenantAuthBypassSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantClientBindingServiceTest {

    private static final String CLIENT_A = "client-a";
    private static final String CLIENT_B = "client-b";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Mock
    private TenantRepository tenantRepository;

    private TenantClientBindingService service;

    @BeforeEach
    void setUp() {
        service = new TenantClientBindingService(tenantRepository);
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void clientA_tenantA_allowed() {
        when(tenantRepository.findByCognitoClientId(CLIENT_A)).thenReturn(Optional.of(tenant(TENANT_A, CLIENT_A)));

        assertDoesNotThrow(() -> service.assertClientOwnsTenant(CLIENT_A, TENANT_A));
        assertDoesNotThrow(() -> service.assertClientOwnsTenantCode(CLIENT_A, TENANT_A));
        assertDoesNotThrow(() -> service.assertClientOwnsTenantId(CLIENT_A, TENANT_A));
    }

    @Test
    void clientA_tenantB_forbidden() {
        when(tenantRepository.findByCognitoClientId(CLIENT_A)).thenReturn(Optional.of(tenant(TENANT_A, CLIENT_A)));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.assertClientOwnsTenant(CLIENT_A, TENANT_B)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals(TenantClientBindingService.FORBIDDEN_MESSAGE, ex.getReason());
    }

    @Test
    void clientB_tenantA_forbidden() {
        when(tenantRepository.findByCognitoClientId(CLIENT_B)).thenReturn(Optional.of(tenant(TENANT_B, CLIENT_B)));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.assertClientOwnsTenant(CLIENT_B, TENANT_A)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void unknownClient_forbidden() {
        when(tenantRepository.findByCognitoClientId("unknown")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.assertClientOwnsTenant("unknown", TENANT_A)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void missingClientId_forbidden() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.assertClientOwnsTenant("  ", TENANT_A)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void jwtMissingClientIdClaim_forbidden() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claims(c -> c.putAll(Map.of("sub", "x")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.assertAuthorizedForTenant(jwt, TENANT_A)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void bypass_matchingTenant_allowed() {
        markBypass(TENANT_A);
        assertDoesNotThrow(() -> service.assertAuthorizedForTenant(null, TENANT_A));
    }

    @Test
    void bypass_crossTenant_forbidden() {
        markBypass(TENANT_A);
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.assertAuthorizedForTenant(null, TENANT_B)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    private static void markBypass(String tenantCode) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        TenantAuthBypassSupport.markActive(request, tenantCode);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static Tenant tenant(String code, String clientId) {
        Tenant t = new Tenant();
        t.setTenantCode(code);
        t.setCognitoClientId(clientId);
        return t;
    }
}
