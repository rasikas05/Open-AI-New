package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.ResponseFeedback;
import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.entity.Tenant;
import com.ai.openai_api_service.entity.User;
import com.ai.openai_api_service.model.FeedbackValue;
import com.ai.openai_api_service.model.ResponseFeedbackRequest;
import com.ai.openai_api_service.model.ResponseFeedbackResponse;
import com.ai.openai_api_service.repository.RequestLogRepository;
import com.ai.openai_api_service.repository.ResponseFeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseFeedbackServiceTest {

    @Mock
    private RequestLogRepository requestLogRepository;
    @Mock
    private ResponseFeedbackRepository responseFeedbackRepository;

    @InjectMocks
    private ResponseFeedbackService responseFeedbackService;

    private RequestLog requestLog;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("tenant1");
        User user = new User();
        user.setUsername("user1");
        user.setTenant(tenant);
        Session session = new Session();
        session.setSessionId("session1");
        session.setTenant(tenant);
        session.setUser(user);

        requestLog = new RequestLog();
        requestLog.setId(12345L);
        requestLog.setSession(session);
    }

    @Test
    void upsert_createsFeedback() {
        when(requestLogRepository.findById(12345L)).thenReturn(Optional.of(requestLog));
        when(responseFeedbackRepository.findByRequestLog_Id(12345L)).thenReturn(Optional.empty());
        when(responseFeedbackRepository.save(any())).thenAnswer(inv -> {
            ResponseFeedback saved = inv.getArgument(0);
            return saved;
        });

        ResponseFeedbackRequest request = baseRequest();
        request.setFeedback(FeedbackValue.GOOD);
        request.setComment("  helpful  ");

        ResponseFeedbackResponse response = responseFeedbackService.upsert(request);

        assertEquals(12345L, response.getRequestLogId());
        assertEquals(FeedbackValue.GOOD, response.getFeedback());
        assertEquals("helpful", response.getComment());

        ArgumentCaptor<ResponseFeedback> captor = ArgumentCaptor.forClass(ResponseFeedback.class);
        verify(responseFeedbackRepository).save(captor.capture());
        assertEquals(FeedbackValue.GOOD, captor.getValue().getFeedback());
        assertEquals("helpful", captor.getValue().getComment());
    }

    @Test
    void upsert_updatesExistingFeedbackGoodToBad() {
        ResponseFeedback existing = new ResponseFeedback();
        existing.setRequestLog(requestLog);
        existing.setFeedback(FeedbackValue.GOOD);
        existing.setComment("old");

        when(requestLogRepository.findById(12345L)).thenReturn(Optional.of(requestLog));
        when(responseFeedbackRepository.findByRequestLog_Id(12345L)).thenReturn(Optional.of(existing));
        when(responseFeedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseFeedbackRequest request = baseRequest();
        request.setFeedback(FeedbackValue.BAD);
        request.setComment("wrong warehouse");

        ResponseFeedbackResponse response = responseFeedbackService.upsert(request);

        assertEquals(FeedbackValue.BAD, response.getFeedback());
        assertEquals("wrong warehouse", response.getComment());
        verify(responseFeedbackRepository).save(existing);
    }

    @Test
    void upsert_ownershipMismatch_returnsNotFound() {
        when(requestLogRepository.findById(12345L)).thenReturn(Optional.of(requestLog));

        ResponseFeedbackRequest request = baseRequest();
        request.setUserId("other-user");
        request.setFeedback(FeedbackValue.GOOD);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> responseFeedbackService.upsert(request)
        );
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void upsert_missingRequestLog_returnsNotFound() {
        when(requestLogRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseFeedbackRequest request = baseRequest();
        request.setRequestLogId(999L);
        request.setFeedback(FeedbackValue.BAD);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> responseFeedbackService.upsert(request)
        );
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void upsert_blankComment_storedAsNull() {
        when(requestLogRepository.findById(12345L)).thenReturn(Optional.of(requestLog));
        when(responseFeedbackRepository.findByRequestLog_Id(12345L)).thenReturn(Optional.empty());
        when(responseFeedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseFeedbackRequest request = baseRequest();
        request.setFeedback(FeedbackValue.GOOD);
        request.setComment("   ");

        ResponseFeedbackResponse response = responseFeedbackService.upsert(request);
        assertNull(response.getComment());
    }

    private ResponseFeedbackRequest baseRequest() {
        ResponseFeedbackRequest request = new ResponseFeedbackRequest();
        request.setTenantCode("tenant1");
        request.setUserId("user1");
        request.setSessionId("session1");
        request.setRequestLogId(12345L);
        return request;
    }
}
