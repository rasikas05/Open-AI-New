package com.ai.openai_api_service.service.api;

import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.service.RequestedInformationResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
public class ApiCapabilityResolver {

    public static final String ACTION_INFORMATION_NOT_AVAILABLE = "information_not_available";
    public static final String ACTION_INFORMATION_UNKNOWN = "information_unknown";

    private final ApiFieldCatalog apiFieldCatalog;
    private final ApiCapabilityMessageBuilder messageBuilder;

    public ApiCapabilityResolver(ApiFieldCatalog apiFieldCatalog, ApiCapabilityMessageBuilder messageBuilder) {
        this.apiFieldCatalog = apiFieldCatalog;
        this.messageBuilder = messageBuilder;
    }

    public ApiCapabilityResult resolve(IntentDefinition definition, List<String> requestedInformation) {
        if (!SpecificInformationHelper.isSpecificInformationRequest(requestedInformation)) {
            return ApiCapabilityResult.fullExecute();
        }

        if (SpecificInformationHelper.containsUnknown(requestedInformation)) {
            return ApiCapabilityResult.blocked(
                    ApiCapabilityMessageBuilder.UNKNOWN_CLARIFICATION,
                    ACTION_INFORMATION_UNKNOWN
            );
        }

        M3ApiKey apiKey = M3ApiKey.of(definition.program(), definition.transaction());
        ApiFieldCatalog.ApiEntry apiEntry = apiFieldCatalog.entryFor(apiKey);
        String apiFriendlyName = apiEntry != null ? apiEntry.friendlyApiName() : definition.program();

        List<String> supportedCodes = new ArrayList<>();
        List<String> unsupportedCodes = new ArrayList<>();
        LinkedHashSet<String> columns = new LinkedHashSet<>();

        for (String code : requestedInformation) {
            if (code == null || code.isBlank() || RequestedInformationResolver.FULL.equals(code)) {
                continue;
            }
            List<String> mapped = apiFieldCatalog.columnsFor(apiKey, code);
            if (mapped.isEmpty()) {
                unsupportedCodes.add(code.toUpperCase(Locale.ROOT));
            } else {
                supportedCodes.add(code.toUpperCase(Locale.ROOT));
                columns.addAll(mapped);
            }
        }

        if (supportedCodes.isEmpty()) {
            List<String> alternates = messageBuilder.alternateApiNamesForUnsupported(
                    unsupportedCodes,
                    apiKey,
                    apiFieldCatalog
            );
            return ApiCapabilityResult.blocked(
                    messageBuilder.buildNoneSupportedMessage(apiFriendlyName, unsupportedCodes, alternates),
                    ACTION_INFORMATION_NOT_AVAILABLE
            );
        }

        if (!unsupportedCodes.isEmpty()) {
            List<String> alternates = messageBuilder.alternateApiNamesForUnsupported(
                    unsupportedCodes,
                    apiKey,
                    apiFieldCatalog
            );
            return ApiCapabilityResult.partialExecute(
                    columns,
                    supportedCodes,
                    unsupportedCodes,
                    messageBuilder.buildPartialMessage(apiFriendlyName, unsupportedCodes, alternates)
            );
        }

        return new ApiCapabilityResult(true, columns, List.copyOf(supportedCodes), List.of(), null, null);
    }
}
