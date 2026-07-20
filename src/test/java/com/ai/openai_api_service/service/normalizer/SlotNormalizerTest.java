package com.ai.openai_api_service.service.normalizer;

import com.ai.openai_api_service.service.SearchFieldCatalog;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotNormalizerTest {

    private final SearchFieldCatalog catalog = new SearchFieldCatalog();
    private final FieldDefinitionRegistry registry = new FieldDefinitionRegistry();
    private final SlotNormalizer normalizer = new SlotNormalizer(catalog, registry);

    @Test
    void normalize_trimsWhitespace() {
        Map<String, SlotValue> slots = Map.of("CustomerNumber", new SlotValue("  C00001  "));

        Map<String, SlotValue> normalized = normalizer.normalize("SearchCustomerOrder", slots);

        assertEquals("C00001", normalized.get("CustomerNumber").value());
    }

    @Test
    void normalize_uppercasesCodeFields() {
        Map<String, SlotValue> slots = Map.of(
                "CustomerNumber", new SlotValue("c00001"),
                "Facility", new SlotValue("a01")
        );

        Map<String, SlotValue> normalized = normalizer.normalize("SearchCustomerOrder", slots);

        assertEquals("C00001", normalized.get("CustomerNumber").value());
        assertEquals("A01", normalized.get("Facility").value());
    }

    @Test
    void normalize_collapsesInternalWhitespace() {
        Map<String, SlotValue> slots = Map.of("Facility", new SlotValue("A 0 1"));

        Map<String, SlotValue> normalized = normalizer.normalize("SearchCustomerOrder", slots);

        assertEquals("A01", normalized.get("Facility").value());
    }

    @Test
    void normalize_alreadyNormalized_unchanged() {
        Map<String, SlotValue> slots = Map.of("CustomerNumber", new SlotValue("C00001"));

        Map<String, SlotValue> normalized = normalizer.normalize("SearchCustomerOrder", slots);

        assertEquals("C00001", normalized.get("CustomerNumber").value());
    }

    @Test
    void normalize_isIdempotent() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", new SlotValue("  c00 001  "));
        slots.put("Facility", new SlotValue("a01"));
        slots.put("OrderDate", new SlotValue(" 2026-04-24 "));

        Map<String, SlotValue> once = normalizer.normalize("SearchCustomerOrder", slots);
        Map<String, SlotValue> twice = normalizer.normalize("SearchCustomerOrder", once);

        assertEquals(once, twice);
    }

    @Test
    void normalize_nullSlots_returnsEmpty() {
        assertTrue(normalizer.normalize("SearchCustomerOrder", null).isEmpty());
    }

    @Test
    void normalize_emptySlots_returnsEmpty() {
        assertTrue(normalizer.normalize("SearchCustomerOrder", Map.of()).isEmpty());
    }

    @Test
    void normalize_blankIntent_returnsDefensiveCopy() {
        Map<String, SlotValue> slots = Map.of("CustomerNumber", new SlotValue("  c00001  "));

        Map<String, SlotValue> normalized = normalizer.normalize("  ", slots);

        assertEquals("  c00001  ", normalized.get("CustomerNumber").value());
    }

    @Test
    void normalize_unknownSlot_trimOnly() {
        Map<String, SlotValue> slots = Map.of("UnknownSlot", new SlotValue("  abc  "));

        Map<String, SlotValue> normalized = normalizer.normalize("SearchCustomerOrder", slots);

        assertEquals("abc", normalized.get("UnknownSlot").value());
    }

    @Test
    void normalize_blankValue_notInvented() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", new SlotValue(""));
        slots.put("Facility", new SlotValue("   "));

        Map<String, SlotValue> normalized = normalizer.normalize("SearchCustomerOrder", slots);

        assertEquals("", normalized.get("CustomerNumber").value());
        assertEquals("   ", normalized.get("Facility").value());
    }

    @Test
    void normalize_nullValue_preserved() {
        Map<String, SlotValue> slots = new LinkedHashMap<>();
        slots.put("CustomerNumber", new SlotValue(null));

        Map<String, SlotValue> normalized = normalizer.normalize("SearchCustomerOrder", slots);

        assertNull(normalized.get("CustomerNumber").value());
    }

    @Test
    void normalize_dateSlot_trimOnly_doesNotCompact() {
        Map<String, SlotValue> slots = Map.of("OrderDate", new SlotValue(" 2026-04-24 "));

        Map<String, SlotValue> normalized = normalizer.normalize("SearchCustomerOrder", slots);

        assertEquals("2026-04-24", normalized.get("OrderDate").value());
    }

    @Test
    void normalize_mergedGarbage_upperOnly_noSplit() {
        Map<String, SlotValue> slots = Map.of("Facility", new SlotValue("a0120250424"));

        Map<String, SlotValue> normalized = normalizer.normalize("SearchCustomerOrder", slots);

        assertEquals("A0120250424", normalized.get("Facility").value());
    }

    @Test
    void toSlotValues_and_toStringMap_roundTrip() {
        Map<String, String> original = new LinkedHashMap<>();
        original.put("CustomerNumber", "C00001");
        original.put("Facility", "A01");

        Map<String, String> roundTrip = SlotNormalizer.toStringMap(SlotNormalizer.toSlotValues(original));

        assertEquals(original, roundTrip);
    }
}
