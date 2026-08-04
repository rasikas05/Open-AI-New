package com.ai.openai_api_service.service.protection;

/**
 * Placeholder token formatting config. Semantic Placeholder Type stays in the catalog;
 * this only controls token syntax.
 */
public record FormattingConfig(String prefix, String suffix, boolean uppercase) {

    public static FormattingConfig defaults() {
        return new FormattingConfig("<", ">", true);
    }
}
