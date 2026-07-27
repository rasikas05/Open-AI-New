package com.ai.openai_api_service.service.slots;

import com.ai.openai_api_service.service.normalizer.SlotValue;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenericSlotInterpreterTest {

    private final GenericSlotInterpreter interpreter =
            new GenericSlotInterpreter(new GenericSlotInterpretationCatalog());

    @Test
    void interpret_statusOnly_rangeIntent_setsHighestStatus() {
        Map<String, SlotValue> slots = Map.of("Status", new SlotValue("33"));

        Map<String, SlotValue> interpreted = interpreter.interpret("SearchCustomerOrder", slots);

        assertEquals(Map.of("HighestStatus", new SlotValue("33")), interpreted);
    }

    @Test
    void interpret_statusWithHighestStatus_setsLowestStatus() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("Status", new SlotValue("22"));
        slots.put("HighestStatus", new SlotValue("44"));

        Map<String, SlotValue> interpreted = interpreter.interpret("SearchCustomerOrder", slots);

        assertEquals(Map.of(
                "HighestStatus", new SlotValue("44"),
                "LowestStatus", new SlotValue("22")
        ), interpreted);
    }

    @Test
    void interpret_statusWithLowestStatus_setsHighestStatus() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("Status", new SlotValue("44"));
        slots.put("LowestStatus", new SlotValue("22"));

        Map<String, SlotValue> interpreted = interpreter.interpret("SearchPurchaseOrder", slots);

        assertEquals(Map.of(
                "HighestStatus", new SlotValue("44"),
                "LowestStatus", new SlotValue("22")
        ), interpreted);
    }

    @Test
    void interpret_bothBoundsPresent_keepsAsIs() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("HighestStatus", new SlotValue("44"));
        slots.put("LowestStatus", new SlotValue("22"));

        Map<String, SlotValue> interpreted = interpreter.interpret("SearchDistributionOrder", slots);

        assertEquals(slots, interpreted);
    }

    @Test
    void interpret_manufacturingStatus_aliasesToManufacturingStatus() {
        Map<String, SlotValue> slots = Map.of("Status", new SlotValue("90"));

        Map<String, SlotValue> interpreted = interpreter.interpret("SearchManufacturingOrder", slots);

        assertEquals(Map.of("ManufacturingStatus", new SlotValue("90")), interpreted);
    }

    @Test
    void interpret_unknownIntent_noOp() {
        Map<String, SlotValue> slots = Map.of("Status", new SlotValue("33"));

        Map<String, SlotValue> interpreted = interpreter.interpret("UnknownIntent", slots);

        assertEquals(slots, interpreted);
    }
}
