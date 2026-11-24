package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.ChatAttachmentDto;
import com.ai.openai_api_service.model.ChatPrepareRequest;
import com.ai.openai_api_service.model.ChatPrepareResponse;
import com.ai.openai_api_service.security.AuthPrincipalLabels;
import com.ai.openai_api_service.service.ChatAttachmentService;
import com.ai.openai_api_service.service.ChatPrepareService;
import com.ai.openai_api_service.service.TenantClientBindingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepare message IDs and attachment metadata APIs (Option A: no IDM binary proxy).
 */
@RestController
@Tag(name = "Chat-Attachments", description = "Message prepare and IDM attachment metadata")
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatAttachmentController {

    private static final Logger logger = LoggerFactory.getLogger(ChatAttachmentController.class);

    private final ChatPrepareService chatPrepareService;
    private final ChatAttachmentService chatAttachmentService;
    private final TenantClientBindingService tenantClientBindingService;

    public ChatAttachmentController(
            ChatPrepareService chatPrepareService,
            ChatAttachmentService chatAttachmentService,
            TenantClientBindingService tenantClientBindingService
    ) {
        this.chatPrepareService = chatPrepareService;
        this.chatAttachmentService = chatAttachmentService;
        this.tenantClientBindingService = tenantClientBindingService;
    }

    @PostMapping("/prepare")
    @Operation(summary = "Prepare a chat message id for IDM upload association")
    @PreAuthorize("hasAuthority(@requiredM2mScope.authority)")
    public ResponseEntity<ChatPrepareResponse> prepare(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatPrepareRequest request
    ) {
        logger.info("Chat prepare request from auth: {}", AuthPrincipalLabels.forLog(jwt));
        tenantClientBindingService.assertAuthorizedForTenant(jwt, request.getTenantCode());
        ChatPrepareResponse response = chatPrepareService.prepare(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{messageId}/attachments")
    @Operation(summary = "List IDM attachment metadata for a prepared/persisted message")
    @PreAuthorize("hasAuthority(@requiredM2mScope.authority)")
    public ResponseEntity<Map<String, Object>> listAttachments(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String messageId,
            @RequestParam String tenantCode,
            @RequestParam String userId,
            @RequestParam String sessionId
    ) {
        logger.info(
                "Chat attachments request from auth: {} messageId={}",
                AuthPrincipalLabels.forLog(jwt),
                messageId
        );
        tenantClientBindingService.assertAuthorizedForTenant(jwt, tenantCode);
        List<ChatAttachmentDto> attachments = chatAttachmentService.listByMessageId(
                tenantCode,
                userId,
                sessionId,
                messageId
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messageId", messageId);
        body.put("attachments", attachments);
        return ResponseEntity.ok(body);
    }
}
