package com.ai.openai_api_service.m3.service;

import com.ai.openai_api_service.m3.config.M3Properties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class M3ApiClient {

    private static final Logger log = LoggerFactory.getLogger(M3ApiClient.class);

    private final RestTemplate restTemplate;
    private final M3Properties properties;
    private final M3TokenService tokenService;

    public M3ApiClient(RestTemplate m3RestTemplate, M3Properties properties, M3TokenService tokenService) {
        this.restTemplate = m3RestTemplate;
        this.properties = properties;
        this.tokenService = tokenService;
    }

    public JsonNode callMi(String program, String transaction, Map<String, Object> record) {
        return callMi(program, transaction, record, Integer.parseInt(properties.getCompany()), 50);
    }

    public JsonNode callMi(String program, String transaction, Map<String, Object> record, int company, int maxReturnedRecords) {
        String token = tokenService.getToken();
        String url = buildM3ExecuteUrl();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Infor-IONAPI-ClientId", properties.getClientId());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> transactionPayload = new HashMap<>();
        transactionPayload.put("transaction", transaction);
        transactionPayload.put("record", record);

        Map<String, Object> body = new HashMap<>();
        body.put("program", program);
        body.put("cono", company);
        body.put("maxReturnedRecords", maxReturnedRecords);
        body.put("transactions", List.of(transactionPayload));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        log.debug("Calling M3 API: {} {} -> {}", program, transaction, url);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new M3ApiException(response.getStatusCodeValue(), response.getBody() != null ? response.getBody().toString() : "Empty response");
        }
        return response.getBody();
    }

    private String buildM3ExecuteUrl() {
        String baseUrl = properties.getBaseUrl().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return String.format("%s/%s/M3/m3api-rest/v2/execute", baseUrl, properties.getTenantId());
    }

    public static class M3ApiException extends RuntimeException {
        private final int statusCode;

        public M3ApiException(int statusCode, String message) {
            super(String.format("M3 API call failed with status %d: %s", statusCode, message));
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
