package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.TenantAuthBypassProperties;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAuthBypassServiceTest {

    private static final String DEMO = "EM47JBNURMACJZ8F_DEM";

    @Mock
    private TenantRepository tenantRepository;

    private TenantAuthBypassProperties properties;
    private TenantAuthBypassService service;

    @BeforeEach
    void setUp() {
        properties = new TenantAuthBypassProperties();
        properties.setCodes(DEMO);
        service = new TenantAuthBypassService(properties, tenantRepository);
    }

    @Test
    void emptyAllowlist_featureDisabled() {
        properties.setCodes("");
        assertFalse(service.isFeatureEnabled());
        assertFalse(service.isBypassAllowed(DEMO));
    }

    @Test
    void allowlistedActive_allowed() {
        when(tenantRepository.findByTenantCode(DEMO)).thenReturn(Optional.of(tenant(DEMO, "ACTIVE")));
        assertTrue(service.isFeatureEnabled());
        assertTrue(service.isBypassAllowed(DEMO));
    }

    @Test
    void allowlistedInactive_notAllowed() {
        when(tenantRepository.findByTenantCode(DEMO)).thenReturn(Optional.of(tenant(DEMO, "DISABLED")));
        assertFalse(service.isBypassAllowed(DEMO));
    }

    @Test
    void allowlistedMissing_notAllowed() {
        when(tenantRepository.findByTenantCode(DEMO)).thenReturn(Optional.empty());
        assertFalse(service.isBypassAllowed(DEMO));
    }

    @Test
    void notAllowlisted_notAllowed() {
        assertFalse(service.isBypassAllowed("OTHER"));
    }

    private static Tenant tenant(String code, String status) {
        Tenant t = new Tenant();
        t.setTenantCode(code);
        t.setStatus(status);
        return t;
    }
}
