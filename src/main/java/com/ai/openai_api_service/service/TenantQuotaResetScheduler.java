package com.ai.openai_api_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TenantQuotaResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(TenantQuotaResetScheduler.class);

    private final TenantQuotaService tenantQuotaService;

    public TenantQuotaResetScheduler(TenantQuotaService tenantQuotaService) {
        this.tenantQuotaService = tenantQuotaService;
    }

    @Scheduled(cron = "${tenant.quota.monthly-reset.cron:0 0 0 1 * *}")
    public void resetMonthlyQuotas() {
        int updatedCount = tenantQuotaService.resetMonthlyQuotas();
        log.info("Monthly tenant quota reset completed. updatedTenants={}", updatedCount);
    }
}
