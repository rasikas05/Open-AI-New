package com.ai.openai_api_service.services.impl;

import com.ai.openai_api_service.entity.PiiEntityDto;
import com.ai.openai_api_service.service.ComprehendService;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.DetectPiiEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.DetectPiiEntitiesResponse;
import software.amazon.awssdk.services.comprehend.model.PiiEntity;

import java.util.List;

@Service
public class ComprehendServiceImpl implements ComprehendService {

    private final ComprehendClient client;

    public ComprehendServiceImpl(ComprehendClient client) {
        this.client = client;
    }

    @Override
    public List<PiiEntityDto> detectPii(String text) {

        DetectPiiEntitiesRequest request = DetectPiiEntitiesRequest.builder()
                .text(text)
                .languageCode("en")
                .build();

        DetectPiiEntitiesResponse response = client.detectPiiEntities(request);

        return response.entities().stream()
                .map(e -> new PiiEntityDto(
                        e.typeAsString(),
                        e.beginOffset(),
                        e.endOffset(),
                        e.score()
                ))
                .toList();
    }
}