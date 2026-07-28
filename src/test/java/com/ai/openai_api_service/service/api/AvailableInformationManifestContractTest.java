package com.ai.openai_api_service.service.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AvailableInformationManifestContractTest {

    private final ApiFieldCatalog apiFieldCatalog = new ApiFieldCatalog();
    private final InformationRequestCatalog informationRequestCatalog = new InformationRequestCatalog();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void manifestCodesExistInCatalogsWithExpectedColumns() throws Exception {
        for (Map<String, Object> row : loadManifest()) {
            String program = (String) row.get("program");
            String transaction = (String) row.get("transaction");
            String code = (String) row.get("code");
            @SuppressWarnings("unchecked")
            List<String> expectedColumns = (List<String>) row.get("columns");

            assertNotNull(
                    informationRequestCatalog.find(code),
                    "InformationRequestCatalog missing code: " + code
            );
            List<String> actual = apiFieldCatalog.columnsFor(M3ApiKey.of(program, transaction), code);
            assertFalse(actual.isEmpty(), "ApiFieldCatalog missing: " + program + "/" + transaction + " " + code);
            assertEquals(expectedColumns, actual);
        }
    }

    private List<Map<String, Object>> loadManifest() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/available-information-manifest.json")) {
            if (in == null) {
                throw new IllegalStateException("available-information-manifest.json not found");
            }
            return objectMapper.readValue(in, new TypeReference<>() {
            });
        }
    }
}
