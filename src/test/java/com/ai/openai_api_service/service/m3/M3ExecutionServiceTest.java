package com.ai.openai_api_service.service.m3;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.M3RequestDto;
import com.ai.openai_api_service.model.python_rag.M3MiCallResponse;
import com.ai.openai_api_service.service.PythonRagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class M3ExecutionServiceTest {

    @Mock
    private PythonRagService pythonRagService;

    @Mock
    private MiResponseParser miResponseParser;

    private M3ExecutionService m3ExecutionService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        m3ExecutionService = new M3ExecutionService(pythonRagService, miResponseParser, "100", 50);
    }

    @Test
    void execute_notRequested_returnsFailure() {
        M3RequestDto dto = new M3RequestDto(false, "OIS100MI", "SearchHead", Map.of());

        M3MiExecutionResult result = m3ExecutionService.execute(dto);

        assertFalse(result.success());
        assertEquals("M3 execution is not requested", result.errorMessage());
    }

    @Test
    void execute_success_delegatesToPythonAndParser() {
        M3RequestDto dto = new M3RequestDto(
                true,
                "OIS100MI",
                "SearchHead",
                Map.of("SQRY", "ORST:20")
        );
        M3MiCallResponse callResponse = new M3MiCallResponse(
                "OIS100MI",
                "SearchHead",
                null,
                null,
                List.of(Map.of("ORNO", "001")),
                null
        );
        M3MiExecutionResult parsed = new M3MiExecutionResult(
                true, "OIS100MI", "SearchHead", 1, List.of("ORNO"),
                List.of(Map.of("ORNO", "001")), null
        );

        when(pythonRagService.executeMi(eq(dto), eq("100"), eq(50))).thenReturn(callResponse);
        when(miResponseParser.parse(callResponse)).thenReturn(parsed);

        M3MiExecutionResult result = m3ExecutionService.execute(dto);

        assertTrue(result.success());
        assertEquals(1, result.recordCount());
        verify(pythonRagService).executeMi(eq(dto), eq("100"), eq(50));
        verify(miResponseParser).parse(callResponse);
    }

    @Test
    void execute_pythonFailure_returnsFailureWithoutThrowing() {
        M3RequestDto dto = new M3RequestDto(
                true,
                "PPS200MI",
                "SearchHead",
                Map.of("SQRY", "PUST:33")
        );
        when(pythonRagService.executeMi(any(), any(), anyInt()))
                .thenThrow(new OpenAIException("Python RAG API timeout", 504));

        M3MiExecutionResult result = m3ExecutionService.execute(dto);

        assertFalse(result.success());
        assertEquals("Python RAG API timeout", result.errorMessage());
    }
}
