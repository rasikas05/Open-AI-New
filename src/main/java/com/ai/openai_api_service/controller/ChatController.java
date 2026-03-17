package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.service.OpenAIService;
import com.ai.openai_api_service.service.PresidioService;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Chat", description = "Chat with OpenAI via sanitized input")
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final OpenAIService openAIService;
    private final PresidioService presidioService;

    public ChatController(OpenAIService openAIService, PresidioService presidioService) {
        this.openAIService = openAIService;
        this.presidioService = presidioService;
    }

    @PostMapping
    @Operation(summary = "Send a chat message", description = "Sanitizes user input with Presidio, then sends to OpenAI and returns the response.")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String sanitized = presidioService.sanitizeText(request.getUserMessage());
        request.setUserMessage(sanitized);
        ChatResponse response = openAIService.chat(request);
        return ResponseEntity.ok(response);
    }
}

