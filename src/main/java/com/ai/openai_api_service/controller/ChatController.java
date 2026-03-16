package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.service.OpenAIService;
import com.ai.openai_api_service.service.PresidioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
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
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String sanitized = presidioService.sanitizeText(request.getUserMessage());
        request.setUserMessage(sanitized);
        ChatResponse response = openAIService.chat(request);
        return ResponseEntity.ok(response);
    }
}

