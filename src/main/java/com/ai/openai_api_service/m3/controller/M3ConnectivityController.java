package com.ai.openai_api_service.m3.controller;

import com.ai.openai_api_service.m3.service.M3ApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/m3")
public class M3ConnectivityController {

    private final M3ApiClient m3ApiClient;

    public M3ConnectivityController(M3ApiClient m3ApiClient) {
        this.m3ApiClient = m3ApiClient;
    }

    @GetMapping("/connectivity")
    public ResponseEntity<JsonNode> testConnectivity(@RequestParam String itemNumber) {
        Map<String, Object> record = new HashMap<>();
        record.put("ITNO", itemNumber);
        JsonNode response = m3ApiClient.callMi("MNS110MI", "Get", record);
        return ResponseEntity.ok(response);
    }
}
