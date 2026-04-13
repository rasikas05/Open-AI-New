package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.TenantQuotaExceededException;
import com.ai.openai_api_service.entity.TenantQuotaEntity;
import com.ai.openai_api_service.entity.TokenTransactionEntity;
import com.ai.openai_api_service.model.TokenTransactionType;
import com.ai.openai_api_service.model.TokenUsageDto;
import com.ai.openai_api_service.model.TenantQuotaResponse;
import com.ai.openai_api_service.model.TopupResponse;
import com.ai.openai_api_service.repository.TenantQuotaRepository;
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

    @Value("${tenant.quota.default-base-limit:200000}")
    private int defaultBaseLimit;

    @Value("${tenant.quota.max-topup-tokens:500000}")
    private int maxTopupTokens;

    @Value("${tenant.quota.timezone:UTC}")
    private String quotaTimezone;

    public TenantQuotaService(
            TenantQuotaRepository tenantQuotaRepository,
            TokenTransactionRepository tokenTransactionRepository
    ) {
        this.tenantQuotaRepository = tenantQuotaRepository;
        this.tokenTransactionRepository = tokenTransactionRepository;
    }

    @Transactional
    public QuotaCheckResult checkBeforeChat(String tenantId) {
        Optional<TenantQuotaEntity> quotaOptional = tenantQuotaRepository.findByTenantId(tenantId);
        if (quotaOptional.isEmpty()) {
            log.warn("Tenant quota missing. tenantId={}", tenantId);
            return new QuotaCheckResult(false, null, "QUOTA_NOT_CONFIGURED");
        }
        TenantQuotaEntity quota = quotaOptional.get();
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
        TenantQuotaEntity quota = tenantQuotaRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "tenant quota not found"));
        if ("BLOCKED".equalsIgnoreCase(quota.getStatus())) {
            throw new ResponseStatusException(FORBIDDEN, "Tenant is blocked");
        }
        int safeConsumed = Math.max(0, consumedTokens);
        if (safeConsumed > 0) {
            int updated = tenantQuotaRepository.incrementUsageIfWithinLimit(tenantId, safeConsumed);
            if (updated == 0) {
                TokenUsageDto latestUsage = toUsage(tenantQuotaRepository.findByTenantId(tenantId).orElse(quota));
                log.warn("Atomic usage increment rejected. tenantId={}, consumed={}, used={}, total={}",
                        tenantId, safeConsumed, latestUsage.getUsed(), latestUsage.getTotal());
                throw new TenantQuotaExceededException("Tenant token limit reached", latestUsage);
            }
        }

        TokenTransactionEntity txn = new TokenTransactionEntity();
        txn.setTenantId(tenantId);
        txn.setType(TokenTransactionType.USAGE);
        txn.setTokens(safeConsumed);
        txn.setReferenceId(referenceId);
        tokenTransactionRepository.save(txn);

        TenantQuotaEntity latestQuota = tenantQuotaRepository.findByTenantId(tenantId).orElse(quota);
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

        TenantQuotaEntity quota = tenantQuotaRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "tenant quota not found"));
        if ("BLOCKED".equalsIgnoreCase(quota.getStatus())) {
            throw new ResponseStatusException(FORBIDDEN, "Blocked tenants cannot be topped up");
        }

        int safeTopup = tokens;
        quota.setExtraTokens(safeInt(quota.getExtraTokens()) + safeTopup);
        TenantQuotaEntity saved = tenantQuotaRepository.save(quota);

        TokenTransactionEntity txn = new TokenTransactionEntity();
        txn.setTenantId(tenantId);
        txn.setType(TokenTransactionType.TOPUP);
        txn.setTokens(safeTopup);
        txn.setReferenceId("TOPUP-" + UUID.randomUUID());
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
        if (tenantQuotaRepository.findByTenantId(tenantId).isPresent()) {
            throw new ResponseStatusException(BAD_REQUEST, "Quota already assigned for this tenant");
        }

        TenantQuotaEntity quota = new TenantQuotaEntity();
        quota.setTenantId(tenantId);
        quota.setBaseLimit(baseLimit);
        quota.setExtraTokens(0);
        quota.setTokensUsed(0);
        quota.setStatus("ACTIVE");
        quota.setLastResetAt(LocalDateTime.now());
        TenantQuotaEntity saved = tenantQuotaRepository.save(quota);
        return toQuotaResponse(saved);
    }

    @Transactional
    public TenantQuotaResponse updateQuota(String tenantId, int baseLimit, String status) {
        if (baseLimit <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "baseLimit must be greater than 0");
        }
        TenantQuotaEntity quota = tenantQuotaRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "tenant quota not found"));
        quota.setBaseLimit(baseLimit);
        if (status != null && !status.isBlank()) {
            quota.setStatus(status.trim().toUpperCase());
        }
        TenantQuotaEntity saved = tenantQuotaRepository.save(quota);
        return toQuotaResponse(saved);
    }

    @Transactional
    public int resetMonthlyQuotas() {
        List<TenantQuotaEntity> activeQuotas = tenantQuotaRepository.findByStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        ZoneId zoneId = ZoneId.of(quotaTimezone);
        YearMonth currentMonth = YearMonth.from(now.atZone(zoneId));
        int resetCount = 0;
        for (TenantQuotaEntity quota : activeQuotas) {
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

    private TokenUsageDto toUsage(TenantQuotaEntity quota) {
        int used = safeInt(quota.getTokensUsed());
        int total = safeInt(quota.getBaseLimit()) + safeInt(quota.getExtraTokens());
        int remaining = Math.max(total - used, 0);
        return new TokenUsageDto(used, total, remaining);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private TenantQuotaResponse toQuotaResponse(TenantQuotaEntity quota) {
        return new TenantQuotaResponse(
                quota.getTenantId(),
                safeInt(quota.getBaseLimit()),
                safeInt(quota.getExtraTokens()),
                safeInt(quota.getTokensUsed()),
                quota.getStatus(),
                toUsage(quota)
        );
    }
}
