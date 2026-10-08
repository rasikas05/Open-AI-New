package com.ai.openai_api_service.config;

import com.ai.openai_api_service.service.TenantAuthBypassService;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenApiConfigTest {

    @Mock
    private TenantAuthBypassService bypassService;

    @Test
    void customizer_addsOptionalTenantBypassHeader() {
        when(bypassService.headerName()).thenReturn("X-Tenant-Code");
        OperationCustomizer customizer = new OpenApiConfig().tenantAuthBypassHeaderCustomizer(bypassService);

        Operation operation = new Operation();
        customizer.customize(operation, mock(HandlerMethod.class));

        assertEquals(1, operation.getParameters().size());
        Parameter parameter = operation.getParameters().get(0);
        assertEquals("X-Tenant-Code", parameter.getName());
        assertEquals("header", parameter.getIn());
        assertFalse(Boolean.TRUE.equals(parameter.getRequired()));
        assertTrue(parameter.getDescription().contains("TEMPORARY DEMO ONLY"));
    }

    @Test
    void customizer_doesNotDuplicateExistingHeader() {
        when(bypassService.headerName()).thenReturn("X-Tenant-Code");
        OperationCustomizer customizer = new OpenApiConfig().tenantAuthBypassHeaderCustomizer(bypassService);

        Operation operation = new Operation();
        operation.addParametersItem(new Parameter().in("header").name("X-Tenant-Code").required(false));
        customizer.customize(operation, mock(HandlerMethod.class));

        assertEquals(1, operation.getParameters().size());
    }
}
