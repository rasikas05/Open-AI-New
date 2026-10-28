package com.ai.openai_api_service.service.timing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FlowTraceAnsi {

    public FlowTraceAnsi(@Value("${chat.flow-log.ansi:true}") boolean ansi) {
        FlowTraceLog.setAnsi(ansi);
    }
}
