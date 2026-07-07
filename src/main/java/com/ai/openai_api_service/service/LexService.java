package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.LexProperties;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.lexruntimev2.LexRuntimeV2Client;
import software.amazon.awssdk.services.lexruntimev2.model.LexRuntimeV2Exception;
import software.amazon.awssdk.services.lexruntimev2.model.RecognizeTextRequest;
import software.amazon.awssdk.services.lexruntimev2.model.RecognizeTextResponse;

import java.util.regex.Pattern;

@Service
public class LexService {

    private static final Logger log = LoggerFactory.getLogger(LexService.class);
    /** Lex V2 sessionId pattern: [0-9a-zA-Z._:-]+ */
    private static final Pattern LEX_SESSION_INVALID_CHARS = Pattern.compile("[^0-9a-zA-Z._:-]");

    private final LexRuntimeV2Client lexClient;
    private final LexProperties lexProperties;

    public LexService(LexRuntimeV2Client lexClient, LexProperties lexProperties) {
        this.lexClient = lexClient;
        this.lexProperties = lexProperties;
    }

    public boolean isEnabled() {
        return lexProperties.isEnabled();
    }

    public String buildLexSessionId(ChatRequest request) {
        String lexSessionId = sanitizeLexSessionPart(request.getTenantCode())
                + ":" + sanitizeLexSessionPart(request.getUserId())
                + ":" + sanitizeLexSessionPart(request.getSessionId());
        log.debug("Lex sessionId built: '{}'", lexSessionId);
        return lexSessionId;
    }

    static String sanitizeLexSessionPart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return LEX_SESSION_INVALID_CHARS.matcher(value.trim()).replaceAll("_");
    }

    public LexRecognizeResult recognizeText(String lexSessionId, String text) {
        if (!lexProperties.isEnabled()) {
            throw new OpenAIException("Lex integration is disabled", 503);
        }
        String botId = lexProperties.getBotId();
        String botAliasId = lexProperties.getBotAliasId();
        if (botId == null || botId.isBlank() || botAliasId == null || botAliasId.isBlank()) {
            throw new OpenAIException("Lex bot-id and bot-alias-id must be configured", 500);
        }
        if (text == null || text.isBlank()) {
            throw new OpenAIException("Text cannot be empty for Lex RecognizeText", 400);
        }
        if (lexSessionId == null || lexSessionId.isBlank()) {
            throw new OpenAIException("Lex sessionId cannot be empty", 400);
        }

        RecognizeTextRequest request = RecognizeTextRequest.builder()
                .botId(botId)
                .botAliasId(botAliasId)
                .localeId(lexProperties.getLocaleId())
                .sessionId(lexSessionId)
                .text(text)
                .build();

        try {
            RecognizeTextResponse response = lexClient.recognizeText(request);
            LexRecognizeResult result = LexRecognizeResult.fromResponse(response);
            log.info(
                    "Lex RecognizeText: session='{}' intent='{}' state='{}' dialogAction='{}' slotToElicit='{}' slots={}",
                    lexSessionId,
                    result.getIntentName(),
                    result.getIntentState(),
                    result.getDialogActionType(),
                    result.getSlotToElicit(),
                    result.getSlots()
            );
            return result;
        } catch (LexRuntimeV2Exception e) {
            log.error("Lex RecognizeText failed: {}", e.awsErrorDetails() != null
                    ? e.awsErrorDetails().errorMessage()
                    : e.getMessage());
            throw new OpenAIException("Lex RecognizeText failed: " + e.getMessage(), 502);
        } catch (SdkClientException e) {
            String region = lexProperties.getRegion();
            log.error(
                    "Lex connectivity failed (region={}, endpoint=runtime-v2-lex.{}.amazonaws.com): {}",
                    region,
                    region,
                    e.getMessage(),
                    e
            );
            throw new OpenAIException(
                    "Cannot reach Amazon Lex in region " + region
                            + ". Check DNS/network/VPN or verify lex.region matches your bot's AWS region. "
                            + "Cause: " + e.getMessage(),
                    502
            );
        }
    }
}
