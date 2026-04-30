package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {
    List<RequestLog> findBySession_Id(Long sessionId);
}