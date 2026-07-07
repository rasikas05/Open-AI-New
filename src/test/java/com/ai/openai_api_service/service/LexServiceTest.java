package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.LexProperties;
import com.ai.openai_api_service.model.ChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.lexruntimev2.LexRuntimeV2Client;
import software.amazon.awssdk.services.lexruntimev2.model.DialogAction;
import software.amazon.awssdk.services.lexruntimev2.model.Intent;
import software.amazon.awssdk.services.lexruntimev2.model.RecognizeTextRequest;
import software.amazon.awssdk.services.lexruntimev2.model.RecognizeTextResponse;
import software.amazon.awssdk.services.lexruntimev2.model.SessionState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LexServiceTest {

    @Mock
    private LexRuntimeV2Client lexClient;

    private LexService lexService;

    @BeforeEach
    void setUp() {
        LexProperties lexProperties = new LexProperties();
        lexProperties.setEnabled(true);
        lexProperties.setRegion("eu-central-1");
        lexProperties.setBotId("BOT123");
        lexProperties.setBotAliasId("ALIAS456");
        lexProperties.setLocaleId("en_US");
        lexService = new LexService(lexClient, lexProperties);
    }

    @Test
    void buildLexSessionId_composesTenantUserSession() {
        ChatRequest request = new ChatRequest();
        request.setTenantCode("t1");
        request.setUserId("u1");
        request.setSessionId("s1");

        assertEquals("t1:u1:s1", lexService.buildLexSessionId(request));
    }

    @Test
    void buildLexSessionId_sanitizesSpacesAndInvalidChars() {
        ChatRequest request = new ChatRequest();
        request.setTenantCode("Test 4");
        request.setUserId("rasika");
        request.setSessionId("session-10015");

        assertEquals("Test_4:rasika:session-10015", lexService.buildLexSessionId(request));
    }

    @Test
    void sanitizeLexSessionPart_replacesDisallowedCharacters() {
        assertEquals("Test_4", LexService.sanitizeLexSessionPart("Test 4"));
        assertEquals("unknown", LexService.sanitizeLexSessionPart("  "));
    }

    @Test
    void recognizeText_parsesElicitSlotResponse() {
        RecognizeTextResponse awsResponse = RecognizeTextResponse.builder()
                .sessionState(SessionState.builder()
                        .intent(Intent.builder()
                                .name("GetCustomer")
                                .state("InProgress")
                                .build())
                        .dialogAction(DialogAction.builder()
                                .type("ElicitSlot")
                                .slotToElicit("CustomerNumber")
                                .build())
                        .build())
                .build();
        when(lexClient.recognizeText(any(RecognizeTextRequest.class))).thenReturn(awsResponse);

        var result = lexService.recognizeText("t1:u1:s1", "show customer");

        assertEquals("GetCustomer", result.getIntentName());
        assertEquals("CustomerNumber", result.getSlotToElicit());
        verify(lexClient).recognizeText(any(RecognizeTextRequest.class));
    }
}
