package com.ai.openai_api_service.m3.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class M3StartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(M3StartupLogger.class);

    private final Environment env;
    private final M3Properties m3Properties;

    public M3StartupLogger(Environment env, M3Properties m3Properties) {
        this.env = env;
        this.m3Properties = m3Properties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (env instanceof ConfigurableEnvironment) {
            ConfigurableEnvironment ce = (ConfigurableEnvironment) env;
            String[] profiles = ce.getActiveProfiles();
            log.info("Active Spring profiles: {}", Arrays.toString(profiles));
            try {
                String envTokenUrl = ce.getProperty("m3.token-url");
                String envBaseUrl = ce.getProperty("m3.base-url");
                String envClientId = ce.getProperty("m3.client-id");
                log.info("Environment properties: m3.base-url={}, m3.token-url={}, m3.client-id={}", envBaseUrl, envTokenUrl, envClientId);
            } catch (Exception ex) {
                log.warn("Unable to read environment properties for m3.*", ex);
            }
        }

        log.info("M3Properties bean values: baseUrl={}, tokenUrl={}, clientId={}", m3Properties.getBaseUrl(), m3Properties.getTokenUrl(), m3Properties.getClientId());
    }
}
