package com.ai.openai_api_service.service.timing;

import com.ai.openai_api_service.config.CorrelationIdSupport;
import com.ai.openai_api_service.config.LexProperties;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.service.LexService;
import com.ai.openai_api_service.service.i18n.LanguageDetectionResult;
import com.ai.openai_api_service.service.i18n.LanguageDetectionService;
import com.ai.openai_api_service.service.i18n.SupportedResponseLanguages;
import com.ai.openai_api_service.service.i18n.TranslationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.lexruntimev2.LexRuntimeV2Client;
import software.amazon.awssdk.services.lexruntimev2.model.LexRuntimeV2Exception;
import software.amazon.awssdk.services.lexruntimev2.model.RecognizeTextRequest;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateException;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowTraceLogTest {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    @Mock
    private TranslateClient translateClient;
    @Mock
    private LanguageDetectionService languageDetectionService;
    @Mock
    private LexRuntimeV2Client lexClient;

    @AfterEach
    void clearTrace() {
        FlowTrace.clear();
        CorrelationIdSupport.clear();
    }

    @Test
    void germanLiveOrder_printsStoredTimestampsAndOutcomes() {
        Instant questionAt = Instant.parse("2026-10-23T04:55:43.102Z");
        Instant languageAt = Instant.parse("2026-10-23T04:55:43.108Z");
        Instant translationAt = Instant.parse("2026-10-23T04:55:43.112Z");
        Instant routeAt = Instant.parse("2026-10-23T04:55:43.121Z");
        Instant rejectedAt = Instant.parse("2026-10-23T04:55:43.281Z");
        Instant repairedAt = Instant.parse("2026-10-23T04:55:43.282Z");
        Instant miAt = Instant.parse("2026-10-23T04:55:43.290Z");
        Instant handoffAt = Instant.parse("2026-10-23T04:55:43.295Z");
        Instant localeAt = Instant.parse("2026-10-23T04:55:43.310Z");
        List<FlowTrace.Event> events = List.of(
                event(questionAt, "CHAT", "question=Zeige den Kundenauftrag 0095000003.", FlowTrace.Color.NONE),
                event(languageAt, "LANG", "detected=German locale=de_DE", FlowTrace.Color.GREEN),
                event(translationAt, "TRANS", "source=de target=en status=OK text=Show customer order 0095000003.", FlowTrace.Color.CYAN),
                event(routeAt, "ROUTE", "python=live", FlowTrace.Color.GREEN),
                event(rejectedAt, "SLOT", "rejected CustomerOrderNumber=0095000003.", FlowTrace.Color.YELLOW),
                event(repairedAt, "SLOT", "repaired CustomerOrderNumber=0095000003", FlowTrace.Color.GREEN),
                event(miAt, "MI", "program=OIS100MI transaction=SearchHead params=SQRY=ORNO:0095000003", FlowTrace.Color.GREEN),
                event(handoffAt, "M3", "handedToWidget=true Spring M3 execution=NO", FlowTrace.Color.BLUE),
                event(localeAt, "CHAT", "locale=de_DE outgoingTranslation=skipped widgetLabels=rendered by widget", FlowTrace.Color.NONE)
        );

        String plain = FlowTraceLog.format(events, false);
        String colored = FlowTraceLog.format(events, true);

        assertTrue(plain.contains(TIME.format(questionAt) + " CHAT  question=Zeige den Kundenauftrag 0095000003."));
        assertTrue(plain.contains(TIME.format(languageAt) + " LANG  detected=German locale=de_DE"));
        assertTrue(plain.contains(TIME.format(translationAt) + " TRANS  source=de target=en status=OK text=Show customer order 0095000003."));
        assertTrue(plain.contains(TIME.format(routeAt) + " ROUTE  python=live"));
        assertTrue(plain.contains(TIME.format(rejectedAt) + " SLOT  rejected CustomerOrderNumber=0095000003."));
        assertTrue(plain.contains(TIME.format(repairedAt) + " SLOT  repaired CustomerOrderNumber=0095000003"));
        assertTrue(plain.contains(TIME.format(miAt) + " MI  program=OIS100MI transaction=SearchHead params=SQRY=ORNO:0095000003"));
        assertTrue(plain.contains(TIME.format(handoffAt) + " M3  handedToWidget=true Spring M3 execution=NO"));
        assertTrue(plain.contains(TIME.format(localeAt) + " CHAT  locale=de_DE outgoingTranslation=skipped widgetLabels=rendered by widget"));
        assertFalse(plain.contains("\u001B"));
        assertTrue(colored.contains("\u001B[33m"));
        assertTrue(colored.contains("\u001B[32m"));
        assertTrue(colored.contains("\u001B[36m"));
        assertTrue(colored.contains(TIME.format(questionAt)));
        assertTrue(colored.contains(TIME.format(localeAt)));
        assertFalse(TIME.format(questionAt).equals(TIME.format(localeAt)));
    }

    @Test
    void englishRequest_recordsTranslationSkippedWithoutAResult() {
        TranslationService translationService = new TranslationService(
                translateClient,
                languageDetectionService,
                new SupportedResponseLanguages(""),
                null
        );
        when(languageDetectionService.bypassesTranslation("How do I display a customer?")).thenReturn(false);
        FlowTrace.begin();

        translationService.normalize("How do I display a customer?", detected("en"));

        String block = FlowTraceLog.render(FlowTrace.current(), false);
        assertTrue(block.contains("source=en status=SKIPPED"));
        assertFalse(block.contains("text="));
        assertFalse(block.contains("target=en"));
    }

    @Test
    void noSlotRepair_omitsSlotLines() {
        String block = FlowTraceLog.format(List.of(
                event(Instant.parse("2026-10-23T04:55:43.108Z"), "LANG", "detected=German locale=de_DE", FlowTrace.Color.GREEN),
                event(Instant.parse("2026-10-23T04:55:43.121Z"), "ROUTE", "python=live", FlowTrace.Color.GREEN)
        ), false);

        assertFalse(block.contains("SLOT"));
        assertFalse(block.contains("rejected"));
        assertFalse(block.contains("repaired"));
    }

    @Test
    void translationFailure_doesNotInventLexOrMi() {
        TranslationService translationService = new TranslationService(
                translateClient,
                languageDetectionService,
                new SupportedResponseLanguages(""),
                null
        );
        when(languageDetectionService.bypassesTranslation("Zeige den Kundenauftrag 0095000003.")).thenReturn(false);
        when(translateClient.translateText(any(TranslateTextRequest.class))).thenThrow(
                TranslateException.builder().message("denied").statusCode(400).build()
        );
        FlowTrace.begin();

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> translationService.normalize("Zeige den Kundenauftrag 0095000003.", detected("de"))
        );

        String block = FlowTraceLog.render(FlowTrace.current(), false);
        assertEquals(422, ex.getStatusCode());
        assertEquals("The message could not be translated.", ex.getMessage());
        assertTrue(block.contains("status=FAILED errorType=TranslateException"));
        assertFalse(block.contains("text="));
        assertFalse(block.contains("LEX"));
        assertFalse(block.contains("program="));
        assertFalse(block.contains("denied"));
    }

    @Test
    void lexFailure_doesNotInventIntentOrMi() {
        LexProperties lexProperties = new LexProperties();
        lexProperties.setEnabled(true);
        lexProperties.setRegion("eu-west-1");
        lexProperties.setBotId("BOT123");
        lexProperties.setBotAliasId("ALIAS456");
        lexProperties.setLocaleId("en_US");
        LexService lexService = new LexService(lexClient, lexProperties);
        when(lexClient.recognizeText(any(RecognizeTextRequest.class))).thenThrow(
                LexRuntimeV2Exception.builder().message("lex down").build()
        );
        FlowTrace.begin();

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> lexService.recognizeText("tenant:user:session", "Show customer order 0095000003.")
        );

        String block = FlowTraceLog.render(FlowTrace.current(), false);
        assertEquals(502, ex.getStatusCode());
        assertTrue(block.contains("LEX  status=FAILED errorType=LexRuntimeV2Exception"));
        assertFalse(block.contains("intent="));
        assertFalse(block.contains("program="));
        assertFalse(block.contains("lex down"));
    }

    @Test
    void exceptionDuringProcessing_flushesRecordedEventsAndClears() {
        FlowTrace.begin();
        FlowTrace.question("Zeige den Kundenauftrag 0095000003.");
        List<String> flushed = new ArrayList<>();
        IllegalStateException original = new IllegalStateException("chat failed");

        IllegalStateException seen = assertThrows(IllegalStateException.class, () -> {
            try {
                throw original;
            } finally {
                FlowTraceLog.finish(trace -> FlowTraceLog.render(trace, false), flushed::add);
            }
        });

        assertSame(original, seen);
        assertEquals(0, original.getSuppressed().length);
        assertEquals(1, flushed.size());
        assertTrue(flushed.getFirst().contains("question=Zeige den Kundenauftrag 0095000003."));
        assertNull(FlowTrace.current());
    }

    @Test
    void formatterFailure_doesNotMaskTheOriginalExceptionAndClears() {
        FlowTrace.begin();
        FlowTrace.question("Zeige den Kundenauftrag 0095000003.");
        List<String> flushed = new ArrayList<>();
        IllegalStateException original = new IllegalStateException("chat failed");

        IllegalStateException seen = assertThrows(IllegalStateException.class, () -> {
            try {
                throw original;
            } finally {
                FlowTraceLog.finish(trace -> {
                    throw new IllegalArgumentException("ansi");
                }, flushed::add);
            }
        });

        assertSame(original, seen);
        assertEquals(0, original.getSuppressed().length);
        assertTrue(flushed.isEmpty());
        assertNull(FlowTrace.current());
    }

    @Test
    void sequentialRequests_doNotShareEvents() {
        FlowTrace.begin();
        FlowTrace.question("first question");
        List<String> flushed = new ArrayList<>();
        FlowTraceLog.finish(trace -> FlowTraceLog.render(trace, false), flushed::add);

        FlowTrace.begin();
        FlowTrace.question("second question");
        String second = FlowTraceLog.render(FlowTrace.current(), false);

        assertEquals(1, flushed.size());
        assertTrue(flushed.getFirst().contains("first question"));
        assertFalse(second.contains("first question"));
        assertTrue(second.contains("second question"));
    }

    @Test
    void questionIsCappedAndPresidioBodyIsNotLogged() {
        FlowTrace.begin();
        FlowTrace.question("x".repeat(121) + "SECRET-TAIL");
        FlowTrace.presidioChanged(true);
        FlowTrace.language(new LanguageDetectionResult(
                "de", "de_DE", "es", "de", "de_DE", 1.0, 0.62, false
        ));

        String block = FlowTraceLog.render(FlowTrace.current(), false);

        assertFalse(block.contains("SECRET-TAIL"));
        assertTrue(block.contains("presidio textChanged=true"));
        assertFalse(block.contains("sanitized"));
        assertTrue(block.contains("detected=de"));
        assertTrue(block.contains("detectedLocale=de_DE"));
        assertTrue(block.contains("selected=de"));
        assertTrue(block.contains("selectedLocale=de_DE"));
    }

    @Test
    void recordedTimestampIsTheEventTimeNotTheFormatTime() throws InterruptedException {
        FlowTrace.begin();
        FlowTrace.question("Zeige den Kundenauftrag 0095000003.");
        Instant recorded = FlowTrace.current().events().getFirst().occurredAt();
        Thread.sleep(5);

        String block = FlowTraceLog.render(FlowTrace.current(), false);

        assertTrue(block.startsWith(TIME.format(recorded) + " CHAT"));
    }

    @Test
    void flowSummaryIsOneLineAndLiveRouteIsNotSkipped() {
        FlowTrace.begin();
        FlowTrace.question("Zeige den Kundenauftrag 0095000003.");
        FlowTrace.recordLlmCall();
        FlowTrace.markLanguage("certain", "de", "de_DE");
        FlowTrace.markTranslation("ok");
        FlowTrace.markGapFill("skipped", "not_applicable");
        FlowTrace.markRouting("AUTO", "LIVE", "SKIP", "lex", true, false, 188);
        MDC.put(CorrelationIdSupport.MDC_KEY, "cea0ff33-18d6-49a3-80a5-779bef5e12e9");

        String live = FlowTraceLog.formatSummary(FlowTrace.current());

        assertEquals(1, live.split("\\[FLOW\\]", -1).length - 1);
        assertTrue(live.contains("correlationId=cea0ff33-18d6-49a3-80a5-779bef5e12e9"), live);
        assertTrue(live.contains("mode=AUTO"), live);
        assertTrue(live.contains("language=certain detected=de locale=de_DE"));
        assertTrue(live.contains("translation=ok route=live planner=skipped"));
        assertTrue(live.contains("handler=lex lex=called rag=skipped"));
        assertTrue(live.contains("gapFill=skipped reason=not_applicable"));
        assertTrue(live.contains("llmCalls=1 durationMs=188 outcome=SUCCESS error=none"));
        assertTrue(live.contains("question=Zeige den Kundenauftrag 0095000003."));
        assertTrue(live.contains("translatedEn=-"));
        assertTrue(live.contains("bip=-"));
        assertTrue(live.contains("llmText=-"));
        assertTrue(live.contains("plannerQueries=-"));
        assertTrue(live.contains("retrievalQuery=-"));
        assertFalse(live.contains("route=skipped"));

        FlowTrace.clear();
        CorrelationIdSupport.clear();
        FlowTrace.begin();
        FlowTrace.markLanguage("uncertain", "none", "unchanged");
        FlowTrace.markTranslation("skipped");
        FlowTrace.recordLlmCall();
        FlowTrace.markRouting("AUTO", "SKIP", "SKIP", "general", false, false, 40);

        String uncertain = FlowTraceLog.formatSummary(FlowTrace.current());

        assertTrue(uncertain.contains("correlationId=-"), uncertain);
        assertTrue(uncertain.contains("language=uncertain detected=none locale=unchanged"));
        assertTrue(uncertain.contains("translation=skipped route=skipped planner=skipped"));
        assertTrue(uncertain.contains("handler=general lex=skipped rag=skipped"));
        assertTrue(uncertain.contains("llmCalls=1"));
        assertTrue(uncertain.contains("question=-"));
        assertTrue(uncertain.contains("retrievalQuery=-"));
    }

    @Test
    void translationOk_storesTranslatedEnglishAndEmitsTransEvent() {
        when(languageDetectionService.bypassesTranslation("Zeige den Kundenauftrag.")).thenReturn(false);
        when(translateClient.translateText(any(TranslateTextRequest.class))).thenReturn(
                software.amazon.awssdk.services.translate.model.TranslateTextResponse.builder()
                        .translatedText("Show the customer order.")
                        .build()
        );
        TranslationService translationService = new TranslationService(
                translateClient,
                languageDetectionService,
                new SupportedResponseLanguages(""),
                null
        );
        FlowTrace.begin();

        translationService.normalize("Zeige den Kundenauftrag.", detected("de"));

        FlowTrace trace = FlowTrace.current();
        assertEquals("Show the customer order.", trace.translatedEnglish());
        String block = FlowTraceLog.render(trace, false);
        assertTrue(block.contains("TRANS  source=de target=en status=OK text=Show the customer order."));
        String summary = FlowTraceLog.formatSummary(trace);
        assertTrue(summary.contains("translatedEn=Show the customer order."));
    }

    @Test
    void protectionCheckpoint_storesBipAndLlmIndependently() {
        FlowTrace.begin();
        FlowTrace.protectionCheckpoint(
                "How do I create a purchase order in M3?",
                "How do I create a purchase order in <PURCHASE_ORDER_NUMBER>?",
                true,
                "Purchase_Order_Number"
        );

        FlowTrace trace = FlowTrace.current();
        assertEquals("How do I create a purchase order in M3?", trace.bipText());
        assertEquals("How do I create a purchase order in <PURCHASE_ORDER_NUMBER>?", trace.llmTextCheckpoint());
        assertEquals("Purchase_Order_Number", trace.bipPlaceholders());
        String summary = FlowTraceLog.formatSummary(trace);
        assertTrue(summary.contains("bip=How do I create a purchase order in M3?"));
        assertTrue(summary.contains("llmText=How do I create a purchase order in <PURCHASE_ORDER_NUMBER>?"));
        assertTrue(summary.contains("bipPlaceholders=Purchase_Order_Number"));
        assertTrue(FlowTraceLog.render(trace, false).contains("PROT  businessApplied=true"));
    }

    @Test
    void plannerQueries_storesTypeAndUpToThreeQueriesIncludingEmpty() {
        FlowTrace.begin();
        FlowTrace.plannerQueries("RAG", List.of(
                "customer master maintain steps",
                "M3 customer master how to",
                "maintain customer master program",
                "fourth ignored"
        ));

        FlowTrace trace = FlowTrace.current();
        assertEquals("RAG", trace.plannerType());
        assertEquals(
                "q1=customer master maintain steps; q2=M3 customer master how to; q3=maintain customer master program",
                trace.plannerQueries()
        );
        assertTrue(FlowTraceLog.formatSummary(trace).contains("plannerType=RAG"));
        assertTrue(FlowTraceLog.formatSummary(trace).contains("plannerQueries=q1=customer master maintain steps"));

        FlowTrace.clear();
        FlowTrace.begin();
        FlowTrace.plannerQueries("CONVERSATIONAL", null);
        assertEquals("-", FlowTrace.current().plannerQueries());
        FlowTrace.plannerQueries("CONVERSATIONAL", List.of());
        assertEquals("-", FlowTrace.current().plannerQueries());
        FlowTrace.plannerQueries("RAG", java.util.Arrays.asList("  ", null));
        assertEquals("-", FlowTrace.current().plannerQueries());
    }

    @Test
    void conversationalSummary_keepsMissingRetrievalAsDash() {
        FlowTrace.begin();
        FlowTrace.question("Hi tu mla kay madat kru shakatos");
        FlowTrace.translationOk("mr", "Hi, what help can you give me?");
        FlowTrace.protectionCheckpoint(
                "Hi, what help can you give me?",
                "Hi, what help can you give me?",
                false,
                "-"
        );
        FlowTrace.plannerQueries("CONVERSATIONAL", List.of());
        FlowTrace.markRouting("AUTO", "RAG", "CONVERSATIONAL", "planner", false, false, 2048);

        String summary = FlowTraceLog.formatSummary(FlowTrace.current());
        assertTrue(summary.contains("translatedEn=Hi, what help can you give me?"));
        assertTrue(summary.contains("plannerType=CONVERSATIONAL"));
        assertTrue(summary.contains("plannerQueries=-"));
        assertTrue(summary.lines().anyMatch(line -> line.equals("retrievalQuery=-")));
    }

    @Test
    void longCheckpointTextIsCappedAndRetrievalQueriesNumbered() {
        FlowTrace.begin();
        String longText = "a".repeat(130) + "TAIL";
        FlowTrace.translationOk("hi", longText);
        FlowTrace.protectionCheckpoint(longText, longText, false, "-");
        FlowTrace.retrievalQuery(List.of(
                "purchase order create steps",
                "how to create PO in M3 | CloudSuite"
        ));

        FlowTrace trace = FlowTrace.current();
        assertFalse(trace.translatedEnglish().contains("TAIL"));
        assertEquals(RoutingSummaryLog.MAX_REQUEST_CHARS, trace.translatedEnglish().length());
        assertEquals(
                "q1=purchase order create steps; q2=how to create PO in M3 | CloudSuite",
                trace.retrievalQuery()
        );
        String summary = FlowTraceLog.formatSummary(trace);
        assertTrue(summary.contains("retrievalQuery=q1=purchase order create steps; q2=how to create PO in M3 | CloudSuite"));
        assertFalse(summary.contains("TAIL"));
    }

    private static FlowTrace.Event event(Instant at, String stage, String message, FlowTrace.Color color) {
        return new FlowTrace.Event(at, stage, message, color);
    }

    private static LanguageDetectionResult detected(String language) {
        String locale = "de".equals(language) ? "de_DE" : language;
        return new LanguageDetectionResult(language, locale, "none", language, locale, 1.0, null, false);
    }
}
