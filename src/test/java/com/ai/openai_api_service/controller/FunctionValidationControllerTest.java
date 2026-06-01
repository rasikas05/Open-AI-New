package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.model.FunctionDetailsResponse;
import com.ai.openai_api_service.model.FunctionValidationRequest;
import com.ai.openai_api_service.model.FunctionValidationResponse;
import com.ai.openai_api_service.service.FunctionValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class FunctionValidationControllerTest {

    @Mock
    private FunctionValidationService validationService;

    @InjectMocks
    private FunctionValidationController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testValidateFunctionIds_Success() throws Exception {
        // Arrange
        FunctionValidationRequest request = new FunctionValidationRequest(
                Arrays.asList("MMS001", "MMS200", "OIS100", "INVALID001")
        );

        FunctionDetailsResponse fd1 = new FunctionDetailsResponse("MMS001", "Item. Open", "WRK", "M3");
        FunctionDetailsResponse fd2 = new FunctionDetailsResponse("MMS200", "Item. Open Toolbox", "WRK", "M3");
        FunctionDetailsResponse fd3 = new FunctionDetailsResponse("OIS100", "Customer Order. Open", "WRK", "M3");

        FunctionValidationResponse response = new FunctionValidationResponse(
                4,
                3,
                Arrays.asList(fd1, fd2, fd3),
                List.of("INVALID001")
        );

        when(validationService.validateFunctionIds(anyList())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/functions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested").value(4))
                .andExpect(jsonPath("$.totalFound").value(3))
                .andExpect(jsonPath("$.validPrograms.length()").value(3))
                .andExpect(jsonPath("$.invalidPrograms.length()").value(1))
                .andExpect(jsonPath("$.invalidPrograms[0]").value("INVALID001"));
    }

    @Test
    void testValidateFunctionIds_WithNullRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/functions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void testValidateFunctionIds_AllValid() throws Exception {
        // Arrange
        FunctionValidationRequest request = new FunctionValidationRequest(
                Arrays.asList("MMS001", "MMS200")
        );

        FunctionDetailsResponse fd1 = new FunctionDetailsResponse("MMS001", "Item. Open", "WRK", "M3");
        FunctionDetailsResponse fd2 = new FunctionDetailsResponse("MMS200", "Item. Open Toolbox", "WRK", "M3");

        FunctionValidationResponse response = new FunctionValidationResponse(
                2,
                2,
                Arrays.asList(fd1, fd2),
                List.of()
        );

        when(validationService.validateFunctionIds(anyList())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/functions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested").value(2))
                .andExpect(jsonPath("$.totalFound").value(2))
                .andExpect(jsonPath("$.validPrograms.length()").value(2))
                .andExpect(jsonPath("$.invalidPrograms.length()").value(0));
    }

    @Test
    void testValidateFunctionIds_AllInvalid() throws Exception {
        // Arrange
        FunctionValidationRequest request = new FunctionValidationRequest(
                Arrays.asList("INVALID001", "INVALID002")
        );

        FunctionValidationResponse response = new FunctionValidationResponse(
                2,
                0,
                List.of(),
                Arrays.asList("INVALID001", "INVALID002")
        );

        when(validationService.validateFunctionIds(anyList())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/functions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested").value(2))
                .andExpect(jsonPath("$.totalFound").value(0))
                .andExpect(jsonPath("$.validPrograms.length()").value(0))
                .andExpect(jsonPath("$.invalidPrograms.length()").value(2));
    }

    @Test
    void testValidateFunctionIds_EmptyList() throws Exception {
        // Arrange
        FunctionValidationRequest request = new FunctionValidationRequest(List.of());

        FunctionValidationResponse response = new FunctionValidationResponse(0, 0, List.of(), List.of());

        when(validationService.validateFunctionIds(anyList())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/functions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested").value(0))
                .andExpect(jsonPath("$.totalFound").value(0));
    }

    @Test
    void testValidateFunctionIds_ResponseStructure() throws Exception {
        // Arrange
        FunctionValidationRequest request = new FunctionValidationRequest(List.of("MMS001"));

        FunctionDetailsResponse fd = new FunctionDetailsResponse("MMS001", "Item. Open", "WRK", "M3");

        FunctionValidationResponse response = new FunctionValidationResponse(
                1,
                1,
                List.of(fd),
                List.of()
        );

        when(validationService.validateFunctionIds(anyList())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/functions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validPrograms[0].fnid").value("MMS001"))
                .andExpect(jsonPath("$.validPrograms[0].description").value("Item. Open"))
                .andExpect(jsonPath("$.validPrograms[0].category").value("WRK"))
                .andExpect(jsonPath("$.validPrograms[0].mnid").value("M3"));
    }
}
