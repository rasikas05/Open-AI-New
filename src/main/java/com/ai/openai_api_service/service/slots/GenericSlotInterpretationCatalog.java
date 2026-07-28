package com.ai.openai_api_service.service.slots;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GenericSlotInterpretationCatalog {

    public enum InterpretationType {
        RANGE,
        ALIAS
    }

    public record InterpretationRule(
            String genericSlot,
            InterpretationType type,
            String primaryTargetSlot,
            String secondaryTargetSlot
    ) {
    }

    private final Map<String, List<InterpretationRule>> rulesByIntent = Map.of(
            "SearchCustomerOrder", List.of(
                    new InterpretationRule("Status", InterpretationType.RANGE, "HighestStatus", "LowestStatus"),
                    new InterpretationRule("OrderNumber", InterpretationType.ALIAS, "CustomerOrderNumber", null)
            ),
            "SearchPurchaseOrder", List.of(
                    new InterpretationRule("Status", InterpretationType.RANGE, "HighestStatus", "LowestStatus")
            ),
            "SearchDistributionOrder", List.of(
                    new InterpretationRule("Status", InterpretationType.RANGE, "HighestStatus", "LowestStatus")
            ),
            "SearchManufacturingOrder", List.of(
                    new InterpretationRule("Status", InterpretationType.ALIAS, "ManufacturingStatus", null)
            )
    );

    public List<InterpretationRule> rulesFor(String intentName) {
        if (intentName == null || intentName.isBlank()) {
            return List.of();
        }
        return rulesByIntent.getOrDefault(intentName, List.of());
    }
}
