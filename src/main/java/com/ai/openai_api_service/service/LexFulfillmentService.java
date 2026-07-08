package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.InvalidLexSlotException;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.M3RequestDto;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LexFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(LexFulfillmentService.class);

    private final LexIntentMapper lexIntentMapper;

    public LexFulfillmentService(LexIntentMapper lexIntentMapper) {
        this.lexIntentMapper = lexIntentMapper;
    }

    public ChatResponse fulfill(LexRecognizeResult lexResult) {
        try {
            return fulfillMapped(lexResult);
        } catch (InvalidLexSlotException e) {
            log.info(
                    "Lex fulfillment rejected invalid slot: intent='{}' message='{}'",
                    lexResult.getIntentName(),
                    e.getUserMessage()
            );
            ChatResponse chatResponse = new ChatResponse(e.getUserMessage(), false);
            chatResponse.setActionTaken("lex_invalid_slot");
            chatResponse.setLexIntent(lexResult.getIntentName());
            return chatResponse;
        }
    }

    private ChatResponse fulfillMapped(LexRecognizeResult lexResult) {
        LexIntentMapper.MappedM3Request mapped = lexIntentMapper.map(lexResult)
                .orElseThrow(() -> new OpenAIException(
                        "No M3 mapping for Lex intent: " + lexResult.getIntentName(),
                        400
                ));

        log.info(
                "Lex fulfillment: intent='{}' program='{}' transaction='{}' params={}",
                lexResult.getIntentName(),
                mapped.program(),
                mapped.transaction(),
                mapped.params()
        );

        M3RequestDto m3Request = new M3RequestDto(
                true,
                mapped.program(),
                mapped.transaction(),
                mapped.params()
        );

        String reply = buildPlaceholderReply(lexResult.getIntentName(), mapped);
        ChatResponse chatResponse = new ChatResponse(reply, false);
        chatResponse.setActionTaken(mapped.actionTaken());
        chatResponse.setM3Request(m3Request);
        chatResponse.setLexIntent(lexResult.getIntentName());
        return chatResponse;
    }

    private String buildPlaceholderReply(String intentName, LexIntentMapper.MappedM3Request mapped) {
        if ("GetCustomer".equals(intentName)) {
            Object cuno = mapped.params().get("CUNO");
            return "Looking up customer " + cuno + "...";
        }
        return "Processing your request...";
    }
}
