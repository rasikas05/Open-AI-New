package com.ai.openai_api_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lex")
public class LexProperties {

    private boolean enabled = false;
    private String region = "eu-central-1";
    private String botId = "";
    private String botAliasId = "";
    private String localeId = "en_US";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region != null ? region.trim() : null;
    }

    public String getBotId() {
        return botId;
    }

    public void setBotId(String botId) {
        this.botId = botId != null ? botId.trim() : null;
    }

    public String getBotAliasId() {
        return botAliasId;
    }

    public void setBotAliasId(String botAliasId) {
        this.botAliasId = botAliasId != null ? botAliasId.trim() : null;
    }

    public String getLocaleId() {
        return localeId;
    }

    public void setLocaleId(String localeId) {
        this.localeId = localeId != null ? localeId.trim() : null;
    }
}
