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
        if (!properties.isAllowlisted(tenantCode)) {
            return false;
        }
        Optional<Tenant> tenant = tenantRepository.findByTenantCode(tenantCode.trim());
        return tenant.isPresent() && ACTIVE_STATUS.equalsIgnoreCase(tenant.get().getStatus());
    }
}
