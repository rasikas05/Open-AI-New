package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.TenantQuotaRequest;
import com.ai.openai_api_service.model.TenantQuotaResponse;
import com.ai.openai_api_service.model.TenantQuotaUpdateRequest;
import com.ai.openai_api_service.model.TokenUsageDto;
import com.ai.openai_api_service.model.TopupRequest;
import com.ai.openai_api_service.model.TopupResponse;
import com.ai.openai_api_service.service.TenantClientBindingService;
import com.ai.openai_api_service.service.TenantQuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Tenant Quota", description = "Tenant quota and top-up operations")
@RequestMapping("/tenant")
@CrossOrigin(origins = "*")
public class TenantQuotaController {

    private static final Logger logger = LoggerFactory.getLogger(TenantQuotaController.class);

    private final TenantQuotaService tenantQuotaService;
    private final TenantClientBindingService tenantClientBindingService;

    public TenantQuotaController(
            TenantQuotaService tenantQuotaService,
            TenantClientBindingService tenantClientBindingService) {
        this.tenantQuotaService = tenantQuotaService;
        this.tenantClientBindingService = tenantClientBindingService;
    }

    @PostMapping("/quota")
    @Operation(summary = "Assign tenant quota", description = "Assigns initial quota for a tenant. Fails if quota already exists.")
    @PreAuthorize("hasAuthority(@requiredM2mScope.authority)")
    public ResponseEntity<TenantQuotaResponse> assignQuota(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TenantQuotaRequest request) {
        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Assign quota request from client_id={} tenantCode={}", clientId, request.getTenantCode());
        tenantClientBindingService.assertClientOwnsTenantCode(clientId, request.getTenantCode());
        TenantQuotaResponse response = tenantQuotaService.assignQuota(request.getTenantCode(), request.getBaseLimit());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/quota")
    @Operation(summary = "Update tenant quota", description = "Updates base quota and optional tenant quota status.")
    @PreAuthorize("hasAuthority(@requiredM2mScope.authority)")
    public ResponseEntity<TenantQuotaResponse> updateQuota(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TenantQuotaUpdateRequest request) {
        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Update quota request from client_id={} tenantCode={}", clientId, request.getTenantCode());
        tenantClientBindingService.assertClientOwnsTenantCode(clientId, request.getTenantCode());
        TenantQuotaResponse response = tenantQuotaService.updateQuota(
                request.getTenantCode(),
                request.getBaseLimit(),
                request.getStatus()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/topup")
    @Operation(summary = "Top up tenant tokens", description = "Adds extra tokens to tenant quota.")
    @PreAuthorize("hasAuthority(@requiredM2mScope.authority)")
    public ResponseEntity<TopupResponse> topup(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TopupRequest request) {
        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Topup request from client_id={} tenantCode={}", clientId, request.getTenantCode());
        tenantClientBindingService.assertClientOwnsTenantCode(clientId, request.getTenantCode());
        TopupResponse response = tenantQuotaService.topup(request.getTenantCode(), request.getTokens());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/quota/{tenantCode}")
    @Operation(summary = "Get tenant token usage", description = "Returns used, total and remaining token counts for a tenant.")
    @PreAuthorize("hasAuthority(@requiredM2mScope.authority)")
    public ResponseEntity<TokenUsageDto> getTokenUsage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantCode) {
        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Get token usage from client_id={} tenantCode={}", clientId, tenantCode);
        tenantClientBindingService.assertClientOwnsTenantCode(clientId, tenantCode);
        TokenUsageDto usage = tenantQuotaService.getTokenUsage(tenantCode);
        return ResponseEntity.ok(usage);
    }
}
