package com.ai.openai_api_service.service.api;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ApiCapabilityMessageBuilder {

    public static final String UNKNOWN_CLARIFICATION =
            "I couldn't determine which information you are requesting. Please rephrase or be more specific.";

    private final InformationRequestCatalog informationRequestCatalog;

    public ApiCapabilityMessageBuilder(InformationRequestCatalog informationRequestCatalog) {
        this.informationRequestCatalog = informationRequestCatalog;
    }

    public String buildNoneSupportedMessage(
            String apiFriendlyName,
            List<String> unsupportedCodes,
            List<String> alternateApiFriendlyNames
    ) {
        String names = formatDisplayNames(unsupportedCodes);
        StringBuilder sb = new StringBuilder();
        sb.append("The requested information (")
                .append(names)
                .append(") is not available from ")
                .append(apiFriendlyName)
                .append(".");
        appendAlternateApis(sb, alternateApiFriendlyNames);
        return sb.toString();
    }

    public String buildPartialMessage(
            String apiFriendlyName,
            List<String> unsupportedCodes,
            List<String> alternateApiFriendlyNames
    ) {
        String names = formatDisplayNames(unsupportedCodes);
        StringBuilder sb = new StringBuilder();
        sb.append("Some of the requested information is not available from ")
                .append(apiFriendlyName)
                .append(": ")
                .append(names)
                .append(".");
        appendAlternateApis(sb, alternateApiFriendlyNames);
        return sb.toString();
    }

    public List<String> alternateApiNamesForUnsupported(
            List<String> unsupportedCodes,
            M3ApiKey currentApi,
            ApiFieldCatalog catalog
    ) {
        Set<String> names = new LinkedHashSet<>();
        for (String code : unsupportedCodes) {
            for (String name : catalog.friendlyApiNamesForInformationCode(code)) {
                ApiFieldCatalog.ApiEntry currentEntry = catalog.entryFor(currentApi);
                if (currentEntry != null && name.equals(currentEntry.friendlyApiName())) {
                    continue;
                }
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private String formatDisplayNames(List<String> codes) {
        List<String> parts = new ArrayList<>();
        for (String code : codes) {
            parts.add(informationRequestCatalog.displayNameFor(code).toLowerCase(Locale.ROOT));
        }
        return String.join(", ", parts);
    }

    private static void appendAlternateApis(StringBuilder sb, List<String> alternateApiFriendlyNames) {
        if (alternateApiFriendlyNames == null || alternateApiFriendlyNames.isEmpty()) {
            return;
        }
        sb.append(" It may be available through: ")
                .append(String.join(", ", alternateApiFriendlyNames))
                .append(".");
    }
}
