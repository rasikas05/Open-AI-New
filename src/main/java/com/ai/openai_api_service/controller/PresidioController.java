package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.PresidioTextRequest;
import com.ai.openai_api_service.service.PresidioService;
import com.ai.openai_api_service.config.SecurityConstants;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Presidio", description = "Debug proxy endpoints for Presidio analyze/anonymize")
@RequestMapping("/api/presidio")
@CrossOrigin(origins = "*")
public class PresidioController {

    private final PresidioService presidioService;

    public PresidioController(PresidioService presidioService) {
        this.presidioService = presidioService;
    }

    @PostMapping("/analyze")
    @Operation(summary = "Analyze text with Presidio", description = "Proxies request to Python analyzer and returns raw response.")
    public ResponseEntity<Map<?, ?>> analyze(@Valid @RequestBody PresidioTextRequest request) {
        Map<?, ?> response = presidioService.analyzeRaw(request.getText());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/anonymize")
    @Operation(summary = "Anonymize text with Presidio", description = "Proxies request to Python anonymizer and returns raw response.")
    public ResponseEntity<Map<?, ?>> anonymize(@Valid @RequestBody PresidioTextRequest request) {
        Map<?, ?> response = presidioService.anonymizeRaw(request.getText());
        return ResponseEntity.ok(response);
    }
}
