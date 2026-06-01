package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.FunctionValidationRequest;
import com.ai.openai_api_service.model.FunctionValidationResponse;
import com.ai.openai_api_service.service.FunctionValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/functions")
public class FunctionValidationController {

    private static final Logger logger = LoggerFactory.getLogger(FunctionValidationController.class);

    private final FunctionValidationService validationService;

    public FunctionValidationController(FunctionValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping("/validate")
    public ResponseEntity<FunctionValidationResponse> validateFunctionIds(@RequestBody FunctionValidationRequest request) {
        try {
            if (request == null || request.getFunctionIds() == null) {
                return ResponseEntity.ok(new FunctionValidationResponse(0, 0, List.of(), List.of()));
            }

            FunctionValidationResponse response = validationService.validateFunctionIds(request.getFunctionIds());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            logger.error("Failed to validate function IDs", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to validate function IDs", ex);
        }
    }
}
