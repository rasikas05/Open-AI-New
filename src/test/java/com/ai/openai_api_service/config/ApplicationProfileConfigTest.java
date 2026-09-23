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

        assertTrue(prod.getProperty("spring.datasource.url").contains("${DB_HOST:localhost}"));
        assertEqualsEnvPlaceholder(prod.getProperty("spring.datasource.username"), "DB_USERNAME");
        assertEqualsEnvPlaceholder(prod.getProperty("spring.datasource.password"), "DB_PASSWORD");
        assertFalse(prod.getProperty("spring.datasource.url").contains("createDatabaseIfNotExists"));
        assertEquals("false", prod.getProperty("spring.jpa.show-sql"));
        assertEquals("${PRESIDIO_API_KEY}", prod.getProperty("presidio.api.key"));
    }

    @Test
    void defaultProperties_useEnvVarsForLocalDb_withoutRootFallback() throws IOException {
        Properties base = loadClasspathProperties("/application.properties");

        assertTrue(base.getProperty("spring.datasource.url").contains("${DB_HOST:localhost}"));
        assertTrue(base.getProperty("spring.datasource.url").contains("createDatabaseIfNotExists=true"));
        assertEqualsEnvPlaceholder(base.getProperty("spring.datasource.username"), "DB_USERNAME");
        assertEqualsEnvPlaceholder(base.getProperty("spring.datasource.password"), "DB_PASSWORD");
        assertEquals("true", base.getProperty("spring.jpa.show-sql"));
    }

    @Test
    void defaultProperties_externalizePresidioKey_andOmitCognitoClientSecret() throws IOException {
        Properties base = loadClasspathProperties("/application.properties");

        String presidioKey = base.getProperty("presidio.api.key");
        assertNotNull(presidioKey);
        assertTrue(presidioKey.contains("${PRESIDIO_API_KEY"), "expected PRESIDIO_API_KEY placeholder, got: " + presidioKey);
        assertFalse(presidioKey.contains("secret123"), "must not hardcode Presidio key");
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
