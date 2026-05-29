package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.python_rag.PythonQueryRequest;
import com.ai.openai_api_service.model.python_rag.PythonQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

@Service
public class PythonRagService {

    private static final Logger log = LoggerFactory.getLogger(PythonRagService.class);

    private final RestTemplate restTemplate;

    @Value("${python-rag.api.base-url:http://localhost:8083}")
    private String pythonRagBaseUrl;

    @Value("${python-rag.api.endpoint:/chat}")
    private String pythonRagEndpoint;

    @Value("${python-rag.api.timeout-ms:30000}")
    private int timeoutMs;

    @Value("${python-rag.api.top-k:5}")
    private int defaultTopK;

    @Value("${python-rag.api.final-limit:8}")
    private int defaultFinalLimit;

    @Value("${python-rag.api.enabled:true}")
    private boolean ragApiEnabled;

    public PythonRagService() {
        this.restTemplate = new RestTemplate();
    }

    /**
    * Query the Python RAG API with the provided message and optional parameters.
    *
    * @param message          The user's message
     * @param topK             Number of chunks per sub-query (optional, defaults to 5)
     * @param finalLimit       Max chunks after merge/rank (optional, defaults to 8)
     * @param deliverable      Filter by deliverable (optional)
     * @param programIds       Filter by M3 program IDs (optional)
     * @param docVersion       Filter by M3 version (optional)
     * @param skipRewrite      Skip LLM query rewriting (optional, defaults to false)
     * @return PythonQueryResponse with answer, sources, and usage info
     * @throws OpenAIException if the API call fails
     */
    public PythonQueryResponse query(
            String message,
            Integer topK,
            Integer finalLimit,
            String deliverable,
            java.util.List<String> programIds,
            String docVersion,
            Boolean skipRewrite) {

        if (!ragApiEnabled) {
            throw new OpenAIException(
                    "Python RAG API is disabled. Set python-rag.api.enabled=true in application.properties",
                    503
            );
        }

        if (message == null || message.isBlank()) {
            throw new OpenAIException("Message cannot be empty", 400);
        }

        // Build request with defaults
        PythonQueryRequest queryRequest = new PythonQueryRequest();
        queryRequest.setMessage(message);
        queryRequest.setTopK(topK != null ? topK : defaultTopK);
        queryRequest.setFinalLimit(finalLimit != null ? finalLimit : defaultFinalLimit);
        queryRequest.setDeliverable(deliverable);
        queryRequest.setProgramIds(programIds);
        queryRequest.setDocVersion(docVersion);
        queryRequest.setSkipRewrite(skipRewrite != null ? skipRewrite : false);

        return callPythonRagApi(queryRequest);
    }

    /**
     * Query the Python RAG API with a pre-built request object.
     *
     * @param queryRequest The Python query request DTO
     * @return PythonQueryResponse with answer, sources, and usage info
     * @throws OpenAIException if the API call fails
     */
    public PythonQueryResponse query(PythonQueryRequest queryRequest) {
        if (!ragApiEnabled) {
            throw new OpenAIException(
                    "Python RAG API is disabled. Set python-rag.api.enabled=true in application.properties",
                    503
            );
        }

        if (queryRequest == null || queryRequest.getMessage() == null || queryRequest.getMessage().isBlank()) {
            throw new OpenAIException("Message cannot be empty", 400);
        }

        // Apply defaults if not set
        if (queryRequest.getTopK() == null) {
            queryRequest.setTopK(defaultTopK);
        }
        if (queryRequest.getFinalLimit() == null) {
            queryRequest.setFinalLimit(defaultFinalLimit);
        }
        if (queryRequest.getSkipRewrite() == null) {
            queryRequest.setSkipRewrite(false);
        }

        return callPythonRagApi(queryRequest);
    }

