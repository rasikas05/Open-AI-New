package com.ai.openai_api_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.lexruntimev2.LexRuntimeV2Client;

@Configuration
public class AwsConfig {

    private static final Logger log = LoggerFactory.getLogger(AwsConfig.class);

    @Value("${aws.region:ap-south-1}")
    private String awsRegion;

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        AwsCredentialsProvider provider = DefaultCredentialsProvider.create();
        log.info("AWS credential provider in use: {}", provider.getClass().getName());
        log.info("AWS_ACCESS_KEY_ID present={}", System.getenv("AWS_ACCESS_KEY_ID") != null);
        log.info("AWS_SECRET_ACCESS_KEY present={}", System.getenv("AWS_SECRET_ACCESS_KEY") != null);
        log.info("AWS_SESSION_TOKEN present={}", System.getenv("AWS_SESSION_TOKEN") != null);
        log.info("AWS_PROFILE present={}", System.getenv("AWS_PROFILE") != null);
        log.info("AWS_DEFAULT_PROFILE present={}", System.getenv("AWS_DEFAULT_PROFILE") != null);
        return provider;
    }

    @Bean
    public ComprehendClient comprehendClient(AwsCredentialsProvider awsCredentialsProvider) {
        return ComprehendClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }

    @Bean
    public LexRuntimeV2Client lexRuntimeV2Client(
            AwsCredentialsProvider awsCredentialsProvider,
            LexProperties lexProperties
    ) {
        String lexRegion = lexProperties.getRegion();
        if (lexRegion == null || lexRegion.isBlank()) {
            lexRegion = awsRegion;
        }
        log.info(
                "Lex Runtime V2 client: region={}, enabled={}, botId={}, botAliasId={}, locale={}",
                lexRegion,
                lexProperties.isEnabled(),
                maskId(lexProperties.getBotId()),
                maskId(lexProperties.getBotAliasId()),
                lexProperties.getLocaleId()
        );
        return LexRuntimeV2Client.builder()
                .region(Region.of(lexRegion))
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }

    private static String maskId(String value) {
        if (value == null || value.isBlank()) {
            return "(not set)";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 4) + "****";
    }
}