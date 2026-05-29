package com.ai.openai_api_service.model.python_rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceItem {

    private String url;
    private String title;
    private Float score;
}
