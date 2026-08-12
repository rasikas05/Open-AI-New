package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.ResponseFeedback;
import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.model.ResponseFeedbackRequest;
import com.ai.openai_api_service.model.ResponseFeedbackResponse;
import com.ai.openai_api_service.repository.RequestLogRepository;
import com.ai.openai_api_service.repository.ResponseFeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ResponseFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(ResponseFeedbackService.class);

    private final RequestLogRepository requestLogRepository;
    private final ResponseFeedbackRepository responseFeedbackRepository;

    public ResponseFeedbackService(
            RequestLogRepository requestLogRepository,
            ResponseFeedbackRepository responseFeedbackRepository
    ) {
        this.requestLogRepository = requestLogRepository;
        this.responseFeedbackRepository = responseFeedbackRepository;
    }

    @Transactional
    public ResponseFeedbackResponse upsert(ResponseFeedbackRequest request) {
        if (request.getFeedback() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "feedback is required");
        }

        String comment = normalizeComment(request.getComment());

        RequestLog requestLog = requestLogRepository.findById(request.getRequestLogId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        Session session = requestLog.getSession();
        if (session == null
                || session.getTenant() == null
                || session.getUser() == null
                || !Objects.equals(session.getTenant().getTenantCode(), request.getTenantCode())
                || !Objects.equals(session.getUser().getUsername(), request.getUserId())
                || !Objects.equals(session.getSessionId(), request.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
        }

        LocalDateTime now = LocalDateTime.now();
        ResponseFeedback entity = responseFeedbackRepository.findByRequestLog_Id(requestLog.getId())
                .orElseGet(ResponseFeedback::new);

        boolean isNew = entity.getId() == null;
        if (isNew) {
            entity.setRequestLog(requestLog);
            entity.setCreatedAt(now);
        }
        entity.setFeedback(request.getFeedback());
        entity.setComment(comment);
        entity.setUpdatedAt(now);

        ResponseFeedback saved = responseFeedbackRepository.save(entity);
        log.info(
                "Response feedback upserted. requestLogId={} feedback={} isNew={}",
                requestLog.getId(),
                saved.getFeedback(),
                isNew
        );

        return new ResponseFeedbackResponse(
                requestLog.getId(),
                saved.getFeedback(),
                saved.getComment(),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }

    private static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
