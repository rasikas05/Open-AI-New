package com.ai.openai_api_service.m3.controller;

import com.ai.openai_api_service.m3.config.M3Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/m3/debug")
public class M3DebugController {

    private static final Logger log = LoggerFactory.getLogger(M3DebugController.class);
    private final M3Properties m3Properties;

    public M3DebugController(M3Properties m3Properties) {
        this.m3Properties = m3Properties;
    }

    @GetMapping("/properties")
    public Map<String, Object> getM3Properties() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("baseUrl", m3Properties.getBaseUrl());
        props.put("tokenUrl", m3Properties.getTokenUrl());
        props.put("clientId", m3Properties.getClientId());
        props.put("clientSecret", m3Properties.getClientSecret() != null ? "***MASKED***" : null);
        props.put("username", m3Properties.getUsername() != null ? "***MASKED***" : null);
        props.put("password", m3Properties.getPassword() != null ? "***MASKED***" : null);
        props.put("tenantId", m3Properties.getTenantId());
        props.put("scope", m3Properties.getScope());
        props.put("company", m3Properties.getCompany());
        props.put("tokenCacheBufferSeconds", m3Properties.getTokenCacheBufferSeconds());
        props.put("tokenTimeoutSeconds", m3Properties.getTokenTimeoutSeconds());

        log.info("M3 Debug Properties Requested: {}", props);
        return props;
    }
}
