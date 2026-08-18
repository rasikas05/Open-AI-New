package com.ai.openai_api_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIServiceExternalPromptTest {

    private String prompt;

    @BeforeEach
    void setUp() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            properties.load(in);
        }
        prompt = properties.getProperty("openai.assistant.system-prompt");
    }

    @Test
    void externalPrompt_enforcesIdentityBoundariesAndConstraints() {
        assertTrue(prompt.contains("M3 AI Assistant"));
        assertTrue(prompt.contains("Never identify as ChatGPT"));
        assertTrue(prompt.contains("not as authoritative documentation"));
        assertTrue(prompt.contains("external technologies connected to M3"));
        assertTrue(prompt.contains("AWS, Azure, or Kubernetes"));
        assertTrue(prompt.contains("politely redirect to M3 / CloudSuite"));
        assertTrue(prompt.contains("word count"));
        assertTrue(prompt.contains("Match response length"));
        assertTrue(prompt.contains("1-3 sentences"));
        assertTrue(prompt.contains("docs.infor.com"));
        assertTrue(prompt.contains("If no verified reference is available, do not invent one"));
        assertTrue(prompt.contains("Do not provide a broad capability list unless requested"));
        assertTrue(prompt.contains("previous user questions already include a greeting or identity/name question"));
        assertTrue(prompt.contains("do not repeat the full identity"));

        assertFalse(prompt.contains("You are ChatGPT"));
        assertFalse(prompt.contains("Input:"));
        assertFalse(prompt.contains("Output:"));
    }

    @Test
    void assistantSystemPrompt_returnsConfiguredFallbackText() {
        OpenAIService openAIService = new OpenAIService(null, null, null);
        ReflectionTestUtils.setField(openAIService, "systemPromptEnabled", true);
        ReflectionTestUtils.setField(openAIService, "systemPrompt", prompt);

        String fallback = openAIService.assistantSystemPrompt();
        assertTrue(fallback.contains("M3 AI Assistant"));
        assertTrue(fallback.contains("Never identify as ChatGPT"));
        assertTrue(fallback.contains("not as authoritative documentation"));
    }
}
