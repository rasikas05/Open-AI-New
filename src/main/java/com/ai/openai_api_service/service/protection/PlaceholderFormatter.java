package com.ai.openai_api_service.service.protection;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Stateless: Placeholder Type + FormattingConfig → token string.
 */
@Component
public class PlaceholderFormatter {

    private final FormattingConfig config;

    public PlaceholderFormatter() {
        this(FormattingConfig.defaults());
    }

    public PlaceholderFormatter(FormattingConfig config) {
        this.config = config != null ? config : FormattingConfig.defaults();
    }

    public String format(String placeholderType) {
        if (placeholderType == null || placeholderType.isBlank()) {
            return config.prefix() + "REDACTED" + config.suffix();
        }
        String token = placeholderType.trim().replaceAll("\\s+", "_");
        if (config.uppercase()) {
            token = token.toUpperCase(Locale.ROOT);
        }
        return config.prefix() + token + config.suffix();
    }
}
