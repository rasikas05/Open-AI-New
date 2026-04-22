package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.config.JwtUtil;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.TokenUsageDto;
import com.ai.openai_api_service.service.ChatPersistenceService;
import com.ai.openai_api_service.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(ChatController.class)
@Import(JwtUtil.class)
class ChatControllerQuotaTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;
    @MockBean
    private ChatPersistenceService chatPersistenceService;

    @Test
    void chatShouldReturn429WhenLimitExceeded() throws Exception {
        ChatResponse response = new ChatResponse("Token limit reached for this tenant. Please top up to continue.", false);
        response.setLimitExceeded(true);
        response.setUsage(new TokenUsageDto(1000, 1000, 0));
        response.setUpgradeOptions(List.of("Buy 100 tokens", "Buy 500 tokens", "Buy 5000 tokens"));
        when(chatService.chat(any())).thenReturn(response);

        mockMvc.perform(post("/api/chat")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"t1\",\"userId\":\"u1\",\"sessionId\":\"s1\",\"userMessage\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitExceeded").value(true))
                .andExpect(jsonPath("$.usage.total").value(1000))
                .andExpect(jsonPath("$.upgradeOptions[0]").value("Buy 100 tokens"));
    }
}
