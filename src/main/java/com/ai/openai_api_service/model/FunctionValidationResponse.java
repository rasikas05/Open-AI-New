package com.ai.openai_api_service.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FunctionValidationResponse {
    private int totalRequested;
    private int totalFound;
    private List<FunctionDetailsResponse> validPrograms;
    private List<String> invalidPrograms;
}
