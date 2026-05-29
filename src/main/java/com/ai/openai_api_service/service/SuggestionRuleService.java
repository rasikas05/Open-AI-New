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

public boolean isSupportedM3Topic(String latestUserMessage) {
    if (latestUserMessage == null || latestUserMessage.isBlank()) {
        return false;
    }

    String text = latestUserMessage.toLowerCase(Locale.ROOT);
    return containsAny(text,
            "m3",
            "infor",
            "ois100",
            "ois101",
            "ois390",
            "crs610",
            "mms200",
            "mi",
            "api",
            "customer order",
            "customer master",
            "address",
            "payer",
            "ship-to",
            "item master",
            "warehouse",
            "facility",
            "uom",
            "costing",
            "invoice",
            "invoicing",
            "billing",
            "validation",
            "error",
            "transaction",
            "program",
            "panel",
            "inventory",
            "delivery",
            "order",
            "customer"
    );
}

public List<String> genericSuggestions(int maxCount) {
    if (maxCount <= 0) {
        return List.of();
    }
    List<String> generic = List.of(
            "Purchase order information",
            "Ad hoc report creation steps",
            "PR order process in M3",
            "M3 PR reference",
            "PO and PR relationship",
            "Invoice number series",
            "Customer order process",
            "Item master fields",
            "MI transaction examples",
            "Invoice posting in AR"
    );
    return generic.subList(0, Math.min(maxCount, generic.size()));
}

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

  suggestions.add("Customer order process");
  suggestions.add("OIS101 release status");
  suggestions.add("Order line status");
  suggestions.add("Order pricing rules");
  suggestions.add("Delivery schedule details");
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

  suggestions.add("CRS610 customer address rules");
  suggestions.add("Payer and ship-to relationships");
  suggestions.add("Customer master validation");
  suggestions.add("Customer address MI fields");
  suggestions.add("Customer setup guidance");
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

  suggestions.add("MMS200 item master rules");
  suggestions.add("Warehouse and facility mapping");
  suggestions.add("Item costing and UoM rules");
  suggestions.add("Item master validation");
  suggestions.add("Item master configuration");
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

  suggestions.add("MI transaction examples");
  suggestions.add("M3 API request structure");
  suggestions.add("MI field to panel mapping");
  suggestions.add("MI response examples");
  suggestions.add("Panel-to-MI mapping concepts");
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

  suggestions.add("Customer invoice workflow");
  suggestions.add("OIS390 invoice flow");
  suggestions.add("Invoice posting in AR");
  suggestions.add("Invoice number series");
  suggestions.add("Invoice approval concepts");
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

  suggestions.add("M3 error root cause");
  suggestions.add("Mandatory fields validation");
  suggestions.add("MI failure handling");
  suggestions.add("Validation error fixes");
  suggestions.add("M3 validation guidance");
  }

/*

* =========================================================
* FALLBACK
* =========================================================
  */
  if (suggestions.isEmpty() && containsAny(text, "m3", "infor")) {

  suggestions.add("M3 process concepts");
  suggestions.add("M3 setup topics");
  suggestions.add("M3 integration examples");
  suggestions.add("M3 best practices");
  suggestions.add("M3 configuration concepts");
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
