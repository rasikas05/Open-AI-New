package com.ai.openai_api_service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Request-scoped markers for temporary tenant auth bypass (not Cognito / not M2M).
 */
public final class TenantAuthBypassSupport {

    public static final String PRINCIPAL_NAME = "tenant-auth-bypass";
    public static final String ATTR_ACTIVE = "tenantAuthBypass";
    public static final String ATTR_TENANT_CODE = "bypassTenantCode";

    private TenantAuthBypassSupport() {
    }

    public static void markActive(HttpServletRequest request, String tenantCode) {
        request.setAttribute(ATTR_ACTIVE, Boolean.TRUE);
        request.setAttribute(ATTR_TENANT_CODE, tenantCode);
    }

    public static boolean isBypassActive() {
        HttpServletRequest request = currentRequest();
        return request != null && Boolean.TRUE.equals(request.getAttribute(ATTR_ACTIVE));
    }

    public static String bypassTenantCode() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(ATTR_TENANT_CODE);
        return value instanceof String s ? s : null;
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }
}
