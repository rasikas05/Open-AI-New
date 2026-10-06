package com.ai.openai_api_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TEMPORARY DEMO ONLY — allowlisted tenants may call secured APIs without Cognito JWT.
 * Keep {@code codes} empty unless you explicitly accept tenant-level unauthenticated access
 * from the network boundary. Remove with the bypass filter when Cognito is mandatory again.
 */
@ConfigurationProperties(prefix = "security.tenant-auth-bypass")
public class TenantAuthBypassProperties {

    /**
     * Comma-separated tenant codes. Empty = feature off.
     * Phase-2 demo example: EM47JBNURMACJZ8F_DEM
     */
    private String codes = "";

    /** Header that carries the tenant code when JWT is absent. */
    private String header = "X-Tenant-Code";

    public String getCodes() {
        return codes;
    }

    public void setCodes(String codes) {
        this.codes = codes;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public Set<String> allowlistedCodes() {
        if (codes == null || codes.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(codes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean isAllowlisted(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return false;
        }
        return allowlistedCodes().contains(tenantCode.trim());
    }

    public String headerName() {
        return header == null || header.isBlank() ? "X-Tenant-Code" : header.trim();
    }

    @Override
    public String toString() {
        return "TenantAuthBypassProperties{allowlistSize="
                + allowlistedCodes().size()
                + ", header="
                + headerName().toLowerCase(Locale.ROOT)
                + "}";
    }
}
