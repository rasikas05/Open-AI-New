package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.TenantAuthBypassProperties;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * TEMPORARY DEMO: validates whether a tenant may use Cognito-less access.
 */
@Service
public class TenantAuthBypassService {

    public static final String ACTIVE_STATUS = "ACTIVE";

    private final TenantAuthBypassProperties properties;
    private final TenantRepository tenantRepository;

    public TenantAuthBypassService(
            TenantAuthBypassProperties properties,
            TenantRepository tenantRepository
    ) {
        this.properties = properties;
        this.tenantRepository = tenantRepository;
    }

    public boolean isFeatureEnabled() {
        return !properties.allowlistedCodes().isEmpty();
    }

    public String headerName() {
        return properties.headerName();
    }

    /**
     * Allowlisted in config and present in DB with ACTIVE status.
     */
    public boolean isBypassAllowed(String tenantCode) {
        return evaluate(tenantCode) == TenantAuthBypassDecision.ALLOWED;
    }

    /**
     * Distinct reasons for diagnostics (config vs DB).
     */
    public TenantAuthBypassDecision evaluate(String tenantCode) {
        if (!properties.isAllowlisted(tenantCode)) {
            return TenantAuthBypassDecision.NOT_ALLOWLISTED;
        }
        Optional<Tenant> tenant = tenantRepository.findByTenantCode(tenantCode.trim());
        if (tenant.isEmpty() || !ACTIVE_STATUS.equalsIgnoreCase(tenant.get().getStatus())) {
            return TenantAuthBypassDecision.TENANT_MISSING_OR_INACTIVE;
        }
        return TenantAuthBypassDecision.ALLOWED;
    }
}
