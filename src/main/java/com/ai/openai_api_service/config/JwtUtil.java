package com.ai.openai_api_service.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    public String getClientId() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            return null;
        }

        String clientId = jwt.getClaimAsString("client_id");
        if (clientId != null && !clientId.isBlank()) {
            return clientId;
        }

        String authorizedParty = jwt.getClaimAsString("azp");
        if (authorizedParty != null && !authorizedParty.isBlank()) {
            return authorizedParty;
        }

        String clientIdAlt = jwt.getClaimAsString("clientId");
        if (clientIdAlt != null && !clientIdAlt.isBlank()) {
            return clientIdAlt;
        }

        return null;
    }

    public Jwt getJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }
}