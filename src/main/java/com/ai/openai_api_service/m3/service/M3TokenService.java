package com.ai.openai_api_service.m3.service;

import com.ai.openai_api_service.m3.config.M3Properties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Service
public class M3TokenService {

    private static final Logger log = LoggerFactory.getLogger(M3TokenService.class);

    private final RestTemplate restTemplate;
    private final M3Properties properties;

    private String cachedToken;
    private Instant tokenExpiresAt;

    public M3TokenService(RestTemplate m3RestTemplate, M3Properties properties) {
        this.restTemplate = m3RestTemplate;
        this.properties = properties;
    }

    public synchronized String getToken() {
        Instant now = Instant.now();
        if (cachedToken != null && tokenExpiresAt != null) {
            if (now.plusSeconds(properties.getTokenCacheBufferSeconds()).isBefore(tokenExpiresAt)) {
                log.debug("M3 token cache hit");
                return cachedToken;
            }
        }
        return refreshToken();
    }

    private String refreshToken() {
        log.info("Refreshing M3 access token. m3.base-url={}, m3.token-url={}, m3.client-id={}", properties.getBaseUrl(), properties.getTokenUrl(), properties.getClientId());

        // Validate token URL
        if (properties.getTokenUrl() == null || properties.getTokenUrl().isBlank()) {
            log.error("M3 token URL is not configured or blank: '{}'", properties.getTokenUrl());
            throw new IllegalArgumentException("M3 token URL is not configured or is not absolute: '" + properties.getTokenUrl() + "'");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("username", properties.getUsername());
        body.add("password", properties.getPassword());
        body.add("scope", properties.getScope());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(properties.getTokenUrl(), request, TokenResponse.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null || response.getBody().getAccessToken() == null) {
            String errorBody = response.hasBody() ? response.getBody().toString() : "unknown body";
            throw new IllegalStateException("Failed to obtain M3 token: " + response.getStatusCode() + " " + errorBody);
        }

        TokenResponse tokenResponse = response.getBody();
        cachedToken = tokenResponse.getAccessToken();
        int expiresIn = tokenResponse.getExpiresIn() != null ? tokenResponse.getExpiresIn() : properties.getTokenTimeoutSeconds();
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        log.info("M3 access token acquired, expires in {} seconds", expiresIn);
        return cachedToken;
    }

    public static class TokenResponse {

        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("expires_in")
        private Integer expiresIn;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public Integer getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(Integer expiresIn) {
            this.expiresIn = expiresIn;
        }

        @Override
        public String toString() {
            return "TokenResponse{" +
                    "accessToken='" + accessToken + '\'' +
                    ", tokenType='" + tokenType + '\'' +
                    ", expiresIn=" + expiresIn +
                    '}';
        }
    }
}
