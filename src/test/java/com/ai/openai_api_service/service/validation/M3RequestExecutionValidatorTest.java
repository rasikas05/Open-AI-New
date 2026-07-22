package com.ai.openai_api_service.service.validation;

import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.LexIntentMapper;
import com.ai.openai_api_service.service.SearchFieldCatalog;
import com.ai.openai_api_service.service.normalizer.FieldDefinitionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3RequestExecutionValidatorTest {

    private M3RequestExecutionValidator validator;
    private IntentDefinition searchPurchaseOrder;

    @BeforeEach
    void setUp() {
        SearchFieldCatalog catalog = new SearchFieldCatalog();
        FieldDefinitionRegistry registry = new FieldDefinitionRegistry();
        validator = new M3RequestExecutionValidator(catalog, registry);
        searchPurchaseOrder = new IntentApiCatalog()
                .find("SearchPurchaseOrder")
                .orElseThrow();
    }

    @Test
    void isExecutable_emptyParams_returnsFalse() {
        LexIntentMapper.MappedM3Request mapped = new LexIntentMapper.MappedM3Request(
                "PPS200MI",
                "SearchHead",
                Map.of(),
                "search"
        );
        assertFalse(validator.isExecutable(searchPurchaseOrder, mapped));
    }

    @Test
    void isExecutable_missingSqry_returnsFalse() {
        LexIntentMapper.MappedM3Request mapped = new LexIntentMapper.MappedM3Request(
                "PPS200MI",
                "SearchHead",
                Map.of("maxrecs", 5),
                "search"
        );
        assertFalse(validator.isExecutable(searchPurchaseOrder, mapped));
    }

    @Test
    void isExecutable_malformedExampleSqry_returnsFalse() {
        LexIntentMapper.MappedM3Request mapped = new LexIntentMapper.MappedM3Request(
                "PPS200MI",
                "SearchHead",
                Map.of("SQRY", "WHLO:AND AND SUNO:WAREHOUSE AND BUYE:BUYER AND PUNO:11"),
                "search"
        );
        assertFalse(validator.isExecutable(searchPurchaseOrder, mapped));
    }

    @Test
    void isExecutable_unknownFieldInSqry_returnsFalse() {
        LexIntentMapper.MappedM3Request mapped = new LexIntentMapper.MappedM3Request(
                "PPS200MI",
                "SearchHead",
                Map.of("SQRY", "XXXX:ABC123"),
                "search"
        );
        assertFalse(validator.isExecutable(searchPurchaseOrder, mapped));
    }

    @Test
    void isExecutable_validPurchaseOrderSqry_returnsTrue() {
        LexIntentMapper.MappedM3Request mapped = new LexIntentMapper.MappedM3Request(
                "PPS200MI",
                "SearchHead",
                Map.of("SQRY", "SUNO:S00001 AND WHLO:A01 AND PUST:33 AND PUDT:20260424"),
                "search"
        );
        assertTrue(validator.isExecutable(searchPurchaseOrder, mapped));
    }

    @Test
    void parseSqryClauses_splitsAndTrims() {
        List<M3RequestExecutionValidator.SqryClause> clauses =
                M3RequestExecutionValidator.parseSqryClauses("CUNO:Y11100 AND ORST:33");
        assertTrue(clauses.size() >= 2);
    }

    @Test
    void isExecutable_readIntent_skipsValidation() {
        IntentDefinition read = new IntentApiCatalog().find("GetCustomer").orElseThrow();
        LexIntentMapper.MappedM3Request mapped = new LexIntentMapper.MappedM3Request(
                "CRS610MI",
                "GetBasicData",
                Map.of("CUNO", "107685"),
                "read"
        );
        assertTrue(validator.isExecutable(read, mapped));
    }

    @Test
    void isExecutable_nullMappedSearch_returnsTrueForGuard() {
        assertTrue(validator.isExecutable(searchPurchaseOrder, null));
    }

    @Test
    void isExecutable_nonSearchDefinition_returnsTrue() {
        IntentDefinition read = new IntentDefinition(
                "GetCustomer",
                "CRS610MI",
                "GetBasicData",
                RequestType.READ,
                "CUNO"
        );
        assertTrue(validator.isExecutable(read, new LexIntentMapper.MappedM3Request(
                "CRS610MI",
                "GetBasicData",
                Map.of(),
                "read"
        )));
    }
}
