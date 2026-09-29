package com.ai.openai_api_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.lexruntimev2.LexRuntimeV2Client;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:mysql://localhost:3306/openaichatprocessdb?createDatabaseIfNotExists=true",
        "spring.datasource.username=root",
        "spring.datasource.password=root",
        "presidio.enabled=false",
        "comprehend.anonymization.enabled=false",
        "lex.enabled=false",
        "python-rag.api.enabled=false",
        "aws.comprehend.api-call-timeout-ms=15000",
        "aws.comprehend.api-call-attempt-timeout-ms=10000",
        "aws.comprehend.connection-timeout-ms=5000",
        "aws.lex.api-call-timeout-ms=15000",
        "aws.lex.api-call-attempt-timeout-ms=10000",
        "aws.lex.connection-timeout-ms=5000"
})
class AwsClientTimeoutConfigTest {

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private ComprehendClient comprehendClient;

    @Autowired
    private LexRuntimeV2Client lexRuntimeV2Client;

    @Test
    void comprehendClient_hasExplicitApiCallTimeouts() {
        ClientOverrideConfiguration override =
                comprehendClient.serviceClientConfiguration().overrideConfiguration();
        assertEquals(Duration.ofMillis(15000), override.apiCallTimeout().orElseThrow());
        assertEquals(Duration.ofMillis(10000), override.apiCallAttemptTimeout().orElseThrow());
    }

    @Test
    void lexClient_hasExplicitApiCallTimeouts() {
        ClientOverrideConfiguration override =
                lexRuntimeV2Client.serviceClientConfiguration().overrideConfiguration();
        assertEquals(Duration.ofMillis(15000), override.apiCallTimeout().orElseThrow());
        assertEquals(Duration.ofMillis(10000), override.apiCallAttemptTimeout().orElseThrow());
    }

    @Test
    void overrideConfiguration_rejectsAttemptNotStrictlyLessThanCall() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AwsConfig.overrideConfiguration(10000, 10000)
        );
        assertTrue(ex.getMessage().contains("strictly less"));
    }
}
