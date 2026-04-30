package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import com.ai.openai_api_service.model.SessionResponse;
import com.ai.openai_api_service.model.TenantCreateRequest;
import com.ai.openai_api_service.model.TenantResponse;
import com.ai.openai_api_service.model.UserResponse;
import com.ai.openai_api_service.repository.SessionRepository;
import com.ai.openai_api_service.repository.TenantRepository;
import com.ai.openai_api_service.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;

    public TenantService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            SessionRepository sessionRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public TenantResponse createTenant(TenantCreateRequest request) {
        if (tenantRepository.existsByTenantId(request.getTenantId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Tenant already exists");
        }

        Tenant tenant = new Tenant();
        tenant.setTenantId(request.getTenantId());
        tenant.setName(request.getName());
        tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(LocalDateTime.now());

        Tenant savedTenant = tenantRepository.save(tenant);
        return toResponse(savedTenant);
    }

    @Transactional
    public void registerUserAndSession(String tenantId, String userId, String sessionId, Integer tokenLimit) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Tenant not found"));

        userRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTenantId(tenantId);
                    newUser.setUserId(userId);
                    newUser.setCreatedAt(LocalDateTime.now());
                    return userRepository.save(newUser);
                });

        sessionRepository.findByTenantIdAndUserIdAndSessionId(tenantId, userId, sessionId)
                .orElseGet(() -> {
                    Session session = new Session();
                    session.setTenantId(tenantId);
                    session.setUserId(userId);
                    session.setSessionId(sessionId);
                    session.setStatus("ACTIVE");
                    session.setTokenLimit(tokenLimit != null ? tokenLimit : 0);
                    session.setTokensUsed(0);
                    session.setCreatedAt(LocalDateTime.now());
                    session.setUpdatedAt(LocalDateTime.now());
                    return sessionRepository.save(session);
                });
    }

    public List<UserResponse> getUsersForTenant(String tenantId) {
        tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Tenant not found"));

        return userRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    public List<SessionResponse> getSessionsForUser(String tenantId, String userId) {
        tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Tenant not found"));

        userRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "User not found"));

        return sessionRepository.findByTenantIdAndUserId(tenantId, userId)
                .stream()
                .map(this::toSessionResponse)
                .collect(Collectors.toList());
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getTenantId(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getCreatedAt()
        );
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                user.getUserId(),
                user.getCreatedAt()
        );
    }

    private SessionResponse toSessionResponse(Session session) {
        return new SessionResponse(
                session.getId(),
                session.getTenantId(),
                session.getUserId(),
                session.getSessionId(),
                session.getStatus(),
                session.getTokenLimit(),
                session.getTokensUsed(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getEndTime()
        );
    }
}
