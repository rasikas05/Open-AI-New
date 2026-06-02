package com.ai.openai_api_service.m3.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(M3Properties.class)
public class M3IntegrationConfig {

    @Bean
    public RestTemplate m3RestTemplate() {
        return new RestTemplate();
    }
}
