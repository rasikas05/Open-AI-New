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
        if (tenantRepository.existsByTenantCode(request.getTenantCode())) {
            throw new ResponseStatusException(BAD_REQUEST, "Tenant already exists");
        }

        Tenant tenant = new Tenant();
        tenant.setTenantCode(request.getTenantCode());
        tenant.setName(request.getName());
        tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(LocalDateTime.now());

        Tenant savedTenant = tenantRepository.save(tenant);
        return toResponse(savedTenant);
    }

    @Transactional
    public void registerUserAndSession(String tenantId, String userId, String sessionId) {
        registerUserAndSession(tenantId, userId, sessionId, null);
    }

    @Transactional
    public void registerUserAndSession(String tenantId, String userId, String sessionId, Integer tokenLimit) {
        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Tenant not found"));

        User user = userRepository.findByTenantAndUsername(tenant, userId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTenant(tenant);
                    newUser.setUsername(userId);
                    newUser.setCreatedAt(LocalDateTime.now());
                    return userRepository.save(newUser);
                });

        sessionRepository.findByTenantAndUserAndSessionId(tenant, user, sessionId)
                .orElseGet(() -> {
                    Session session = new Session();
                    session.setTenant(tenant);
                    session.setUser(user);
                    session.setSessionId(sessionId);
                    session.setStatus("ACTIVE");
                    session.setTokensUsed(0);
                    session.setCreatedAt(LocalDateTime.now());
                    session.setUpdatedAt(LocalDateTime.now());
                    Session saved = sessionRepository.save(session);
                    return saved;
                });
    }

    public List<UserResponse> getUsersForTenant(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Tenant not found"));

        return userRepository.findByTenant(tenant)
                .stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    public List<SessionResponse> getSessionsForUser(String tenantId, String userId) {

        Tenant tenant = tenantRepository.findByTenantCode(tenantId)
                .orElseThrow(() ->
                        new ResponseStatusException(BAD_REQUEST, "Tenant not found"));

        User user = userRepository.findByTenantAndUsername(tenant, userId)
                .orElseThrow(() ->
                        new ResponseStatusException(BAD_REQUEST, "User not found"));

        return sessionRepository
                .findByTenantAndUserOrderByUpdatedAtDesc(tenant, user)
                .stream()
                .map(this::toSessionResponse)
                .collect(Collectors.toList());
    }
    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getTenantCode(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getCreatedAt()
        );
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getTenant().getTenantCode(),
                user.getUsername(),
                user.getCreatedAt()
        );
    }

    private SessionResponse toSessionResponse(Session session) {
        return new SessionResponse(
                session.getId(),
                session.getTenant().getTenantCode(),
                session.getUser().getUsername(),
                session.getSessionId(),
                session.getStatus(),
                session.getTokensUsed(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getEndTime()
        );
    }
}
