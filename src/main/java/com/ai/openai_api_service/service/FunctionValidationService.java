package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.FunctionValidationResponse;

import java.util.List;

public interface FunctionValidationService {
    FunctionValidationResponse validateFunctionIds(List<String> functionIds);
}
