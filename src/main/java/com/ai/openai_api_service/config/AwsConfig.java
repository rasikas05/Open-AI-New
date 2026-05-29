package com.ai.openai_api_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;

@Configuration
public class AwsConfig {

    private static final Logger log = LoggerFactory.getLogger(AwsConfig.class);

    @Bean
    public ComprehendClient comprehendClient() {
        AwsCredentialsProvider provider = DefaultCredentialsProvider.create();
        log.info("AWS credential provider in use: {}", provider.getClass().getName());
        log.info("AWS_ACCESS_KEY_ID present={}", System.getenv("AWS_ACCESS_KEY_ID") != null);
        log.info("AWS_SECRET_ACCESS_KEY present={}", System.getenv("AWS_SECRET_ACCESS_KEY") != null);
        log.info("AWS_SESSION_TOKEN present={}", System.getenv("AWS_SESSION_TOKEN") != null);
        log.info("AWS_PROFILE present={}", System.getenv("AWS_PROFILE") != null);
        log.info("AWS_DEFAULT_PROFILE present={}", System.getenv("AWS_DEFAULT_PROFILE") != null);

        return ComprehendClient.builder()
                .region(Region.AP_SOUTH_1)
                .credentialsProvider(provider)
                .build();
    }
}