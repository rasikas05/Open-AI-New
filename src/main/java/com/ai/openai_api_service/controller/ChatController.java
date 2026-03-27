package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.service.ChatPersistenceService;
import com.ai.openai_api_service.service.OpenAIService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
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
}

