package com.ai.openai_api_service.controller;

import com.ai.openai_api_service.entity.PiiEntityDto;
import com.ai.openai_api_service.service.ComprehendService;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.comprehend.model.PiiEntity;

import java.util.List;

@RestController
@RequestMapping("/test")
public class ComprehendTestController {

    private final ComprehendService comprehendService;

    public ComprehendTestController(ComprehendService comprehendService) {
        this.comprehendService = comprehendService;
    }

    @PostMapping("/pii")
    public List<PiiEntityDto> detect(@RequestBody String text) {
        return comprehendService.detectPii(text);
    }
}