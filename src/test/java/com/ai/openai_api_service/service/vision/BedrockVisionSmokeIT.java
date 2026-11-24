package com.ai.openai_api_service.service.vision;

import com.ai.openai_api_service.config.RestTemplateFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Opt-in live Bedrock vision smoke. Skips when {@code BEDROCK_API_KEY} is unset.
 * Do not run during local implementation unless UAT credentials are intentionally provided.
 * Never logs the API key, Authorization header, or Base64 image payload.
 */
@EnabledIfEnvironmentVariable(named = "BEDROCK_API_KEY", matches = ".+")
class BedrockVisionSmokeIT {

    private static final Logger log = LoggerFactory.getLogger(BedrockVisionSmokeIT.class);

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void liveVision_readsSmokePhraseFromSyntheticPng() throws Exception {
        String apiKey = System.getenv("BEDROCK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            fail("BEDROCK_API_KEY required for live IT");
        }

        Properties props = loadApplicationProperties();
        String model = firstNonBlank(
                System.getenv("BEDROCK_RUNTIME_MODEL"),
                props.getProperty("bedrock.runtime.model"),
                BedrockVisionRequestSupport.DEFAULT_MODEL
        );
        String url = firstNonBlank(
                System.getenv("BEDROCK_RUNTIME_URL"),
                props.getProperty("bedrock.runtime.url"),
                BedrockVisionRequestSupport.DEFAULT_URL
        );
        int timeoutMs = parseTimeout(
                props.getProperty("bedrock.runtime.timeout-ms"),
                120_000
        );

        byte[] png = BedrockVisionRequestSupport.generateSmokePngBytes();
        String dataUrl = BedrockVisionRequestSupport.toPngDataUrl(png);
        Map<String, Object> body = BedrockVisionRequestSupport.buildMultimodalBody(
                model,
                dataUrl,
                BedrockVisionRequestSupport.DEFAULT_PROMPT
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        RestTemplate restTemplate = RestTemplateFactory.create(timeoutMs);
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> response = responseEntity.getBody();
            String content = BedrockVisionRequestSupport.extractAssistantContent(response);
            int contentLen = content != null ? content.length() : 0;

            log.info(
                    "Bedrock vision smoke OK | status={} model={} elapsedMs={} contentChars={}",
                    responseEntity.getStatusCode().value(),
                    model,
                    elapsed,
                    contentLen
            );

            assertTrue(
                    BedrockVisionRequestSupport.responseMentionsSmokePhrase(content),
                    "Assistant content should mention M3-VISION-SMOKE (allowing punctuation/spacing differences)"
            );
        } catch (HttpClientErrorException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error(
                    "Bedrock vision smoke HTTP error | status={} elapsedMs={} statusText={}",
                    e.getStatusCode().value(),
                    elapsed,
                    e.getStatusText()
            );
            throw e;
        } catch (ResourceAccessException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error(
                    "Bedrock vision smoke transport error | elapsedMs={} message={}",
                    elapsed,
                    e.getMessage()
            );
            throw e;
        }
    }

    private static Properties loadApplicationProperties() throws Exception {
        Properties props = new Properties();
        try (InputStream in = BedrockVisionSmokeIT.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        }
        return props;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                // Resolve simple ${ENV:default} placeholders from properties.
                String resolved = resolvePlaceholder(v.trim());
                if (resolved != null && !resolved.isBlank()) {
                    return resolved;
                }
            }
        }
        return null;
    }

    private static String resolvePlaceholder(String raw) {
        if (!raw.startsWith("${") || !raw.endsWith("}")) {
            return raw;
        }
        String inner = raw.substring(2, raw.length() - 1);
        int colon = inner.indexOf(':');
        if (colon < 0) {
            String env = System.getenv(inner);
            return env != null && !env.isBlank() ? env : null;
        }
        String envName = inner.substring(0, colon);
        String defaultVal = inner.substring(colon + 1);
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return defaultVal.isBlank() ? null : defaultVal;
    }

    private static int parseTimeout(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
