package com.ai.openai_api_service.model;

public class TokenUsageDto {
    private int used;
    private int total;
    private int remaining;

    public TokenUsageDto() {
    }

    public TokenUsageDto(int used, int total, int remaining) {
        this.used = used;
        this.total = total;
        this.remaining = remaining;
    }

    public int getUsed() {
        return used;
    }

    public void setUsed(int used) {
        this.used = used;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getRemaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }
}
