package com.ai.openai_api_service.model.rag;

import java.util.ArrayList;
import java.util.List;

public class GroundedRagResult {

    private RagStatus status;
    private String answer;
    private List<String> missingTopics = new ArrayList<>();

    public GroundedRagResult() {
    }

    public GroundedRagResult(RagStatus status, String answer, List<String> missingTopics) {
        this.status = status;
        this.answer = answer;
        this.missingTopics = missingTopics != null ? missingTopics : new ArrayList<>();
    }

    public RagStatus getStatus() {
        return status;
    }

    public void setStatus(RagStatus status) {
        this.status = status;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<String> getMissingTopics() {
        return missingTopics;
    }

    public void setMissingTopics(List<String> missingTopics) {
        this.missingTopics = missingTopics != null ? missingTopics : new ArrayList<>();
    }
}
