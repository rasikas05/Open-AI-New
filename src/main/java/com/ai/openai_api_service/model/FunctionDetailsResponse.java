package com.ai.openai_api_service.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FunctionDetailsResponse {
    private String fnid;
    private String description;
    private String category;
    private String mnid;
}
