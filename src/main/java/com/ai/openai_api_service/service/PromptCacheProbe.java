package com.ai.openai_api_service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Observation-only prompt structure metrics for Bedrock cache investigation.
 * Never logs raw prompt or documentation text — hashes and lengths only.
 */
final class PromptCacheProbe {

    static final String RAG_MARKER = "RETRIEVED DOCUMENTATION:";
    private static final String HASH_SEP = "\n---\n";

    private PromptCacheProbe() {
    }

    record ProbeResult(
            String systemPromptHash,
            String historyHash,
            String ragContextHash,
            String fullPromptHash,
            String cumulativeSys,
            String cumulativeSysHist,
            String cumulativeSysHistQuestion,
            String cumulativeFull,
            int systemPromptChars,
            int systemPromptTokensEst,
            int prefixCharsBeforeRag,
            int prefixTokensBeforeRagEst
    ) {
    }

    static ProbeResult probe(List<? extends Map<String, ?>> messages) {
        if (messages == null || messages.isEmpty()) {
            return empty();
        }

        int systemIndex = indexOfRole(messages, "system");
        String systemSerialized = systemIndex >= 0 ? serializeOne(messages.get(systemIndex)) : "";
        String systemPromptHash = shortHash(systemSerialized);
        int systemPromptChars = systemSerialized.length();

        List<String> historyParts = new ArrayList<>();
        int lastIndex = messages.size() - 1;
        int historyStart = systemIndex >= 0 ? systemIndex + 1 : 0;
        for (int i = historyStart; i < lastIndex; i++) {
            historyParts.add(serializeOne(messages.get(i)));
        }
        String historySerialized = String.join("", historyParts);
        String historyHash = historyParts.isEmpty() ? "-" : shortHash(historySerialized);

        Map<String, ?> finalMsg = messages.get(lastIndex);
        String finalContent = contentAsString(finalMsg.get("content"));
        int ragMarkerIdx = indexOfIgnoreCase(finalContent, RAG_MARKER);
        String ragContextHash = "-";
        String questionOnlyContent;
        if (ragMarkerIdx >= 0) {
            String after = finalContent.substring(ragMarkerIdx + RAG_MARKER.length());
            ragContextHash = shortHash(after);
            questionOnlyContent = finalContent.substring(0, ragMarkerIdx);
        } else {
            questionOnlyContent = finalContent;
        }

        StringBuilder sysHist = new StringBuilder();
        if (!systemSerialized.isEmpty()) {
            sysHist.append(systemSerialized);
        }
        sysHist.append(historySerialized);

        String questionMsgSerialized = serializeRoleContent(
                roleOf(finalMsg),
                questionOnlyContent
        );
        String fullSerialized = serializeAll(messages);

        String cumulativeSys = systemSerialized.isEmpty() ? "-" : shortHash(systemSerialized);
        String cumulativeSysHist = sysHist.isEmpty() ? "-" : shortHash(sysHist.toString());
        String sysHistQuestionSerialized = sysHist + questionMsgSerialized;
        String cumulativeSysHistQuestion = sysHistQuestionSerialized.isEmpty()
                ? "-"
                : shortHash(sysHistQuestionSerialized);
        String fullPromptHash = fullSerialized.isEmpty() ? "-" : shortHash(fullSerialized);

        int prefixCharsBeforeRag = sysHistQuestionSerialized.length();

        return new ProbeResult(
                systemPromptHash.isEmpty() ? "-" : systemPromptHash,
                historyHash,
                ragContextHash,
                fullPromptHash,
                cumulativeSys,
                cumulativeSysHist,
                cumulativeSysHistQuestion,
                fullPromptHash,
                systemPromptChars,
                estimateTokens(systemPromptChars),
                prefixCharsBeforeRag,
                estimateTokens(prefixCharsBeforeRag)
        );
    }

    static boolean bodyHasExplicitCacheOptions(Map<String, Object> body) {
        if (body == null) {
            return false;
        }
        if (body.containsKey("prompt_cache_options") || body.containsKey("prompt_cache_key")) {
            return true;
        }
        return bodyContainsBreakpoint(body.get("messages"));
    }

    static int estimateTokens(int chars) {
        if (chars <= 0) {
            return 0;
        }
        return (chars + 3) / 4;
    }

    static String shortHash(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static ProbeResult empty() {
        return new ProbeResult("-", "-", "-", "-", "-", "-", "-", "-", 0, 0, 0, 0);
    }

    private static String serializeAll(List<? extends Map<String, ?>> messages) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, ?> message : messages) {
            sb.append(serializeOne(message));
        }
        return sb.toString();
    }

    private static String serializeOne(Map<String, ?> message) {
        return serializeRoleContent(roleOf(message), contentAsString(message.get("content")));
    }

    private static String serializeRoleContent(String role, String content) {
        return role + "\n" + content + HASH_SEP;
    }

    private static String roleOf(Map<String, ?> message) {
        Object role = message.get("role");
        return role == null ? "" : role.toString();
    }

    private static String contentAsString(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object partObj : parts) {
                if (partObj instanceof Map<?, ?> part) {
                    Object text = part.get("text");
                    if (text != null) {
                        if (!sb.isEmpty()) {
                            sb.append('\n');
                        }
                        sb.append(text);
                    }
                }
            }
            return sb.toString();
        }
        return String.valueOf(content);
    }

    private static int indexOfRole(List<? extends Map<String, ?>> messages, String role) {
        for (int i = 0; i < messages.size(); i++) {
            if (role.equalsIgnoreCase(roleOf(messages.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) {
            return -1;
        }
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    private static boolean bodyContainsBreakpoint(Object messagesObj) {
        if (!(messagesObj instanceof List<?> messages)) {
            return false;
        }
        for (Object messageObj : messages) {
            if (!(messageObj instanceof Map<?, ?> message)) {
                continue;
            }
            Object content = message.get("content");
            if (content instanceof List<?> parts) {
                for (Object partObj : parts) {
                    if (partObj instanceof Map<?, ?> part && part.containsKey("prompt_cache_breakpoint")) {
                        return true;
                    }
                }
            } else if (content instanceof Map<?, ?> part && part.containsKey("prompt_cache_breakpoint")) {
                return true;
            }
        }
        return false;
    }
}
