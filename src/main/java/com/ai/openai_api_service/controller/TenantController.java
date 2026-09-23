package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.SessionResponse;
import com.ai.openai_api_service.model.TenantCreateRequest;
import com.ai.openai_api_service.model.TenantResponse;
import com.ai.openai_api_service.model.UserResponse;
import com.ai.openai_api_service.model.UserSessionRequest;
import com.ai.openai_api_service.service.TenantClientBindingService;
import com.ai.openai_api_service.service.TenantService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/tenant")
@Tag(name = "Tenant", description = "Tenant, user and session management")
@CrossOrigin(origins = "*")
public class TenantController {

    private static final Logger logger = LoggerFactory.getLogger(TenantController.class);
    private final TenantService tenantService;
    private final TenantClientBindingService tenantClientBindingService;

    public TenantController(TenantService tenantService, TenantClientBindingService tenantClientBindingService) {
        this.tenantService = tenantService;
        this.tenantClientBindingService = tenantClientBindingService;
    }

    @PostMapping
    @Operation(summary = "Create tenant", description = "Create a tenant record in the database.")
    @PreAuthorize("hasAuthority(@requiredM2mScope.authority)")
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody TenantCreateRequest request) {
        logger.info("Creating tenant: {}", request.getTenantCode());
        TenantResponse response = tenantService.createTenant(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tenantId}/users")
    @Operation(summary = "List users", description = "List users for a tenant.")
    @PreAuthorize("hasAuthority(@requiredM2mScope.authority)")
    public ResponseEntity<List<UserResponse>> listUsers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId) {
        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Listing users for tenant={} client_id={}", tenantId, clientId);
        tenantClientBindingService.assertClientOwnsTenantId(clientId, tenantId);
        return ResponseEntity.ok(tenantService.getUsersForTenant(tenantId));
    }

    @GetMapping("/{tenantId}/users/{userId}/sessions")
    @Operation(summary = "List sessions", description = "List sessions for a tenant user.")
    @PreAuthorize("hasAuthority(@requiredM2mScope.authority)")
    public ResponseEntity<List<SessionResponse>> listUserSessions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String userId) {
        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Listing sessions for tenant={} user={} client_id={}", tenantId, userId, clientId);
        tenantClientBindingService.assertClientOwnsTenantId(clientId, tenantId);
        return ResponseEntity.ok(tenantService.getSessionsForUser(tenantId, userId));
    }

    @PostMapping("/{tenantId}/users/{userId}/sessions")
    @Operation(summary = "Register user session", description = "Register a user and session for an existing tenant.")
    @PreAuthorize("hasAuthority(@requiredM2mScope.authority)")
    public ResponseEntity<Void> registerUserSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody UserSessionRequest request) {
        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Registering session {} for tenant={} user={} client_id={}", request.getSessionId(), tenantId, userId, clientId);
        tenantClientBindingService.assertClientOwnsTenantId(clientId, tenantId);
        tenantService.registerUserAndSession(tenantId, userId, request.getSessionId(), request.getTokenLimit());
        return ResponseEntity.ok().build();
    }
}
