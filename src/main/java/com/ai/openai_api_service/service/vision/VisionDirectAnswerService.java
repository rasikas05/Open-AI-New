package com.ai.openai_api_service.service.vision;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.service.LlmChatCompletionsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Direct multimodal vision answer used when retrieval/grounding evidence is weak
 * or vision query generation fails. Never logs Base64 or image bytes.
 */
@Service
public class VisionDirectAnswerService {

    private static final Logger log = LoggerFactory.getLogger(VisionDirectAnswerService.class);

    private static final String SYSTEM_PROMPT = """
            You are the M3 AI Assistant for Infor M3 / CloudSuite with vision.

            Answer the user's question using all attached images and the question text. \
            Consider every image; do not ignore any of them.
            Rules:
            - Do not invent program IDs, error codes, customer numbers, order numbers, or other \
            identifiers that are not clearly readable in the images.
            - Do not retrieve or invent live tenant / MI data. If live data is required, say so \
            and ask the user to switch to Auto or M3 mode with the relevant ID.
            - Prefer factual interpretation of visible UI, messages, and documentation cues.
            - If uncertain, say what you can and cannot determine from the images.
            - Respond in the language indicated by the response locale when provided; otherwise \
            match the user's question language.
            """;

    private final LlmChatCompletionsClient llmClient;
    private final int maxCompletionTokens;

    public VisionDirectAnswerService(
            LlmChatCompletionsClient llmClient,
            @Value("${chat.vision.direct-answer.max-completion-tokens:1024}") int maxCompletionTokens
    ) {
        this.llmClient = llmClient;
        this.maxCompletionTokens = maxCompletionTokens > 0 ? maxCompletionTokens : 1024;
    }

    public VisionDirectAnswerResult answer(
            String originalQuestion,
            ValidatedImageAttachment image,
            String resolvedLocale
    ) {
        return answer(originalQuestion, image == null ? List.of() : List.of(image), resolvedLocale);
    }

    public VisionDirectAnswerResult answer(
            String originalQuestion,
            List<ValidatedImageAttachment> images,
            String resolvedLocale
    ) {
        if (images == null || images.isEmpty()) {
            throw new OpenAIException("Validated image attachment is required for vision direct answer.", 400);
        }
        for (ValidatedImageAttachment image : images) {
            if (image == null || image.bytes() == null || image.bytes().length == 0) {
                throw new OpenAIException("Validated image attachment is required for vision direct answer.", 400);
            }
            if (image.mediaType() == null || image.mediaType().isBlank()) {
                throw new OpenAIException("Validated image mediaType is required for vision direct answer.", 400);
            }
        }

        String userText = resolveUserPromptText(originalQuestion, resolvedLocale);
        List<Map<String, Object>> userContent = VisionMultimodalRequestSupport.buildUserContentParts(
                userText,
                images
        );
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userContent)
        );

        int aggregateBytes = images.stream().mapToInt(img -> img.bytes().length).sum();
        log.info(
                "Vision direct answer starting imageCount={} aggregateDecodedBytes={} promptChars={} locale={}",
                images.size(),
                aggregateBytes,
                userText.length(),
                resolvedLocale != null ? resolvedLocale : "none"
        );

        LlmChatCompletionsClient.ChatCompletionResult completion;
        try {
            completion = llmClient.completeMultimodal(messages, 0.3, maxCompletionTokens, false, "VisionAnswer");
        } catch (OpenAIException e) {
            throw VisionQueryGenerationService.sanitizeException(e);
        }

        String reply = completion.content() != null ? completion.content().strip() : "";
        if (reply.isEmpty()) {
            throw new OpenAIException("Vision direct answer returned an empty model response.", 502);
        }

        OpenAIUsage usage = completion.usage() != null
                ? completion.usage()
                : new OpenAIUsage(0, 0, 0, null);
        log.info(
                "Vision direct answer completed elapsedMs={} replyChars={}",
                completion.elapsedMs(),
                reply.length()
        );
        return new VisionDirectAnswerResult(reply, usage, completion.elapsedMs());
    }

    static String resolveUserPromptText(String originalQuestion, String resolvedLocale) {
        String question;
        if (VisionQueryGenerationService.isImageOnlySentinel(originalQuestion)) {
            question = "Describe and explain what is shown in this Infor M3 / CloudSuite image.";
        } else {
            question = originalQuestion;
        }
        if (resolvedLocale != null && !resolvedLocale.isBlank()) {
            return question + "\n\nResponse locale: " + resolvedLocale.trim();
        }
        return question;
    }
}
