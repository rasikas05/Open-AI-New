package com.ai.openai_api_service.service.api;

import com.ai.openai_api_service.service.RequestedInformationResolver;

import java.util.List;

public final class SpecificInformationHelper {

    public static final String UNKNOWN_INFORMATION = "UNKNOWN_INFORMATION";

    private SpecificInformationHelper() {
    }

    public static boolean isSpecificInformationRequest(List<String> requestedInformation) {
        if (requestedInformation == null || requestedInformation.isEmpty()) {
            return false;
        }
        if (requestedInformation.size() == 1
                && RequestedInformationResolver.FULL.equals(requestedInformation.getFirst())) {
            return false;
        }
        return requestedInformation.stream().anyMatch(SpecificInformationHelper::isSpecificCode);
    }

    public static boolean containsUnknown(List<String> requestedInformation) {
        if (requestedInformation == null) {
            return false;
        }
        return requestedInformation.stream().anyMatch(UNKNOWN_INFORMATION::equalsIgnoreCase);
    }

    public static boolean useApiCapabilityReturncols(List<String> requestedInformation) {
        return isSpecificInformationRequest(requestedInformation);
    }

    private static boolean isSpecificCode(String code) {
        return code != null
                && !code.isBlank()
                && !RequestedInformationResolver.FULL.equals(code);
    }
}
