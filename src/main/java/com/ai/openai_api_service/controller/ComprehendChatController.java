package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.entity.ChatMessageEntity;
import com.ai.openai_api_service.entity.ChatSessionEntity;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.model.SessionSummaryDto;
import com.ai.openai_api_service.service.ComprehendChatService;
import com.ai.openai_api_service.service.ChatPersistenceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

import java.util.List;

@RestController
@Tag(name = "Chat-Comprehend", description = "Chat with OpenAI via Comprehend-sanitized input")
@RequestMapping("/api/chat/comprehend")
@CrossOrigin(origins = "*")
public class ComprehendChatController {

    private static final Logger logger = LoggerFactory.getLogger(ComprehendChatController.class);

    private final ComprehendChatService comprehendChatService;
    private final ChatPersistenceService chatPersistenceService;

    public ComprehendChatController(
            ComprehendChatService comprehendChatService,
            ChatPersistenceService chatPersistenceService
    ) {
        this.comprehendChatService = comprehendChatService;
        this.chatPersistenceService = chatPersistenceService;
    }

    @PostMapping
    @Operation(
            summary = "Send a chat message with Comprehend PII detection",
            description = "Sends user input to OpenAI with AWS Comprehend-based PII detection and anonymization before processing."
    )
    @PreAuthorize("hasAuthority('SCOPE_default-m2m-resource-server-bhkkzj/read')")
    public ResponseEntity<ChatResponse> chat(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatRequest request) {

        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Comprehend Chat request from client_id: {}", clientId);

        ChatResponse response = comprehendChatService.chat(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @Operation(
            summary = "Get chat history",
            description = "Returns prior session messages for widget display (Comprehend-based)."
    )
    @PreAuthorize("hasAuthority('SCOPE_default-m2m-resource-server-bhkkzj/read')")
    public ResponseEntity<List<MessageDto>> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "10") int maxExchanges) {

        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Comprehend History request from client_id: {}", clientId);

        List<MessageDto> response = chatPersistenceService.loadHistoryForPrompt(
                tenantId, userId, sessionId, maxExchanges
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    @Operation(
            summary = "List user sessions",
            description = "Returns session-wise history for a tenant user (Comprehend-based)."
    )
    @PreAuthorize("hasAuthority('SCOPE_default-m2m-resource-server-bhkkzj/read')")
    public ResponseEntity<List<SessionSummaryDto>> listSessions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @RequestParam String userId) {

        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Comprehend List sessions request from client_id: {}", clientId);

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

    @GetMapping("/sessions/{sessionId}")
    @Operation(
            summary = "Get session details",
            description = "Returns full session details including all messages (Comprehend-based)."
    )
    @PreAuthorize("hasAuthority('SCOPE_default-m2m-resource-server-bhkkzj/read')")
    public ResponseEntity<SessionSummaryDto> getSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sessionId) {

        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Comprehend Get session request from client_id: {} sessionId: {}", clientId, sessionId);

        List<ChatSessionEntity> sessions = chatPersistenceService.listSessions(null, null);
        ChatSessionEntity session = sessions.stream()
                .filter(s -> sessionId.equals(s.getSessionId()))
                .findFirst()
                .orElse(null);

        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        SessionSummaryDto response = new SessionSummaryDto(
                session.getSessionId(),
                session.getStatus(),
                session.getTokenLimit(),
                session.getTokensUsed(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{sessionId}/close")
    @Operation(
            summary = "Close a session",
            description = "Marks a session as closed (Comprehend-based)."
    )
    @PreAuthorize("hasAuthority('SCOPE_default-m2m-resource-server-bhkkzj/read')")
    public ResponseEntity<SessionSummaryDto> closeSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sessionId) {

        String clientId = jwt.getClaimAsString("client_id");
        logger.info("Comprehend Close session request from client_id: {} sessionId: {}", clientId, sessionId);

        List<ChatSessionEntity> sessions = chatPersistenceService.listSessions(null, null);
        ChatSessionEntity session = sessions.stream()
                .filter(s -> sessionId.equals(s.getSessionId()))
                .findFirst()
                .orElse(null);

        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        session.setStatus("CLOSED");
        session.setEndTime(java.time.LocalDateTime.now());

        SessionSummaryDto response = new SessionSummaryDto(
                session.getSessionId(),
                session.getStatus(),
                session.getTokenLimit(),
                session.getTokensUsed(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
        return ResponseEntity.ok(response);
    }
}
