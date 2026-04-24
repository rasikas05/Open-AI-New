package com.ai.openai_api_service.entity;

public class PiiEntityDto {

    private String type;
    private int start;
    private int end;
    private double score;

    public PiiEntityDto(String type, int start, int end, double score) {
        this.type = type;
        this.start = start;
        this.end = end;
        this.score = score;
    }

    public String getType() { return type; }
    public int getStart() { return start; }
    public int getEnd() { return end; }
    public double getScore() { return score; }
}