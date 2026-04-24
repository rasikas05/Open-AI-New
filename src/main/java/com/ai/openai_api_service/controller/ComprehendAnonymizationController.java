package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.PresidioTextRequest;
import com.ai.openai_api_service.service.ComprehendAnonymizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Comprehend-Anonymization", description = "Standalone Comprehend PII detection + Presidio anonymization")
@RequestMapping("/api/comprehend")
@CrossOrigin(origins = "*")
public class ComprehendAnonymizationController {

    private static final Logger logger = LoggerFactory.getLogger(ComprehendAnonymizationController.class);
    private final ComprehendAnonymizationService comprehendAnonymizationService;

    public ComprehendAnonymizationController(ComprehendAnonymizationService comprehendAnonymizationService) {
        this.comprehendAnonymizationService = comprehendAnonymizationService;
    }

    @PostMapping("/anonymize")
    @Operation(
            summary = "Detect and anonymize text with Comprehend + Presidio",
            description = "Detects PII using AWS Comprehend and anonymizes it using Presidio anonymizer."
    )
    @PreAuthorize("hasAuthority('SCOPE_default-m2m-resource-server-bhkkzj/read')")
    public ResponseEntity<Map<String, Object>> anonymize(@Valid @RequestBody PresidioTextRequest request) {
        logger.info("Comprehend anonymization request received");
        Map<String, Object> result = comprehendAnonymizationService.detectAndAnonymize(request.getText());
        return ResponseEntity.ok(result);
    }
}
