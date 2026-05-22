package com.ai.openai_api_service.model.python_rag;

import com.ai.openai_api_service.model.MessageDto;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PythonQueryResponse {

    @JsonProperty("reply")
    @JsonAlias("answer")
    private String reply;

    private List<SourceItem> sources;
    private String model;

    @JsonProperty("retrievedChunks")
    private Integer retrievedChunks;

    private UsageInfo usage;
    private List<MessageDto> history;

    @JsonProperty("action_taken")
    private String actionTaken;

    @JsonProperty("pending_tool")
    private String pendingTool;

    @JsonProperty("pending_args")
    private Map<String, Object> pendingArgs;

    @JsonProperty("collecting_tool")
    private String collectingTool;

    @JsonProperty("collected_args")
    private Map<String, Object> collectedArgs;

    @JsonProperty("next_field")
    private String nextField;

    @JsonProperty("next_field_optional")
    private Boolean nextFieldOptional;

    @JsonProperty("m3_data")
    private Map<String, Object> m3Data;

    public String getAnswer() {
        return reply;
    }

    public void setAnswer(String answer) {
        this.reply = answer;
    }
}