    /**
     * Internal method to call the Python RAG API with error handling and logging.
     */
    private PythonQueryResponse callPythonRagApi(PythonQueryRequest queryRequest) {
        long startTime = System.currentTimeMillis();

        String url = buildPythonRagUrl();
        log.info(
                "Calling Python RAG API. url={}, message='{}', topK={}, finalLimit={}, deliverable={}, programIds={}, docVersion={}, skipRewrite={}",
                url,
                queryRequest.getMessage(),
                queryRequest.getTopK(),
                queryRequest.getFinalLimit(),
                queryRequest.getDeliverable(),
                queryRequest.getProgramIds(),
                queryRequest.getDocVersion(),
                queryRequest.getSkipRewrite()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<PythonQueryRequest> entity = new HttpEntity<>(queryRequest, headers);

        try {
            ResponseEntity<PythonQueryResponse> responseEntity = restTemplate.postForEntity(
                    url,
                    entity,
                    PythonQueryResponse.class
            );

            PythonQueryResponse response = responseEntity.getBody();
            long responseTime = System.currentTimeMillis() - startTime;

            if (response == null) {
                log.error("Python RAG API returned null response. url={}, responseTime={}ms", url, responseTime);
                throw new OpenAIException("No response from Python RAG API", 502);
            }

            log.info(
                    "Python RAG API call successful. url={}, responseTime={}ms, reply_length={}, sources_count={}, retrievedChunks={}, model={}, usage={}",
                    url,
                    responseTime,
                    response.getReply() != null ? response.getReply().length() : 0,
                    response.getSources() != null ? response.getSources().size() : 0,
                    response.getRetrievedChunks(),
                    response.getModel(),
                    response.getUsage()
            );

            return response;

        } catch (HttpClientErrorException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            int code = e.getStatusCode().value();
            String errorBody = e.getResponseBodyAsString();

            log.error(
                    "Python RAG API HTTP error. url={}, status={}, responseTime={}ms, errorBody={}",
                    url,
                    code,
                    responseTime,
                    errorBody
            );

            String msg = code == 404
                    ? "Python RAG API endpoint not found. Check python-rag.api.base-url and python-rag.api.endpoint"
                    : code == 400
                    ? "Python RAG API validation error: " + errorBody
                    : "Python RAG API error: " + code + " " + e.getStatusText();

            throw new OpenAIException(msg, code);

        } catch (ResourceAccessException e) {
            long responseTime = System.currentTimeMillis() - startTime;

            if (e.getCause() instanceof SocketTimeoutException) {
                log.error(
                        "Python RAG API timeout. url={}, timeout={}ms, responseTime={}ms",
                        url,
                        timeoutMs,
                        responseTime,
                        e
                );
                throw new OpenAIException(
                        "Python RAG API timeout after " + timeoutMs + "ms. The API may be slow or unreachable.",
                        504
                );
            } else {
                log.error(
                        "Python RAG API connection error. url={}, responseTime={}ms, error={}",
                        url,
                        responseTime,
                        e.getMessage(),
                        e
                );
                throw new OpenAIException(
                        "Cannot connect to Python RAG API at " + pythonRagBaseUrl + ". Check if the service is running.",
                        503
                );
            }

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error(
                    "Python RAG API unexpected error. url={}, responseTime={}ms, error={}",
                    url,
                    responseTime,
                    e.getMessage(),
                    e
            );
            throw new OpenAIException(
                    "Unexpected error calling Python RAG API: " + e.getMessage(),
                    500
            );
        }
    }

    private String buildPythonRagUrl() {
        String baseUrl = pythonRagBaseUrl.replaceAll("/$", "");
        String endpoint = pythonRagEndpoint.startsWith("/") ? pythonRagEndpoint : "/" + pythonRagEndpoint;
        return baseUrl + endpoint;
    }

    /**
     * Check if the Python RAG API is enabled and accessible.
     * Useful for health checks.
     */
    public boolean isEnabled() {
        return ragApiEnabled;
    }
}
