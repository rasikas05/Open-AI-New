package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.PiiEntityDto;
import software.amazon.awssdk.services.comprehend.model.PiiEntity;

import java.util.List;

public interface ComprehendService {
    List<PiiEntityDto> detectPii(String text);
}
