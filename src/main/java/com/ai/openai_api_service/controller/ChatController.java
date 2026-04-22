package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.config.SecurityConstants;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.ai.openai_api_service.entity.ChatMessageEntity;
import com.ai.openai_api_service.entity.ChatSessionEntity;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.model.SessionCountDto;
import com.ai.openai_api_service.model.SessionMessageDto;
import com.ai.openai_api_service.model.SessionSummaryDto;
import com.ai.openai_api_service.service.ChatService;
import com.ai.openai_api_service.service.ChatPersistenceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@Tag(name = "Chat", description = "Chat with OpenAI via sanitized input")
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ChatPersistenceService chatPersistenceService;

    public ChatController(ChatService chatService, ChatPersistenceService chatPersistenceService) {
        this.chatService = chatService;
        this.chatPersistenceService = chatPersistenceService;
    }

    @PostMapping
    @Operation(summary = "Send a chat message", description = "Sends user input to OpenAI and returns the response.")
    @PreAuthorize("hasAuthority('SCOPE_default-m2m-resource-server-bhkkzj/read')")
    public ResponseEntity<ChatResponse> chat(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatRequest request) {

        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Chat request from client_id: {}", clientId);

        ChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @Operation(summary = "Get chat history", description = "Returns prior session messages for widget display.")
    @PreAuthorize("hasAuthority('SCOPE_default-m2m-resource-server-bhkkzj/read')")
    public ResponseEntity<List<MessageDto>> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "10") int maxExchanges) {

        String clientId = jwt.getClaimAsString("client_id");
        logger.info("History request from client_id: {}", clientId);

        List<MessageDto> response = chatPersistenceService.loadHistoryForPrompt(
                tenantId, userId, sessionId, maxExchanges
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    @Operation(summary = "List user sessions", description = "Returns session-wise history for a tenant user.")
    @PreAuthorize("hasAuthority('SCOPE_default-m2m-resource-server-bhkkzj/read')")
    public ResponseEntity<List<SessionSummaryDto>> listSessions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @RequestParam String userId) {

        String clientId = jwt.getClaimAsString("client_id");
        logger.info("List sessions request from client_id: {}", clientId);

        List<ChatSessionEntity> sessions = chatPersistenceService.listSessions(tenantId, userId);

        List<SessionSummaryDto> response = sessions.stream()
                .map(session -> new SessionSummaryDto(
                        session.getSessionId(),
                        session.getStatus(),
                        session.getTokenLimit(),
                        session.getTokensUsed(),
                        session.getCreatedAt(),
                        session.getUpdatedAt()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/count")
    @Operation(summary = "Count user sessions", description = "Returns number of sessions for a tenant user.")
    @PreAuthorize("hasAuthority('SCOPE_default-m2m-resource-server-bhkkzj/read')")
    public ResponseEntity<SessionCountDto> countSessions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @RequestParam String userId) {

        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Count sessions request from client_id: {}", clientId);

        long sessionCount = chatPersistenceService.countSessions(tenantId, userId);
        SessionCountDto response = new SessionCountDto(tenantId, userId, sessionCount);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/test-auth")
    public String testAuth(Authentication authentication) {
        return authentication.getName();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "Get session transcript", description = "Returns all messages for a selected user session.")
    @PreAuthorize("hasAuthority('SCOPE_default-m2m-resource-server-bhkkzj/read')")
    public ResponseEntity<List<SessionMessageDto>> sessionMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sessionId,
            @RequestParam String tenantId,
            @RequestParam String userId) {

        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Session messages request from client_id: {}", clientId);

        List<ChatMessageEntity> messages =
                chatPersistenceService.loadSessionMessages(tenantId, userId, sessionId);

        List<SessionMessageDto> response = messages.stream()
                .map(message -> new SessionMessageDto(
                        message.getId(),
                        message.getSessionId(),
                        message.getOriginalText(),
                        message.getSanitizedText(),
                        message.getOpenaiResponse(),
                        message.getTokensUsed(),
                        message.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }
}