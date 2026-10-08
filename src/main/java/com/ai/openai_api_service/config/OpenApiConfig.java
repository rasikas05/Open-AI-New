package com.ai.openai_api_service.config;

import com.ai.openai_api_service.service.TenantAuthBypassService;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/**
 * Documents temporary demo header {@code X-Tenant-Code} in OpenAPI for ION/Swagger.
 * Does not change security rules: bypass still requires allowlist + ACTIVE tenant.
 */
@Configuration
public class OpenApiConfig {

    public static final String TENANT_BYPASS_HEADER_DESCRIPTION =
            "TEMPORARY DEMO ONLY. When no Authorization Bearer JWT is sent, this header "
                    + "identifies the tenant for Cognito auth bypass. Must equal an allowlisted "
                    + "ACTIVE tenant code (security.tenant-auth-bypass.codes) and must match the "
                    + "API path/body tenant. JWT clients omit this header.";

    @Bean
    public OperationCustomizer tenantAuthBypassHeaderCustomizer(TenantAuthBypassService bypassService) {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            String headerName = bypassService.headerName();
            boolean alreadyPresent = operation.getParameters() != null
                    && operation.getParameters().stream()
                    .anyMatch(p -> headerName.equalsIgnoreCase(p.getName())
                            && "header".equalsIgnoreCase(p.getIn()));
            if (!alreadyPresent) {
                operation.addParametersItem(new Parameter()
                        .in("header")
                        .name(headerName)
                        .required(false)
                        .description(TENANT_BYPASS_HEADER_DESCRIPTION)
                        .schema(new StringSchema()));
            }
            return operation;
        };
    }
}
