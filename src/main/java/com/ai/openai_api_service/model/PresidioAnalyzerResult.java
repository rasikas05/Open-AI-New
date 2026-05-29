package com.ai.openai_api_service.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PresidioAnalyzerResult {

    @JsonProperty("entity_type")
    private String entityType;

    @JsonProperty("start")
    private int start;

    @JsonProperty("end")
    private int end;

    @JsonProperty("score")
    private double score;

    public PresidioAnalyzerResult() {}

    public PresidioAnalyzerResult(String entityType, int start, int end, double score) {
        this.entityType = entityType;
        this.start = start;
        this.end = end;
        this.score = score;
    }

    public String getEntityType() { return entityType; }
    public int getStart() { return start; }
    public int getEnd() { return end; }
    public double getScore() { return score; }

    public void setEntityType(String entityType) { this.entityType = entityType; }
    public void setStart(int start) { this.start = start; }
    public void setEnd(int end) { this.end = end; }
    public void setScore(double score) { this.score = score; }
}
