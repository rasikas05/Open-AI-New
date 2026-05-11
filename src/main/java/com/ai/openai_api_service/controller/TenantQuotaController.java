package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.TenantQuotaRequest;
import com.ai.openai_api_service.model.TenantQuotaResponse;
import com.ai.openai_api_service.model.TenantQuotaUpdateRequest;
import com.ai.openai_api_service.model.TopupRequest;
import com.ai.openai_api_service.model.TopupResponse;
import com.ai.openai_api_service.service.TenantQuotaService;
import com.ai.openai_api_service.config.SecurityConstants;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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

    private final TenantQuotaService tenantQuotaService;

    public TenantQuotaController(TenantQuotaService tenantQuotaService) {
        this.tenantQuotaService = tenantQuotaService;
    }

    @PostMapping("/quota")
    @Operation(summary = "Assign tenant quota", description = "Assigns initial quota for a tenant. Fails if quota already exists.")
    public ResponseEntity<TenantQuotaResponse> assignQuota(@Valid @RequestBody TenantQuotaRequest request) {
        TenantQuotaResponse response = tenantQuotaService.assignQuota(request.getTenantCode(), request.getBaseLimit());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/quota")
    @Operation(summary = "Update tenant quota", description = "Updates base quota and optional tenant quota status.")
    public ResponseEntity<TenantQuotaResponse> updateQuota(@Valid @RequestBody TenantQuotaUpdateRequest request) {
        TenantQuotaResponse response = tenantQuotaService.updateQuota(
                request.getTenantCode(),
                request.getBaseLimit(),
                request.getStatus()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/topup")
    @Operation(summary = "Top up tenant tokens", description = "Adds extra tokens to tenant quota.")
    public ResponseEntity<TopupResponse> topup(@Valid @RequestBody TopupRequest request) {
        TopupResponse response = tenantQuotaService.topup(request.getTenantCode(), request.getTokens());
        return ResponseEntity.ok(response);
    }
}
