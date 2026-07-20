package com.ai.openai_api_service.service.m3;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.M3RequestDto;
import com.ai.openai_api_service.model.python_rag.M3MiCallResponse;
import com.ai.openai_api_service.service.PythonRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class M3ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(M3ExecutionService.class);

    private final PythonRagService pythonRagService;
    private final MiResponseParser miResponseParser;
    private final String defaultCompany;
    private final int maxReturnedRecords;

    public M3ExecutionService(
            PythonRagService pythonRagService,
            MiResponseParser miResponseParser,
            @Value("${m3.execution.company:100}") String defaultCompany,
            @Value("${m3.execution.max-records:50}") int maxReturnedRecords
    ) {
        this.pythonRagService = pythonRagService;
        this.miResponseParser = miResponseParser;
        this.defaultCompany = defaultCompany;
        this.maxReturnedRecords = maxReturnedRecords;
    }

    public M3MiExecutionResult execute(M3RequestDto request) {
        if (request == null || !request.isExecute()) {
            return M3MiExecutionResult.failure("", "", "M3 execution is not requested");
        }

        String program = request.getProgram();
        String transaction = request.getTransaction();
        if (program == null || program.isBlank()) {
            return M3MiExecutionResult.failure("", nullToEmpty(transaction), "M3 program is required");
        }
        if (transaction == null || transaction.isBlank()) {
            return M3MiExecutionResult.failure(program, "", "M3 transaction is required");
        }

        try {
            M3MiCallResponse response = pythonRagService.executeMi(
                    request,
                    defaultCompany,
                    maxReturnedRecords
            );
            M3MiExecutionResult parsed = miResponseParser.parse(response);
            log.info(
                    "M3 execution: program='{}' transaction='{}' success={} recordCount={}",
                    program,
                    transaction,
                    parsed.success(),
                    parsed.recordCount()
            );
            return parsed;
        } catch (OpenAIException e) {
            log.warn(
                    "M3 execution failed: program='{}' transaction='{}' message='{}'",
                    program,
                    transaction,
                    e.getMessage()
            );
            return M3MiExecutionResult.failure(program, transaction, e.getMessage());
        } catch (Exception e) {
            log.warn(
                    "M3 execution unexpected error: program='{}' transaction='{}'",
                    program,
                    transaction,
                    e
            );
            return M3MiExecutionResult.failure(program, transaction, e.getMessage());
        }
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
