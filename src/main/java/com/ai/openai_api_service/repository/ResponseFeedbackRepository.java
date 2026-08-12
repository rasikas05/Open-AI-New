package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.ResponseFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResponseFeedbackRepository extends JpaRepository<ResponseFeedback, Long> {
    Optional<ResponseFeedback> findByRequestLog_Id(Long requestLogId);
}
