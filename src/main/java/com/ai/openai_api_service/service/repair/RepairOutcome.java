package com.ai.openai_api_service.service.repair;

import java.util.List;

public record RepairOutcome(List<RepairAction> actions) {

    public boolean changed() {
        return actions != null && !actions.isEmpty();
    }

    public double aggregateConfidence() {
        if (actions == null || actions.isEmpty()) {
            return 0.0;
        }
        return actions.stream().mapToDouble(RepairAction::confidence).average().orElse(0.0);
    }
}
