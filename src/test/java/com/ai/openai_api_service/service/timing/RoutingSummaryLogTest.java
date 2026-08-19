package com.ai.openai_api_service.service.timing;

import com.ai.openai_api_service.model.ChatMode;
import com.ai.openai_api_service.model.RequestUnderstandType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingSummaryLogTest {

    @Test
    void format_conversationalM3() {
        RoutingSummaryState state = base("Hi", ChatMode.M3);
        state.setRouter("OpenAI / gpt-5.6-terra");
        state.setType(RequestUnderstandType.CONVERSATIONAL);
        state.setOverride("none");
        state.setRoute("conversational");
        state.setHandler("request-router");
        state.setAction("conversational");

        String text = RoutingSummaryLog.format(state, false, false, 2310);

        assertEquals(
                "[ROUTING] \"Hi\" | mode=M3 | type=CONVERSATIONAL | route=conversational"
                        + " | handler=request-router | action=conversational | lex=SKIP | rag=SKIP | total=2.31s",
                text
        );
        assertFalse(text.contains("router="));
        assertFalse(text.contains("override="));
    }

    @Test
    void format_liveLex() {
        RoutingSummaryState state = base("Show customer Y00111", ChatMode.M3);
        state.setRouter("OpenAI / gpt-5.6-terra");
        state.setType(RequestUnderstandType.LIVE_M3);
        state.setRoute("live");
        state.setHandler("lex");
        state.setIntent("SearchCustomerOrder");
        state.setAction("lex_elicit_intent");

        String text = RoutingSummaryLog.format(state, true, false, 3820);

        assertTrue(text.startsWith("[ROUTING] \"Show customer Y00111\""));
        assertTrue(text.contains("type=LIVE_M3"));
        assertTrue(text.contains("handler=lex"));
        assertTrue(text.contains("intent=SearchCustomerOrder"));
        assertTrue(text.contains("lex=CALLED"));
        assertTrue(text.contains("rag=SKIP"));
        assertTrue(text.contains("total=3.82s"));
        assertFalse(text.contains("router="));
    }

    @Test
    void format_m3RagSteer() {
        RoutingSummaryState state = base("What is OIS100?", ChatMode.M3);
        state.setRouter("OpenAI / gpt-5.6-terra");
        state.setType(RequestUnderstandType.RAG);
        state.setOverride("RAG -> m3_live_steer");
        state.setRoute("m3_live_steer");
        state.setHandler("request-router");
        state.setAction("m3_live_steer");

        String text = RoutingSummaryLog.format(state, false, false, 2810);

        assertTrue(text.contains("override=RAG->m3_live_steer"));
        assertTrue(text.contains("route=m3_live_steer"));
        assertTrue(text.contains("lex=SKIP"));
        assertFalse(text.contains("SKIPPED"));
    }

    @Test
    void format_pendingLex() {
        RoutingSummaryState state = base("Y00111", ChatMode.M3);
        state.setRouter("skipped (pending-lex)");
        state.setTypeRaw("-");
        state.setRoute("live");
        state.setHandler("lex-pending");
        state.setAction("lex_elicit_intent");

        String text = RoutingSummaryLog.format(state, true, false, 1100);

        assertTrue(text.contains("router=SKIP(pending-lex)"));
        assertFalse(text.contains("type="));
        assertTrue(text.contains("handler=lex-pending"));
        assertTrue(text.contains("lex=CALLED"));
    }

    @Test
    void format_failureMissingAction() {
        RoutingSummaryState state = base("Show customer Y00111", ChatMode.M3);
        state.setRouter("skipped (router-error)");
        state.setRoute("live");
        state.setHandler("lex");

        String text = RoutingSummaryLog.format(state, false, false, 500);

        assertTrue(text.contains("action=-"));
        assertTrue(text.contains("lex=SKIP"));
        assertFalse(text.contains("lex=CALLED"));
        assertTrue(text.contains("router=SKIP(router-error)"));
    }

    private static RoutingSummaryState base(String request, ChatMode mode) {
        RoutingSummaryState state = new RoutingSummaryState();
        state.setRequestText(request);
        state.setMode(mode);
        return state;
    }
}
