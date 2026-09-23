package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Enforces JWT Cognito {@code client_id} → tenant binding for secured tenant-scoped APIs.
 * API {@code tenantId} parameters are tenant codes (strings), not numeric PKs.
 */
@Service
public class TenantClientBindingService {

    public static final String FORBIDDEN_MESSAGE = "Client is not authorized for this tenant";

    private final TenantRepository tenantRepository;

    public TenantClientBindingService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public void assertClientOwnsTenant(Jwt jwt, String requestedTenantCode) {
        String clientId = jwt == null ? null : jwt.getClaimAsString("client_id");
        assertClientOwnsTenant(clientId, requestedTenantCode);
    }

    /** Call-site alias when the API field is named {@code tenantCode}. */
    public void assertClientOwnsTenantCode(String clientId, String tenantCode) {
        assertClientOwnsTenant(clientId, tenantCode);
    }

    /**
     * Call-site alias when the API field is named {@code tenantId}.
     * In this codebase {@code tenantId} is the tenant code string.
     */
    public void assertClientOwnsTenantId(String clientId, String tenantId) {
        assertClientOwnsTenant(clientId, tenantId);
    }

    public void assertClientOwnsTenant(String clientId, String requestedTenantCode) {
        if (clientId == null || clientId.isBlank()
                || requestedTenantCode == null || requestedTenantCode.isBlank()) {
            throw forbidden();
        }

        Tenant tenant = tenantRepository.findByCognitoClientId(clientId.trim())
                .orElseThrow(this::forbidden);

        if (!requestedTenantCode.equals(tenant.getTenantCode())) {
            throw forbidden();
        }
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE);
    }
}
