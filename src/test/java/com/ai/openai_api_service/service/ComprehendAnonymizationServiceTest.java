package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.PiiEntityDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComprehendAnonymizationServiceTest {

    @Mock
    private ComprehendService comprehendService;

    @Mock
    private PresidioService presidioService;

    @InjectMocks
    private ComprehendAnonymizationService service;

    @Test
    void comprehendFailure_fallsBackToPresidioSafe() {
        String originalText = "how to configure purchase settings";
        when(comprehendService.detectPii(originalText))
                .thenThrow(new RuntimeException("Comprehend IAM denied"));
        when(presidioService.sanitizeTextSafe(originalText))
                .thenReturn("how to configure purchase settings");

        ReflectionTestUtils.setField(service, "enabled", true);

        Map<String, Object> result = service.detectAndAnonymize(originalText);

        assertEquals("how to configure purchase settings", result.get("sanitizedText"));
        verify(presidioService).sanitizeTextSafe(originalText);
    }

    @Test
    void shouldFallbackMaskEmailAndInvoiceNumber() {
        String originalText = "Hi I am Rasika and i want to know about invoice number 5678934 how to get this email id is [Ras@gmail.com](mailto:Ras@gmail.com)";

        when(comprehendService.detectPii(originalText))
                .thenReturn(List.of(new PiiEntityDto("NAME", 8, 14, 0.99)));

        when(presidioService.sanitizeTextWithExternalResults(eq(originalText), anyList()))
                .thenReturn("Hi I am [Name] and i want to know about invoice number 5678934 how to get this email id is [Ras@gmail.com](mailto:Ras@gmail.com)");

        ReflectionTestUtils.setField(service, "enabled", true);

        Map<String, Object> result = service.detectAndAnonymize(originalText);

        assertEquals("Hi I am [Name] and i want to know about invoice number [NUMBER] how to get this email id is [EMAIL]",
                result.get("sanitizedText"));
    }
}
