package com.ai.openai_api_service.service.repair;

import java.util.Optional;

public interface SlotRepairRule {

    Optional<RepairOutcome> apply(RepairContext context);
}
