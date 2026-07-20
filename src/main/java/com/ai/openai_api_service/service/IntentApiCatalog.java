package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.RequestType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class IntentApiCatalog {

    private final Map<String, IntentDefinition> definitionsByIntent;

    public IntentApiCatalog() {
        Map<String, IntentDefinition> definitions = new LinkedHashMap<>();
        seed(definitions, new IntentDefinition(
                "GetCustomer",
                "CRS610MI",
                "GetBasicData",
                RequestType.READ,
                "CUNO"
        ));
        seed(definitions, new IntentDefinition(
                "GetCustomerFinancial",
                "CRS610MI",
                "GetFinancial",
                RequestType.READ,
                "CUNO"
        ));
        seed(definitions, new IntentDefinition(
                "SearchCustomerOrder",
                "OIS100MI",
                "SearchHead",
                RequestType.SEARCH,
                "SQRY"
        ));
        seed(definitions, new IntentDefinition(
                "SearchPurchaseOrder",
                "PPS200MI",
                "SearchHead",
                RequestType.SEARCH,
                "SQRY"
        ));
        seed(definitions, new IntentDefinition(
                "SearchManufacturingOrder",
                "PMS100MI",
                "SearchMO",
                RequestType.SEARCH,
                "SQRY"
        ));
        seed(definitions, new IntentDefinition(
                "SearchDistributionOrder",
                "MMS100MI",
                "SearchHead",
                RequestType.SEARCH,
                "SQRY"
        ));
        this.definitionsByIntent = Map.copyOf(definitions);
    }

    private static void seed(Map<String, IntentDefinition> definitions, IntentDefinition definition) {
        definitions.put(definition.intentName(), definition);
    }

    public Optional<IntentDefinition> find(String intentName) {
        if (intentName == null || intentName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitionsByIntent.get(intentName));
    }

    public boolean contains(String intentName) {
        return find(intentName).isPresent();
    }
}
