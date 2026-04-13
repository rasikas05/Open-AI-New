package com.ai.openai_api_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SuggestionRuleService {

    @Value("${suggestion.rule.enabled:true}")
    private boolean ruleEnabled;

    public List<String> suggest(String latestUserMessage, int maxCount) {
        if (!ruleEnabled || latestUserMessage == null || latestUserMessage.isBlank()) {
            return List.of();
        }

        String text = latestUserMessage.toLowerCase(Locale.ROOT);
        Set<String> suggestions = new LinkedHashSet<>();

        if (containsAny(text, "ois100", "ois101", "order", "customer order", "line status", "delivery")) {
            suggestions.add("Do you want steps to create and release a customer order in OIS100/OIS101?");
            suggestions.add("Should I help validate pricing, quantity, and delivery date for this order?");
            suggestions.add("Do you want the common order status flow and where to verify each stage?");
        }

        if (containsAny(text, "crs610", "address", "payer", "ship-to", "customer master")) {
            suggestions.add("Do you want a checklist for mandatory CRS610 customer address fields?");
            suggestions.add("Should I help troubleshoot payer and ship-to setup validation errors?");
            suggestions.add("Do you want MI/API fields needed to create or update customer addresses?");
        }

        if (containsAny(text, "mms200", "item", "item master", "uom", "costing", "warehouse", "facility")) {
            suggestions.add("Do you want the required MMS200 fields for creating an item master?");
            suggestions.add("Should I map item, facility, and warehouse dependencies for your transaction?");
            suggestions.add("Do you want help validating UoM and costing setup for this item?");
        }

        if (containsAny(text, "mi", "api", "transaction", "program", "panel")) {
            suggestions.add("Do you want the exact MI transaction and field mapping for this use case?");
            suggestions.add("Should I provide a sample MI request/response payload for this step?");
            suggestions.add("Do you want troubleshooting steps for panel-to-MI field mismatches?");
        }

        if (containsAny(text, "error", "validation", "missing", "failed", "failure", "invalid", "mandatory")) {
            suggestions.add("Should I identify the most likely root cause from this error context?");
            suggestions.add("Do you want a field-by-field validation checklist to fix this issue?");
            suggestions.add("Should I provide retry and logging steps for MI/API failure diagnosis?");
        }

        if (suggestions.isEmpty() && containsAny(text, "m3", "infor")) {
            suggestions.add("Do you want this mapped to a specific M3 program, panel, or MI transaction?");
            suggestions.add("Should I provide a step-by-step solution path for your M3 scenario?");
        }

        return new ArrayList<>(suggestions).subList(0, Math.min(maxCount, suggestions.size()));
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
