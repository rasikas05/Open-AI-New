package com.ai.openai_api_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresidioServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PresidioService presidioService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(presidioService, "enabled", true);
        ReflectionTestUtils.setField(presidioService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(presidioService, "apiKeyHeader", "x-api-key");
        ReflectionTestUtils.setField(presidioService, "analyzerUrl", "http://localhost:8000/analyze");
        ReflectionTestUtils.setField(presidioService, "anonymizerUrl", "http://localhost:8000/anonymize");
        ReflectionTestUtils.setField(presidioService, "restTemplate", restTemplate);
    }

    @Test
    void sanitizeText_masksNameAndEmail() {
        String input = "Contact John at john@example.com";
        String expected = "Contact [PERSON] at [EMAIL_ADDRESS]";
        stubAnonymizeResponse(expected);

        assertEquals(expected, presidioService.sanitizeText(input));
    }

    @Test
    void sanitizeText_masksPhoneNumber() {
        String input = "Call me at 9876543210 for CRS900 help";
        String expected = "Call me at [PHONE_NUMBER] for CRS900 help";
        stubAnonymizeResponse(expected);

        assertEquals(expected, presidioService.sanitizeText(input));
    }

    @Test
    void sanitizeText_leavesNonPiiUnchanged() {
        String input = "How to configure purchase settings in CRS900";
        stubAnonymizeResponse(input);

        assertEquals(input, presidioService.sanitizeText(input));
    }

    @Test
    void sanitizeTextSafe_returnsOriginalOnPresidioFailure() {
        String input = "How to configure CRS780";
        when(restTemplate.postForEntity(
                eq("http://localhost:8000/anonymize"),
                any(),
                eq(Map.class)
        )).thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));

        assertEquals(input, presidioService.sanitizeTextSafe(input));
    }

    private void stubAnonymizeResponse(String sanitizedText) {
        when(restTemplate.postForEntity(
                eq("http://localhost:8000/anonymize"),
                any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "originalText", "ignored",
                "sanitizedText", sanitizedText
        )));
    }
}
