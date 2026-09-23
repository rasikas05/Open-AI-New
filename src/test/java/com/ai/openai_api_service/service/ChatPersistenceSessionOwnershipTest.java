package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import com.ai.openai_api_service.repository.RequestLogRepository;
import com.ai.openai_api_service.repository.SessionRepository;
import com.ai.openai_api_service.repository.TenantRepository;
import com.ai.openai_api_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPersistenceSessionOwnershipTest {

    private static final String TENANT_A = "tenant-a";
    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";
    private static final String SESSION_A = "session-a";

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private RequestLogRepository requestLogRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;

    private ChatPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ChatPersistenceService(
                sessionRepository,
                requestLogRepository,
                tenantRepository,
                userRepository,
                100
        );
    }

    @Test
    void requireSession_ownedByUser_returnsSession() {
        Tenant tenant = tenant(TENANT_A);
        User userA = user(tenant, USER_A);
        Session session = session(tenant, userA, SESSION_A);

        when(tenantRepository.findByTenantCode(TENANT_A)).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, USER_A)).thenReturn(Optional.of(userA));
        when(sessionRepository.findByTenantAndUserAndSessionId(tenant, userA, SESSION_A))
                .thenReturn(Optional.of(session));

        assertSame(session, service.requireSessionForTenantUser(TENANT_A, USER_A, SESSION_A));
        verify(sessionRepository, never()).findBySessionId(any());
    }

    @Test
    void requireSession_wrongUser_forbidden() {
        Tenant tenant = tenant(TENANT_A);
        User userA = user(tenant, USER_A);
        User userB = user(tenant, USER_B);
        Session session = session(tenant, userA, SESSION_A);

        when(tenantRepository.findByTenantCode(TENANT_A)).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, USER_B)).thenReturn(Optional.of(userB));
        when(sessionRepository.findByTenantAndUserAndSessionId(tenant, userB, SESSION_A))
                .thenReturn(Optional.empty());
        when(sessionRepository.findBySessionId(SESSION_A)).thenReturn(Optional.of(session));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.requireSessionForTenantUser(TENANT_A, USER_B, SESSION_A)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Session does not belong to this user", ex.getReason());
    }

    @Test
    void requireSession_wrongUserWhenRequestedUserMissing_forbidden() {
        Tenant tenant = tenant(TENANT_A);
        User userA = user(tenant, USER_A);
        Session session = session(tenant, userA, SESSION_A);

        when(tenantRepository.findByTenantCode(TENANT_A)).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, USER_B)).thenReturn(Optional.empty());
        when(sessionRepository.findBySessionId(SESSION_A)).thenReturn(Optional.of(session));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.requireSessionForTenantUser(TENANT_A, USER_B, SESSION_A)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void requireSession_unknown_notFound() {
        Tenant tenant = tenant(TENANT_A);
        User userA = user(tenant, USER_A);

        when(tenantRepository.findByTenantCode(TENANT_A)).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantAndUsername(tenant, USER_A)).thenReturn(Optional.of(userA));
        when(sessionRepository.findByTenantAndUserAndSessionId(tenant, userA, "missing"))
                .thenReturn(Optional.empty());
        when(sessionRepository.findBySessionId("missing")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.requireSessionForTenantUser(TENANT_A, USER_A, "missing")
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void closeAuthorizedSession_closesGivenEntity() {
        Tenant tenant = tenant(TENANT_A);
        User userA = user(tenant, USER_A);
        Session session = session(tenant, userA, SESSION_A);
        session.setStatus("ACTIVE");

        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        Session closed = service.closeAuthorizedSession(session);
        assertEquals("CLOSED", closed.getStatus());
        verify(sessionRepository).save(session);
        verify(sessionRepository, never()).findBySessionId(eq(SESSION_A));
    }

    private static Tenant tenant(String code) {
        Tenant t = new Tenant();
        t.setTenantCode(code);
        return t;
    }

    private static User user(Tenant tenant, String username) {
        User u = new User();
        u.setTenant(tenant);
        u.setUsername(username);
        return u;
    }

    private static Session session(Tenant tenant, User user, String sessionId) {
        Session s = new Session();
        s.setTenant(tenant);
        s.setUser(user);
        s.setSessionId(sessionId);
        s.setStatus("ACTIVE");
        return s;
    }
}
