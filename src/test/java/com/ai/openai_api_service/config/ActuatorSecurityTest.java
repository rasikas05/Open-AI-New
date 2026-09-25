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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 #14 — health remains anonymous for GFT/ALB; other Actuator paths are not permitAll
 * (Security returns 401 before MVC 404 when the path requires authentication).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health",
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
                .andExpect(status().isOk());
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
