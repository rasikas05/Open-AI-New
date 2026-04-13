package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.entity.ChatMessageEntity;
import com.ai.openai_api_service.entity.ChatSessionEntity;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.model.SessionCountDto;
import com.ai.openai_api_service.model.SessionMessageDto;
import com.ai.openai_api_service.model.SessionSummaryDto;
import com.ai.openai_api_service.service.ChatPersistenceService;
import com.ai.openai_api_service.service.OpenAIService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
@Tag(name = "Chat", description = "Chat with OpenAI via sanitized input")
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final OpenAIService openAIService;
    private final ChatPersistenceService chatPersistenceService;

    public ChatController(OpenAIService openAIService, ChatPersistenceService chatPersistenceService) {
        this.openAIService = openAIService;
        this.chatPersistenceService = chatPersistenceService;
    }

    @PostMapping
    @Operation(summary = "Send a chat message", description = "Sends user input to OpenAI and returns the response.")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = openAIService.chat(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @Operation(summary = "Get chat history", description = "Returns prior session messages for widget display.")
    public ResponseEntity<List<MessageDto>> history(
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "10") int maxExchanges
    ) {
        List<MessageDto> response = chatPersistenceService.loadHistoryForPrompt(
                tenantId,
                userId,
                sessionId,
                maxExchanges
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    @Operation(summary = "List user sessions", description = "Returns session-wise history for a tenant user.")
    public ResponseEntity<List<SessionSummaryDto>> listSessions(
            @RequestParam String tenantId,
            @RequestParam String userId
    ) {
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
    public ResponseEntity<SessionCountDto> countSessions(
            @RequestParam String tenantId,
            @RequestParam String userId
    ) {
        long sessionCount = chatPersistenceService.countSessions(tenantId, userId);
        SessionCountDto response = new SessionCountDto(tenantId, userId, sessionCount);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "Get session transcript", description = "Returns all messages for a selected user session.")
    public ResponseEntity<List<SessionMessageDto>> sessionMessages(
            @PathVariable String sessionId,
            @RequestParam String tenantId,
            @RequestParam String userId
    ) {
        List<ChatMessageEntity> messages = chatPersistenceService.loadSessionMessages(tenantId, userId, sessionId);
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

