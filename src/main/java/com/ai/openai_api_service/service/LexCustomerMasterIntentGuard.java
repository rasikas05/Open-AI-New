package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Post-Lex remap when NLU confuses customer master with customer order.
 * GetCustomer vs SearchCustomerOrder sample utterances live in the AWS Lex console, not this repo.
 */
public final class LexCustomerMasterIntentGuard {

    private static final Logger log = LoggerFactory.getLogger(LexCustomerMasterIntentGuard.class);

    static final String GET_CUSTOMER = "GetCustomer";
    static final String SEARCH_CUSTOMER_ORDER = "SearchCustomerOrder";

    private static final Pattern CUSTOMER_MASTER = Pattern.compile(
            "(?i)\\b(fetch|show|get)\\s+customer\\s+([A-Za-z0-9][A-Za-z0-9._-]*)\\b"
    );

    /** Whole-token / phrase order words. Token CO only — not "company". */
    private static final Pattern ORDER_WORDS = Pattern.compile(
            "(?i)customer\\s+order|sales\\s+order|order\\s+number|(?<![A-Za-z0-9_])(?:orders?|CO)(?![A-Za-z0-9_])"
    );

    private LexCustomerMasterIntentGuard() {
    }

    public static LexRecognizeResult apply(String utterance, LexRecognizeResult lexResult) {
        if (lexResult == null || !SEARCH_CUSTOMER_ORDER.equals(lexResult.getIntentName())) {
            return lexResult;
        }
        if (!isCustomerMasterUtterance(utterance)) {
            return lexResult;
        }
        String elicit = lexResult.getSlotToElicit();
        if ("CustomerOrderNumber".equals(elicit) || "OrderNumber".equals(elicit)) {
            elicit = "CustomerNumber";
        }
        Map<String, String> slots = new LinkedHashMap<>(lexResult.getSlots());
        if (!slots.containsKey("CustomerNumber")) {
            String fromOrder = slots.get("CustomerOrderNumber");
            if (fromOrder != null && !fromOrder.isBlank()) {
                slots.put("CustomerNumber", fromOrder);
            }
        }
        log.info(
                "Lex intent override SearchCustomerOrder -> GetCustomer (customer master utterance; Lex console samples not in repo)"
        );
        return new LexRecognizeResult(
                GET_CUSTOMER,
                lexResult.getIntentState(),
                lexResult.getDialogActionType(),
                elicit,
                slots,
                lexResult.getMessages(),
                lexResult.getSessionAttributes()
        );
    }

    static boolean isCustomerMasterUtterance(String utterance) {
        if (utterance == null || utterance.isBlank()) {
            return false;
        }
        String normalized = utterance.trim().replaceAll("\\s+", " ");
        if (ORDER_WORDS.matcher(normalized).find()) {
            return false;
        }
        return CUSTOMER_MASTER.matcher(normalized).find();
    }
}
