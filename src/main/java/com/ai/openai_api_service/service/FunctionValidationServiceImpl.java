package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.FunctionMaster;
import com.ai.openai_api_service.model.FunctionDetailsResponse;
import com.ai.openai_api_service.model.FunctionValidationResponse;
import com.ai.openai_api_service.repository.FunctionMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FunctionValidationServiceImpl implements FunctionValidationService {

    private static final Logger log = LoggerFactory.getLogger(FunctionValidationServiceImpl.class);

    private final FunctionMasterRepository repository;

    public FunctionValidationServiceImpl(FunctionMasterRepository repository) {
        this.repository = repository;
    }

    @Override
    public FunctionValidationResponse validateFunctionIds(List<String> functionIds) {
        int totalRequested = functionIds == null ? 0 : functionIds.size();
        if (functionIds == null || functionIds.isEmpty()) {
            return new FunctionValidationResponse(totalRequested, 0, List.of(), List.of());
        }

        List<String> searchIds = functionIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<FunctionMaster> foundFunctions = searchIds.isEmpty()
                ? List.of()
                : repository.findByFnidIn(searchIds);

        Map<String, FunctionMaster> foundMap = foundFunctions.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(FunctionMaster::getFnid, fm -> fm, (first, second) -> first));

        List<FunctionDetailsResponse> validPrograms = new ArrayList<>();
        Set<String> validIdsAdded = new LinkedHashSet<>();
        Set<String> invalidPrograms = new LinkedHashSet<>();

        for (String fnid : functionIds) {
            if (fnid == null || fnid.isBlank()) {
                invalidPrograms.add(fnid);
                continue;
            }

            FunctionMaster match = foundMap.get(fnid);
            if (match != null) {
                if (validIdsAdded.add(fnid)) {
                    validPrograms.add(new FunctionDetailsResponse(
                            match.getFnid(),
                            match.getTx40(),
                            match.getFnt3(),
                            match.getMnid()
                    ));
                }
            } else {
                invalidPrograms.add(fnid);
            }
        }

        return new FunctionValidationResponse(
                totalRequested,
                validPrograms.size(),
                validPrograms,
                new ArrayList<>(invalidPrograms)
        );
    }
}
