package com.ai.openai_api_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 #14 / #21 — health + liveness/readiness anonymous for ALB;
 * P0 #20 — prometheus exposed but authenticated (not permitAll).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.prometheus.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:mysql://localhost:3306/openaichatprocessdb?createDatabaseIfNotExists=true",
        "spring.datasource.username=root",
        "spring.datasource.password=root",
        "presidio.enabled=false",
        "comprehend.anonymization.enabled=false",
        "lex.enabled=false",
        "python-rag.api.enabled=false"
})
class ActuatorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    /** Prevents SecurityConfig from calling the real Cognito JWKS endpoint at context startup. */
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void actuatorHealth_withoutJwt_isOk() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdSupport.HEADER));
    }

    @Test
    void actuatorHealth_reusesInboundCorrelationId() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header(CorrelationIdSupport.HEADER, "e2e-corr-fixed-id"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdSupport.HEADER, "e2e-corr-fixed-id"));
    }

    @Test
    void actuatorLiveness_withoutJwt_isOk() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorReadiness_withoutJwt_isOk() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorPrometheus_withoutJwt_isUnauthorized() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorEnv_withoutJwt_isUnauthorized() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorBeans_withoutJwt_isUnauthorized() throws Exception {
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorMappings_withoutJwt_isUnauthorized() throws Exception {
        mockMvc.perform(get("/actuator/mappings"))
                .andExpect(status().isUnauthorized());
    }
}
