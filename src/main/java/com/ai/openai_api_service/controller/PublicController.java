package com.ai.openai_api_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Public", description = "Public endpoints")
@RequestMapping("/public")
public class PublicController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns service health status.")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Service is healthy");
    }
}