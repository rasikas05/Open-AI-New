package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.config.SecurityConstants;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Secured", description = "Secured endpoints requiring authentication")
@RequestMapping("/api/secured")
public class SecuredController {

    private static final Logger logger = LoggerFactory.getLogger(SecuredController.class);

    private final JwtUtil jwtUtil;

    public SecuredController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/client-info")
    @Operation(summary = "Get client information", description = "Returns information about the authenticated client.")
    @PreAuthorize("hasAuthority('SCOPE_default-m2m-resource-server-bhkkzj/read')")
    public ResponseEntity<String> getClientInfo() {
        String clientId = jwtUtil.getClientId();
        logger.info("Client info request from client_id: {}", clientId);
        return ResponseEntity.ok("Authenticated client: " + clientId);
    }
}
