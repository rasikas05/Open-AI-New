package com.ai.openai_api_service.model.lex;

import software.amazon.awssdk.services.lexruntimev2.model.Message;
import software.amazon.awssdk.services.lexruntimev2.model.RecognizeTextResponse;
import software.amazon.awssdk.services.lexruntimev2.model.SessionState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LexRecognizeResult {

    public static final String FALLBACK_INTENT = "FallbackIntent";
    public static final String DIALOG_ELICIT_SLOT = "ElicitSlot";
    public static final String INTENT_STATE_READY = "ReadyForFulfillment";

    private final String intentName;
    private final String intentState;
    private final String dialogActionType;
    private final String slotToElicit;
    private final Map<String, String> slots;
    private final List<String> messages;

    public LexRecognizeResult(
            String intentName,
            String intentState,
            String dialogActionType,
            String slotToElicit,
            Map<String, String> slots,
            List<String> messages
    ) {
        this.intentName = intentName;
        this.intentState = intentState;
        this.dialogActionType = dialogActionType;
        this.slotToElicit = slotToElicit;
        this.slots = slots != null ? Map.copyOf(slots) : Map.of();
        this.messages = messages != null ? List.copyOf(messages) : List.of();
    }

    public static LexRecognizeResult fromResponse(RecognizeTextResponse response) {
        SessionState state = response.sessionState();
        String intentName = null;
        String intentState = null;
        Map<String, String> slots = new LinkedHashMap<>();

        if (state != null && state.intent() != null) {
            intentName = state.intent().name();
            intentState = state.intent().stateAsString();
            if (state.intent().slots() != null) {
                state.intent().slots().forEach((slotName, slot) -> {
                    if (slot == null) {
                        return;
                    }
                    String value = null;
                    if (slot.value() != null) {
                        if (slot.value().interpretedValue() != null && !slot.value().interpretedValue().isBlank()) {
                            value = slot.value().interpretedValue();
                        } else if (slot.value().originalValue() != null && !slot.value().originalValue().isBlank()) {
                            value = slot.value().originalValue();
                        }
                    }
                    if (value != null) {
                        slots.put(slotName, value);
                    }
                });
            }
        }

        String dialogActionType = null;
        String slotToElicit = null;
        if (state != null && state.dialogAction() != null) {
            dialogActionType = state.dialogAction().typeAsString();
            slotToElicit = state.dialogAction().slotToElicit();
        }

        List<String> messages = new ArrayList<>();
        if (response.messages() != null) {
            for (Message message : response.messages()) {
                if (message != null && message.content() != null && !message.content().isBlank()) {
                    messages.add(message.content());
                }
            }
        }

        return new LexRecognizeResult(
                intentName,
                intentState,
                dialogActionType,
                slotToElicit,
                slots,
                messages
        );
    }

    public String getIntentName() {
        return intentName;
    }

    public String getIntentState() {
        return intentState;
    }

    public String getDialogActionType() {
        return dialogActionType;
    }

    public String getSlotToElicit() {
        return slotToElicit;
    }

    public Map<String, String> getSlots() {
        return slots;
    }

    public List<String> getMessages() {
        return messages;
    }

    public String firstMessage() {
        return messages.isEmpty() ? "" : messages.get(0);
    }

    public boolean isElicitSlot() {
        return DIALOG_ELICIT_SLOT.equalsIgnoreCase(dialogActionType);
    }

    public boolean isReadyForFulfillment() {
        return INTENT_STATE_READY.equalsIgnoreCase(intentState);
    }

    public boolean isFallbackIntent() {
        return FALLBACK_INTENT.equalsIgnoreCase(intentName);
    }
}
