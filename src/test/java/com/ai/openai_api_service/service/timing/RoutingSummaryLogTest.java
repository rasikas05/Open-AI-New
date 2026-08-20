package com.ai.openai_api_service.service.timing;

import com.ai.openai_api_service.model.ChatMode;
import com.ai.openai_api_service.model.RequestUnderstandType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingSummaryLogTest {

    @Test
    void format_autoLivePythonFirst() {
        RoutingSummaryState state = base("show customer C10001", ChatMode.AUTO);
        state.setPythonRoute("LIVE");
        state.setPlanner("SKIP");
        state.setRoute("live");
        state.setHandler("lex");
        state.setAction("lex_elicit_intent");

        String text = RoutingSummaryLog.format(state, true, false, 8480);

        assertEquals(
                "[ROUTING] \"show customer C10001\" | mode=AUTO | python=LIVE | planner=SKIP"
                        + " | route=live | handler=lex | action=lex_elicit_intent | lex=CALLED | rag=SKIP | total=8.48s",
                text
        );
    }

    @Test
    void format_autoRagPlanner() {
        RoutingSummaryState state = base("what is OIS100?", ChatMode.AUTO);
        state.setPythonRoute("RAG");
        state.setPlanner("RAG");
        state.setType(RequestUnderstandType.RAG);
        state.setRoute("rag");
        state.setHandler("documentation/retrieval");
        state.setAction("rag");

        String text = RoutingSummaryLog.format(state, false, true, 18160);

        assertTrue(text.contains("python=RAG"));
        assertTrue(text.contains("planner=RAG"));
        assertTrue(text.contains("type=RAG"));
        assertTrue(text.contains("lex=SKIP"));
        assertTrue(text.contains("rag=CALLED"));
        assertTrue(text.contains("total=18.16s"));
    }

    @Test
    void format_docsLiveSteer() {
        RoutingSummaryState state = base("fetch customer Y11100", ChatMode.DOCS);
        state.setPythonRoute("SKIP");
        state.setPlanner("LIVE_M3");
        state.setType(RequestUnderstandType.LIVE_M3);
        state.setRoute("docs_live_steer");
        state.setHandler("planner");
        state.setAction("docs_live_steer");

        String text = RoutingSummaryLog.format(state, false, false, 5490);

        assertTrue(text.contains("python=SKIP"));
        assertTrue(text.contains("planner=LIVE_M3"));
        assertTrue(text.contains("route=docs_live_steer"));
        assertTrue(text.contains("lex=SKIP"));
        assertTrue(text.contains("rag=SKIP"));
    }

    @Test
    void format_pythonErrorFallback() {
        RoutingSummaryState state = base("what is the weather?", ChatMode.AUTO);
        state.setPythonRoute("ERROR");
        state.setFallback("RAG");
        state.setPlanner("NON_M3");
        state.setType(RequestUnderstandType.NON_M3);
        state.setRoute("general_redirect");
        state.setHandler("planner");
        state.setAction("general_redirect");

        String text = RoutingSummaryLog.format(state, false, false, 5490);

        assertTrue(text.contains("python=ERROR"));
        assertTrue(text.contains("fallback=RAG"));
        assertTrue(text.contains("planner=NON_M3"));
    }

    @Test
    void format_pendingLex() {
        RoutingSummaryState state = base("Y00111", ChatMode.M3);
        state.setPythonRoute("SKIP");
        state.setPlanner("SKIP");
        state.setRoute("live");
        state.setHandler("lex-pending");
        state.setAction("lex_elicit_intent");

        String text = RoutingSummaryLog.format(state, true, false, 1100);

        assertTrue(text.contains("python=SKIP"));
        assertTrue(text.contains("planner=SKIP"));
        assertFalse(text.contains("type="));
        assertTrue(text.contains("handler=lex-pending"));
        assertTrue(text.contains("lex=CALLED"));
    }

    private static RoutingSummaryState base(String request, ChatMode mode) {
        RoutingSummaryState state = new RoutingSummaryState();
        state.setRequestText(request);
        state.setMode(mode);
        return state;
    }
}
