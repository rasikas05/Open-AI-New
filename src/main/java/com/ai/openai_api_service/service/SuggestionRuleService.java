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

 /*

* =========================================================
* CUSTOMER ORDER
* =========================================================
  */
  if (containsAny(text,
  "ois100",
  "ois101",
  "customer order",
  "order",
  "delivery",
  "line status")) {

  suggestions.add("Explain customer order creation in OIS100");
  suggestions.add("How does OIS101 order release work?");
  suggestions.add("Describe order line status flow in M3");
  suggestions.add("Validate order pricing and delivery dates");
  suggestions.add("Troubleshoot customer order delivery issues");
  }

/*

* =========================================================
* CUSTOMER / ADDRESS
* =========================================================
  */
  if (containsAny(text,
  "crs610",
  "customer master",
  "address",
  "payer",
  "ship-to")) {

  suggestions.add("How to configure CRS610 customer address");
  suggestions.add("Set up payer and ship-to in M3");
  suggestions.add("Explain customer master validation rules");
  suggestions.add("Review customer address MI fields");
  suggestions.add("Fix CRS610 customer setup issues");
  }

/*

* =========================================================
* ITEM / MMS200
* =========================================================
  */
  if (containsAny(text,
  "mms200",
  "item",
  "item master",
  "uom",
  "warehouse",
  "facility",
  "costing")) {

  suggestions.add("How to set up MMS200 item master");
  suggestions.add("Describe warehouse and facility mapping");
  suggestions.add("Configure item costing and UoM in M3");
  suggestions.add("Validate item master fields for M3");
  suggestions.add("Show MMS200 item master best practices");
  }

/*

* =========================================================
* MI / API
* =========================================================
  */
  if (containsAny(text,
  "mi",
  "api",
  "transaction",
  "program",
  "panel")) {

  suggestions.add("Show MI transaction examples in M3");
  suggestions.add("Explain API request structure for M3");
  suggestions.add("Map MI fields to M3 program panels");
  suggestions.add("Describe MI response examples in M3");
  suggestions.add("Troubleshoot M3 panel-to-MI mapping");
  }

/*

* =========================================================
* INVOICING
* =========================================================
  */
  if (containsAny(text,
  "invoice",
  "invoicing",
  "billing",
  "customer invoice",
  "supplier invoice")) {

  suggestions.add("Explain customer invoice workflow in M3");
  suggestions.add("How to process OIS390 invoices");
  suggestions.add("Describe invoice posting in AR ledger");
  suggestions.add("How to configure invoice number series in M3");
  suggestions.add("How does invoice approval work in M3?");
  }

/*

* =========================================================
* ERROR / VALIDATION
* =========================================================
  */
  if (containsAny(text,
  "error",
  "validation",
  "failed",
  "failure",
  "invalid",
  "mandatory",
  "missing")) {

  suggestions.add("Analyze the M3 error root cause");
  suggestions.add("Validate mandatory fields in M3");
  suggestions.add("Describe MI API failure handling in M3");
  suggestions.add("Fix validation errors in M3 transactions");
  suggestions.add("How to resolve M3 validation issues");
  }

/*

* =========================================================
* FALLBACK
* =========================================================
  */
  if (suggestions.isEmpty() && containsAny(text, "m3", "infor")) {

  suggestions.add("Explain the M3 process flow");
  suggestions.add("Describe M3 configuration steps");
  suggestions.add("Validate M3 setup for this scenario");
  suggestions.add("Show M3 integration examples");
  suggestions.add("Explain M3 best practices");
  }


    return new ArrayList<>(suggestions)
            .subList(0, Math.min(maxCount, suggestions.size()));
}

private boolean containsAny(String text, String... keywords) {

    for (String keyword : keywords) {

        if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
            return true;
        }
    }

    return false;
}
}
