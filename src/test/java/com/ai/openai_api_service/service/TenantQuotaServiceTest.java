package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.TenantQuotaEntity;
import com.ai.openai_api_service.repository.TenantQuotaRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantQuotaServiceTest {

    @Mock
    private TenantQuotaRepository tenantQuotaRepository;
    @Mock
    private TokenTransactionRepository tokenTransactionRepository;

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
        TenantQuotaEntity quota = new TenantQuotaEntity();
        quota.setTenantId("tenant-1");
        quota.setBaseLimit(500);
        quota.setExtraTokens(100);
        quota.setTokensUsed(600);
        quota.setLastResetAt(LocalDateTime.now());
        when(tenantQuotaRepository.findByTenantId("tenant-1")).thenReturn(Optional.of(quota));

        TenantQuotaService.QuotaCheckResult result = tenantQuotaService.checkBeforeChat("tenant-1");

        assertTrue(!result.allowed());
        assertEquals(0, result.usage().getRemaining());
        assertEquals("LIMIT_EXCEEDED", result.reason());
    }

    @Test
    void topupShouldIncreaseExtraTokensAndReturnUpdatedUsage() {
        TenantQuotaEntity quota = new TenantQuotaEntity();
        quota.setTenantId("tenant-2");
        quota.setBaseLimit(1000);
        quota.setExtraTokens(0);
        quota.setTokensUsed(100);
        quota.setLastResetAt(LocalDateTime.now());
        when(tenantQuotaRepository.findByTenantId("tenant-2")).thenReturn(Optional.of(quota));
        when(tenantQuotaRepository.save(quota)).thenReturn(quota);

        var response = tenantQuotaService.topup("tenant-2", 500);

        assertEquals(500, quota.getExtraTokens());
        assertEquals(1400, response.getUsage().getRemaining());
        verify(tokenTransactionRepository, times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resetMonthlyQuotasShouldClearUsageAndTopups() {
        TenantQuotaEntity quota = new TenantQuotaEntity();
        quota.setTenantId("tenant-3");
        quota.setBaseLimit(1000);
        quota.setExtraTokens(250);
        quota.setTokensUsed(400);
        quota.setStatus("ACTIVE");
        quota.setLastResetAt(LocalDateTime.now().minusDays(40));
        when(tenantQuotaRepository.findByStatus("ACTIVE")).thenReturn(List.of(quota));

        int resetCount = tenantQuotaService.resetMonthlyQuotas();

        assertEquals(1, resetCount);
        assertEquals(0, quota.getTokensUsed());
        assertEquals(0, quota.getExtraTokens());
        ArgumentCaptor<List<TenantQuotaEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(tenantQuotaRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
    }
}
