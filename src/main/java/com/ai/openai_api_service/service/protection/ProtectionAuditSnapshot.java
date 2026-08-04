package com.ai.openai_api_service.service.protection;

/**
 * Optional persistence/API snapshot of protection audit fields (7B.3–7B.4).
 */
public record ProtectionAuditSnapshot(
        String businessProtectedText,
        String piiSanitizedText,
        String openaiResponseRaw,
        String finalResponse,
        boolean businessProtectionApplied,
        int businessEntitiesCount,
        String businessEntitiesJson
) {
    public static ProtectionAuditSnapshot fromSession(ProtectionSession session, String openaiResponseRaw, String finalResponse) {
        if (session == null) {
            return null;
        }
        int count = session.businessActions() != null ? session.businessActions().size() : 0;
        StringBuilder json = new StringBuilder("[");
        if (session.replacementMap() != null) {
            boolean first = true;
            for (String placeholder : session.replacementMap().keySet()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                String type = placeholder != null
                        ? placeholder.replace("<", "").replace(">", "")
                        : "";
                json.append("{\"type\":\"").append(type)
                        .append("\",\"placeholder\":\"").append(placeholder).append("\"}");
            }
        }
        json.append(']');
        return new ProtectionAuditSnapshot(
                session.businessProtectedText(),
                session.piiSanitizedText(),
                openaiResponseRaw,
                finalResponse,
                session.businessProtectionApplied(),
                count,
                json.toString()
        );
    }
}
