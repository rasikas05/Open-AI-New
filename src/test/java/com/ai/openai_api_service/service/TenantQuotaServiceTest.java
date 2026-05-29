package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.TenantQuota;
import com.ai.openai_api_service.model.TopupResponse;
import com.ai.openai_api_service.repository.TenantQuotaRepository;
import com.ai.openai_api_service.repository.TenantRepository;
import com.ai.openai_api_service.repository.TokenTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantQuotaServiceTest {

    @Mock
    private TenantQuotaRepository tenantQuotaRepository;

    @Mock
    private TokenTransactionRepository tokenTransactionRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantQuotaService tenantQuotaService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(tenantQuotaService, "defaultBaseLimit", 1000);
        ReflectionTestUtils.setField(tenantQuotaService, "maxTopupTokens", 10000);
        ReflectionTestUtils.setField(tenantQuotaService, "quotaTimezone", "UTC");
    }

    @Test
    void checkBeforeChatShouldBlockWhenUsedEqualsTotal() {

        Tenant tenant = new Tenant();
        tenant.setTenantCode("tenant-1");

        TenantQuota quota = new TenantQuota();
        quota.setTenant(tenant);
        quota.setBaseLimit(500);
        quota.setExtraTokens(100);
        quota.setTokensUsed(600);
        quota.setLastResetAt(LocalDateTime.now());

        when(tenantRepository.findByTenantCode("tenant-1"))
                .thenReturn(Optional.of(tenant));
        when(tenantQuotaRepository.findByTenant(tenant))
                .thenReturn(Optional.of(quota));

        TenantQuotaService.QuotaCheckResult result = tenantQuotaService.checkBeforeChat("tenant-1");

        assertTrue(result.allowed() == false);
        assertEquals("LIMIT_EXCEEDED", result.reason());
        assertEquals(600, result.usage().getUsed());
        assertEquals(600, result.usage().getTotal());
        assertEquals(0, result.usage().getRemaining());
    }

    @Test
    void topupShouldIncreaseExtraTokensAndReturnUpdatedUsage() {

        Tenant tenant = new Tenant();
        tenant.setTenantCode("tenant-2");

        TenantQuota quota = new TenantQuota();
        quota.setTenant(tenant);
        quota.setBaseLimit(1000);
        quota.setExtraTokens(0);
        quota.setTokensUsed(100);
        quota.setLastResetAt(LocalDateTime.now());

        when(tenantRepository.findByTenantCode("tenant-2"))
                .thenReturn(Optional.of(tenant));
        when(tenantQuotaRepository.findByTenant(tenant))
                .thenReturn(Optional.of(quota));
        when(tenantQuotaRepository.save(quota))
                .thenReturn(quota);

        TopupResponse response = tenantQuotaService.topup("tenant-2", 500);

        assertEquals("tenant-2", response.getTenantCode());
        assertEquals(500, response.getTokensAdded());
        assertEquals(1500, response.getUsage().getTotal());
        assertEquals(1400, response.getUsage().getRemaining());
        verify(tenantQuotaRepository).save(quota);
        verify(tokenTransactionRepository).save(any());
    }

    @Test
    void resetMonthlyQuotasShouldClearUsageAndTopups() {

        Tenant tenant = new Tenant();
        tenant.setTenantCode("tenant-3");

        TenantQuota quota = new TenantQuota();
        quota.setTenant(tenant);
        quota.setBaseLimit(1000);
        quota.setExtraTokens(250);
        quota.setTokensUsed(400);
        quota.setStatus("ACTIVE");
        quota.setLastResetAt(LocalDateTime.now().minusDays(40));

        when(tenantQuotaRepository.findByStatus("ACTIVE"))
                .thenReturn(List.of(quota));

        int resetCount = tenantQuotaService.resetMonthlyQuotas();

        assertEquals(1, resetCount);
        assertEquals(0, quota.getTokensUsed());
        assertEquals(0, quota.getExtraTokens());

        ArgumentCaptor<List<TenantQuota>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(tenantQuotaRepository).saveAll(captor.capture());

        assertEquals(1, captor.getValue().size());
    }
}