package com.ai.openai_api_service.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationProfileConfigTest {

    @Test
    void prodProperties_useEnvVarsForDb_withoutRootFallback() throws IOException {
        Properties prod = loadClasspathProperties("/application-prod.properties");

        String prodUrl = prod.getProperty("spring.datasource.url");
        assertTrue(prodUrl.contains("${DB_HOST:10.0.130.125}"), "expected DB_HOST default for GFT DB, got: " + prodUrl);
        assertTrue(prodUrl.contains("m3bot_prod"), "expected prod database name m3bot_prod, got: " + prodUrl);
        assertEqualsEnvPlaceholder(prod.getProperty("spring.datasource.username"), "DB_USERNAME");
        assertEqualsEnvPlaceholder(prod.getProperty("spring.datasource.password"), "DB_PASSWORD");
        assertFalse(prodUrl.contains("createDatabaseIfNotExists"));
        assertEquals("false", prod.getProperty("spring.jpa.show-sql"));
        assertEquals("${PRESIDIO_API_KEY}", prod.getProperty("presidio.api.key"));
        assertEquals("${RAG_INTERNAL_API_KEY}", prod.getProperty("python-rag.api.key"));
        assertEquals("x-api-key", prod.getProperty("python-rag.api.key.header"));
        assertEquals("health,prometheus", prod.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("true", prod.getProperty("management.endpoint.health.probes.enabled"));
        assertEquals("true", prod.getProperty("management.endpoint.prometheus.enabled"));
        assertEquals("never", prod.getProperty("management.endpoint.health.show-details"));
        assertEquals("15000", prod.getProperty("aws.comprehend.api-call-timeout-ms"));
        assertEquals("10000", prod.getProperty("aws.comprehend.api-call-attempt-timeout-ms"));
        assertEquals("5000", prod.getProperty("aws.comprehend.connection-timeout-ms"));
        assertEquals("15000", prod.getProperty("aws.lex.api-call-timeout-ms"));
        assertEquals("10000", prod.getProperty("aws.lex.api-call-attempt-timeout-ms"));
        assertEquals("5000", prod.getProperty("aws.lex.connection-timeout-ms"));
    }

    @Test
    void defaultProperties_useEnvVarsForLocalDb_withoutRootFallback() throws IOException {
        Properties base = loadClasspathProperties("/application.properties");

        String baseUrl = base.getProperty("spring.datasource.url");
        assertTrue(baseUrl.contains("${DB_HOST:10.0.130.125}"), "expected DB_HOST default for GFT DB, got: " + baseUrl);
        assertTrue(baseUrl.contains("m3bot_prod"), "expected database name m3bot_prod, got: " + baseUrl);
        assertTrue(baseUrl.contains("createDatabaseIfNotExists=true"));
        assertEqualsEnvPlaceholder(base.getProperty("spring.datasource.username"), "DB_USERNAME");
        assertEqualsEnvPlaceholder(base.getProperty("spring.datasource.password"), "DB_PASSWORD");
        assertEquals("true", base.getProperty("spring.jpa.show-sql"));
        assertEquals("health,prometheus", base.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("true", base.getProperty("management.endpoint.health.probes.enabled"));
        assertEquals("true", base.getProperty("management.endpoint.prometheus.enabled"));
        assertEquals("never", base.getProperty("management.endpoint.health.show-details"));
        assertEquals("15000", base.getProperty("aws.comprehend.api-call-timeout-ms"));
        assertEquals("10000", base.getProperty("aws.comprehend.api-call-attempt-timeout-ms"));
        assertEquals("5000", base.getProperty("aws.comprehend.connection-timeout-ms"));
        assertEquals("15000", base.getProperty("aws.lex.api-call-timeout-ms"));
        assertEquals("10000", base.getProperty("aws.lex.api-call-attempt-timeout-ms"));
        assertEquals("5000", base.getProperty("aws.lex.connection-timeout-ms"));
    }

    @Test
    void defaultProperties_externalizePresidioKey_andOmitCognitoClientSecret() throws IOException {
        Properties base = loadClasspathProperties("/application.properties");

        String presidioKey = base.getProperty("presidio.api.key");
        assertNotNull(presidioKey);
        assertTrue(presidioKey.contains("${PRESIDIO_API_KEY"), "expected PRESIDIO_API_KEY placeholder, got: " + presidioKey);
        assertFalse(presidioKey.contains("secret123"), "must not hardcode Presidio key");

        String ragKey = base.getProperty("python-rag.api.key");
        assertNotNull(ragKey);
        assertTrue(ragKey.contains("${RAG_INTERNAL_API_KEY"), "expected RAG_INTERNAL_API_KEY placeholder, got: " + ragKey);
        assertEquals("x-api-key", base.getProperty("python-rag.api.key.header"));

        assertFalse(base.containsKey("aws.cognito.clientSecret"), "Cognito client secret must not be in Spring config");
        assertTrue(base.containsKey("aws.cognito.clientId"), "public client id may remain as reference");
    }

    @Test
    void prodProperties_keepInternalLocalhostUrls() throws IOException {
        Properties prod = loadClasspathProperties("/application-prod.properties");

        assertEquals("http://localhost:8083", prod.getProperty("python-rag.api.base-url"));
        assertEquals("http://localhost:8000/api/v1/analyze", prod.getProperty("presidio.analyzer.url"));
        assertEquals("http://localhost:8000/api/v1/anonymize", prod.getProperty("presidio.anonymizer.url"));
    }

    private static void assertEqualsEnvPlaceholder(String value, String envName) {
        assertNotNull(value);
        assertTrue(value.contains("${" + envName + "}"), "expected ${" + envName + "}, got: " + value);
        assertFalse(value.contains(":root"), "must not use root as fallback");
    }

    private static Properties loadClasspathProperties(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ApplicationProfileConfigTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing resource: " + resource);
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // Strip comment lines so Properties.load does not mis-parse '#' in URLs
            StringBuilder sb = new StringBuilder();
            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                sb.append(line).append('\n');
            }
            properties.load(new java.io.ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)));
        }
        return properties;
    }
}
