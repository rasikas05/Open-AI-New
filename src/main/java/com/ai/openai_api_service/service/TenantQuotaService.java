package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.TenantQuotaExceededException;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.TenantQuota;
import com.ai.openai_api_service.entity.TokenTransactionEntity;
import com.ai.openai_api_service.model.TokenTransactionType;
import com.ai.openai_api_service.model.TokenUsageDto;
import com.ai.openai_api_service.model.TenantQuotaResponse;
import com.ai.openai_api_service.model.TopupResponse;
import com.ai.openai_api_service.repository.TenantQuotaRepository;
import com.ai.openai_api_service.repository.TenantRepository;
import com.ai.openai_api_service.repository.TokenTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class TenantQuotaService {
    private static final Logger log = LoggerFactory.getLogger(TenantQuotaService.class);

    private final TenantQuotaRepository tenantQuotaRepository;
    private final TokenTransactionRepository tokenTransactionRepository;
    private final TenantRepository tenantRepository;

    @Value("${tenant.quota.default-base-limit:200000}")
    private int defaultBaseLimit;

    @Value("${tenant.quota.max-topup-tokens:500000}")
    private int maxTopupTokens;

    @Value("${tenant.quota.timezone:UTC}")
    private String quotaTimezone;

    public TenantQuotaService(
            TenantQuotaRepository tenantQuotaRepository,
            TokenTransactionRepository tokenTransactionRepository,
            TenantRepository tenantRepository
    ) {
        this.tenantQuotaRepository = tenantQuotaRepository;
        this.tokenTransactionRepository = tokenTransactionRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public QuotaCheckResult checkBeforeChat(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Tenant not found"));

        TenantQuota quota = tenantQuotaRepository.findByTenant(tenant).orElse(null);
        if (quota == null) {
            log.warn("Tenant quota missing. tenantId={}", tenantId);
            return new QuotaCheckResult(false, null, "QUOTA_NOT_CONFIGURED");
        }
        TokenUsageDto usage = toUsage(quota);
        if ("BLOCKED".equalsIgnoreCase(quota.getStatus())) {
            log.warn("Blocked tenant attempted chat. tenantId={}", tenantId);
            return new QuotaCheckResult(false, usage, "TENANT_BLOCKED");
        }
        boolean allowed = usage.getUsed() < usage.getTotal();
        if (!allowed) {
            log.warn("Tenant quota exceeded before chat call. tenantId={}, used={}, total={}",
                    tenantId, usage.getUsed(), usage.getTotal());
            return new QuotaCheckResult(false, usage, "LIMIT_EXCEEDED");
        }
        return new QuotaCheckResult(true, usage, null);
    }

    @Transactional
    public TokenUsageDto recordUsage(String tenantId, int consumedTokens, String referenceId) {
        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Tenant not found"));

        TenantQuota quota = tenantQuotaRepository.findByTenant(tenant)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "tenant quota not found"));
        if ("BLOCKED".equalsIgnoreCase(quota.getStatus())) {
            throw new ResponseStatusException(FORBIDDEN, "Tenant is blocked");
        }
        int safeConsumed = Math.max(0, consumedTokens);
        if (safeConsumed > 0) {
            int updated = tenantQuotaRepository.incrementUsageIfWithinLimit(tenant.getId(), safeConsumed);
            if (updated == 0) {
                TenantQuota latestQuota = tenantQuotaRepository.findByTenant(tenant).orElse(quota);
                TokenUsageDto latestUsage = toUsage(latestQuota);
                log.warn("Atomic usage increment rejected. tenantId={}, consumed={}, used={}, total={}",
                        tenantId, safeConsumed, latestUsage.getUsed(), latestUsage.getTotal());
                throw new TenantQuotaExceededException("Tenant token limit reached", latestUsage);
            }
        }

        TenantQuota latestQuota = tenantQuotaRepository.findByTenant(tenant).orElse(quota);
        int remaining = Math.max((safeInt(latestQuota.getBaseLimit()) + safeInt(latestQuota.getExtraTokens())) - safeInt(latestQuota.getTokensUsed()), 0);

        TokenTransactionEntity txn = new TokenTransactionEntity();
        txn.setTenant(tenant);
        txn.setType(TokenTransactionType.USAGE);
        txn.setTokens(safeConsumed);
        txn.setReferenceId(referenceId);
        txn.setBalanceAfter(remaining);
        txn.setTransactionSource("USAGE_API");
        tokenTransactionRepository.save(txn);

        return toUsage(latestQuota);
    }

    @Transactional
    public TopupResponse topup(String tenantId, int tokens) {
        if (tokens <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "tokens must be greater than 0");
        }
        if (tokens > maxTopupTokens) {
            throw new ResponseStatusException(BAD_REQUEST, "tokens exceeds allowed top-up limit");
        }

        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Tenant not found"));

        TenantQuota quota = tenantQuotaRepository.findByTenant(tenant)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "tenant quota not found"));
        if ("BLOCKED".equalsIgnoreCase(quota.getStatus())) {
            throw new ResponseStatusException(FORBIDDEN, "Blocked tenants cannot be topped up");
        }

        int safeTopup = tokens;
        quota.setExtraTokens(safeInt(quota.getExtraTokens()) + safeTopup);
        TenantQuota saved = tenantQuotaRepository.save(quota);

        int remaining = Math.max((safeInt(saved.getBaseLimit()) + safeInt(saved.getExtraTokens())) - safeInt(saved.getTokensUsed()), 0);

        TokenTransactionEntity txn = new TokenTransactionEntity();
        txn.setTenant(tenant);
        txn.setType(TokenTransactionType.TOPUP);
        txn.setTokens(safeTopup);
        txn.setReferenceId("TOPUP-" + UUID.randomUUID());
        txn.setBalanceAfter(remaining);
        txn.setTransactionSource("TOPUP_API");
        tokenTransactionRepository.save(txn);

        return new TopupResponse(
                "Top-up successful",
                tenantId,
                safeTopup,
                toUsage(saved)
        );
    }

    @Transactional
    public TenantQuotaResponse assignQuota(String tenantId, int baseLimit) {
        if (baseLimit <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "baseLimit must be greater than 0");
        }

        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Tenant not found"));

        if (tenantQuotaRepository.findByTenant(tenant).isPresent()) {
            throw new ResponseStatusException(BAD_REQUEST, "Quota already assigned for this tenant");
        }

        TenantQuota quota = new TenantQuota();
        quota.setTenant(tenant);
        quota.setBaseLimit(baseLimit);
        quota.setExtraTokens(0);
        quota.setTokensUsed(0);
        quota.setStatus("ACTIVE");
        quota.setLastResetAt(LocalDateTime.now());

        TenantQuota saved = tenantQuotaRepository.save(quota);

        return toQuotaResponse(saved);
    }

    @Transactional
    public TenantQuotaResponse updateQuota(String tenantId, int baseLimit, String status) {

        if (baseLimit <= 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "baseLimit must be greater than 0"
            );
        }

        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                BAD_REQUEST,
                                "Tenant not found"
                        ));

        TenantQuota quota = tenantQuotaRepository.findByTenant(tenant)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                BAD_REQUEST,
                                "tenant quota not found"
                        ));

        quota.setBaseLimit(baseLimit);

        if (status != null && !status.isBlank()) {
            quota.setStatus(status.trim().toUpperCase());
        }

        TenantQuota saved = tenantQuotaRepository.save(quota);

        return toQuotaResponse(saved);
    }

    @Transactional
    public TokenUsageDto getTokenUsage(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Tenant not found"));

        TenantQuota quota = tenantQuotaRepository.findByTenant(tenant)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "tenant quota not found"));

        return toUsage(quota);
    }

    @Transactional
    public int resetMonthlyQuotas() {
        List<TenantQuota> activeQuotas = tenantQuotaRepository.findByStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        ZoneId zoneId = ZoneId.of(quotaTimezone);
        YearMonth currentMonth = YearMonth.from(now.atZone(zoneId));
        int resetCount = 0;
        for (TenantQuota quota : activeQuotas) {
            LocalDateTime lastResetAt = quota.getLastResetAt();
            YearMonth quotaMonth = lastResetAt == null
                    ? null
                    : YearMonth.from(lastResetAt.atZone(zoneId));
            if (quotaMonth != null && quotaMonth.equals(currentMonth)) {
                continue;
            }
            quota.setTokensUsed(0);
            quota.setExtraTokens(0);
            quota.setLastResetAt(now);
            resetCount++;
        }
        tenantQuotaRepository.saveAll(activeQuotas);
        return resetCount;
    }

    public record QuotaCheckResult(boolean allowed, TokenUsageDto usage, String reason) {
    }

    private TokenUsageDto toUsage(TenantQuota quota) {
        int used = safeInt(quota.getTokensUsed());
        int total = safeInt(quota.getBaseLimit()) + safeInt(quota.getExtraTokens());
        int remaining = Math.max(total - used, 0);
        return new TokenUsageDto(used, total, remaining);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private TenantQuotaResponse toQuotaResponse(TenantQuota quota) {
        return new TenantQuotaResponse(
                quota.getTenant().getTenantCode(),
                safeInt(quota.getBaseLimit()),
                safeInt(quota.getExtraTokens()),
                safeInt(quota.getTokensUsed()),
                quota.getStatus(),
                toUsage(quota)
        );
    }
}
