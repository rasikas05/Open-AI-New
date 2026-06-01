package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.FunctionMaster;
import com.ai.openai_api_service.model.FunctionDetailsResponse;
import com.ai.openai_api_service.model.FunctionValidationResponse;
import com.ai.openai_api_service.repository.FunctionMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FunctionValidationServiceImplTest {

    @Mock
    private FunctionMasterRepository functionMasterRepository;

    @InjectMocks
    private FunctionValidationServiceImpl functionValidationService;

    private List<FunctionMaster> mockFunctionMasters;

    @BeforeEach
    void setUp() {
        // Create mock FunctionMaster objects
        FunctionMaster fm1 = FunctionMaster.builder()
                .id(1L)
                .fnid("MMS001")
                .tx40("Item. Open")
                .fnt3("WRK")
                .mnid("M3")
                .build();

        FunctionMaster fm2 = FunctionMaster.builder()
                .id(2L)
                .fnid("MMS200")
                .tx40("Item. Open Toolbox")
                .fnt3("WRK")
                .mnid("M3")
                .build();

        FunctionMaster fm3 = FunctionMaster.builder()
                .id(3L)
                .fnid("OIS100")
                .tx40("Customer Order. Open")
                .fnt3("WRK")
                .mnid("M3")
                .build();

        mockFunctionMasters = Arrays.asList(fm1, fm2, fm3);
    }

    @Test
    void testValidateFunctionIds_WithValidAndInvalidIds() {
        // Arrange
        List<String> requestedIds = Arrays.asList("MMS001", "MMS200", "OIS100", "INVALID001");
        when(functionMasterRepository.findByFnidIn(anyList()))
                .thenReturn(mockFunctionMasters);

        // Act
        FunctionValidationResponse response = functionValidationService.validateFunctionIds(requestedIds);

        // Assert
        assertEquals(4, response.getTotalRequested());
        assertEquals(3, response.getTotalFound());
        assertEquals(3, response.getValidPrograms().size());
        assertEquals(1, response.getInvalidPrograms().size());
        assertTrue(response.getInvalidPrograms().contains("INVALID001"));

        // Verify valid program details
        FunctionDetailsResponse first = response.getValidPrograms().get(0);
        assertEquals("MMS001", first.getFnid());
        assertEquals("Item. Open", first.getDescription());
        assertEquals("WRK", first.getCategory());
        assertEquals("M3", first.getMnid());
    }

    @Test
    void testValidateFunctionIds_AllValid() {
        // Arrange
        List<String> requestedIds = Arrays.asList("MMS001", "MMS200");
        when(functionMasterRepository.findByFnidIn(anyList()))
                .thenReturn(mockFunctionMasters.subList(0, 2));

        // Act
        FunctionValidationResponse response = functionValidationService.validateFunctionIds(requestedIds);

        // Assert
        assertEquals(2, response.getTotalRequested());
        assertEquals(2, response.getTotalFound());
        assertEquals(2, response.getValidPrograms().size());
        assertEquals(0, response.getInvalidPrograms().size());
    }

    @Test
    void testValidateFunctionIds_AllInvalid() {
        // Arrange
        List<String> requestedIds = Arrays.asList("INVALID001", "INVALID002");
        when(functionMasterRepository.findByFnidIn(anyList()))
                .thenReturn(List.of());

        // Act
        FunctionValidationResponse response = functionValidationService.validateFunctionIds(requestedIds);

        // Assert
        assertEquals(2, response.getTotalRequested());
        assertEquals(0, response.getTotalFound());
        assertEquals(0, response.getValidPrograms().size());
        assertEquals(2, response.getInvalidPrograms().size());
        assertTrue(response.getInvalidPrograms().containsAll(requestedIds));
    }

    @Test
    void testValidateFunctionIds_EmptyList() {
        // Arrange
        List<String> requestedIds = List.of();

        // Act
        FunctionValidationResponse response = functionValidationService.validateFunctionIds(requestedIds);

        // Assert
        assertEquals(0, response.getTotalRequested());
        assertEquals(0, response.getTotalFound());
        assertEquals(0, response.getValidPrograms().size());
        assertEquals(0, response.getInvalidPrograms().size());
    }

    @Test
    void testValidateFunctionIds_NullList() {
        // Act
        FunctionValidationResponse response = functionValidationService.validateFunctionIds(null);

        // Assert
        assertEquals(0, response.getTotalRequested());
        assertEquals(0, response.getTotalFound());
        assertEquals(0, response.getValidPrograms().size());
        assertEquals(0, response.getInvalidPrograms().size());
    }

    @Test
    void testValidateFunctionIds_WithNullValues() {
        // Arrange
        List<String> requestedIds = Arrays.asList("MMS001", null, "MMS200");
        when(functionMasterRepository.findByFnidIn(anyList()))
                .thenReturn(mockFunctionMasters.subList(0, 2));

        // Act
        FunctionValidationResponse response = functionValidationService.validateFunctionIds(requestedIds);

        // Assert
        assertEquals(3, response.getTotalRequested());
        assertEquals(2, response.getTotalFound());
        assertEquals(2, response.getValidPrograms().size());
        assertTrue(response.getInvalidPrograms().contains(null));
    }

    @Test
    void testValidateFunctionIds_WithBlankValues() {
        // Arrange
        List<String> requestedIds = Arrays.asList("MMS001", "", "MMS200");
        when(functionMasterRepository.findByFnidIn(anyList()))
                .thenReturn(mockFunctionMasters.subList(0, 2));

        // Act
        FunctionValidationResponse response = functionValidationService.validateFunctionIds(requestedIds);

        // Assert
        assertEquals(3, response.getTotalRequested());
        assertEquals(2, response.getTotalFound());
        assertEquals(2, response.getValidPrograms().size());
        assertTrue(response.getInvalidPrograms().contains(""));
    }

    @Test
    void testValidateFunctionIds_WithDuplicates() {
        // Arrange
        List<String> requestedIds = Arrays.asList("MMS001", "MMS001", "MMS200");
        when(functionMasterRepository.findByFnidIn(anyList()))
                .thenReturn(mockFunctionMasters.subList(0, 2));

        // Act
        FunctionValidationResponse response = functionValidationService.validateFunctionIds(requestedIds);

        // Assert
        assertEquals(3, response.getTotalRequested());
        assertEquals(2, response.getTotalFound());
        assertEquals(2, response.getValidPrograms().size());  // Duplicates not repeated
        assertEquals(0, response.getInvalidPrograms().size());
    }
}
