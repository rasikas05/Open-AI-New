package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.RestTemplateFactory;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.python_rag.PythonQueryRequest;
import com.ai.openai_api_service.model.python_rag.PythonQueryResponse;
import com.ai.openai_api_service.model.python_rag.PythonRetrievalRequest;
import com.ai.openai_api_service.model.python_rag.PythonRetrievalResponse;
import com.ai.openai_api_service.model.python_rag.PythonRouteRequest;
import com.ai.openai_api_service.model.python_rag.PythonRouteResponse;
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

    private RestTemplate restTemplate;

    @Value("${python-rag.api.base-url:http://localhost:8083}")
    private String pythonRagBaseUrl;

    @Value("${python-rag.api.endpoint:/chat}")
    private String pythonRagEndpoint;

    @Value("${python-rag.api.retrieval-endpoint:/retrieval}")
    private String pythonRetrievalEndpoint;

    @Value("${python-rag.api.route-endpoint:/route}")
    private String pythonRouteEndpoint;

    @Value("${python-rag.api.timeout-ms:180000}")
    private int timeoutMs;

    @Value("${python-rag.api.top-k:5}")
    private int defaultTopK;

    @Value("${python-rag.api.final-limit:8}")
    private int defaultFinalLimit;

    @Value("${python-rag.api.enabled:true}")
    private boolean ragApiEnabled;

    public PythonRagService(@Value("${python-rag.api.timeout-ms:180000}") int timeoutMs) {
        this.restTemplate = RestTemplateFactory.create(timeoutMs);
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
     * Classify message as live M3 query or documentation RAG.
     */
    public PythonRouteResponse route(String message) {
        ensureEnabled();
        if (message == null || message.isBlank()) {
            throw new OpenAIException("Message cannot be empty", 400);
        }
        String url = buildUrl(pythonRouteEndpoint);
        PythonRouteRequest body = new PythonRouteRequest(message);
        log.info("Calling Python RAG route API. url={}, message='{}'", url, message);
        return postForEntity(url, body, PythonRouteResponse.class, "route");
    }

    /**
     * Retrieve documentation chunks with threshold metadata (no answer LLM).
     */
    public PythonRetrievalResponse retrieve(PythonQueryRequest queryRequest) {
        ensureEnabled();
        if (queryRequest == null || queryRequest.getMessage() == null || queryRequest.getMessage().isBlank()) {
            throw new OpenAIException("Message cannot be empty", 400);
        }

        PythonRetrievalRequest retrievalRequest = new PythonRetrievalRequest();
        retrievalRequest.setQuery(queryRequest.getMessage());
        retrievalRequest.setTopK(queryRequest.getTopK() != null ? queryRequest.getTopK() : defaultTopK);
        retrievalRequest.setFinalLimit(queryRequest.getFinalLimit() != null ? queryRequest.getFinalLimit() : defaultFinalLimit);
        retrievalRequest.setDeliverable(queryRequest.getDeliverable());
        retrievalRequest.setProgramIds(queryRequest.getProgramIds());
        retrievalRequest.setDocVersion(queryRequest.getDocVersion());
        retrievalRequest.setSkipRewrite(queryRequest.getSkipRewrite() != null ? queryRequest.getSkipRewrite() : false);

        String url = buildUrl(pythonRetrievalEndpoint);
        log.info(
                "Calling Python RAG retrieval API. url={}, query='{}', topK={}, finalLimit={}",
                url,
                retrievalRequest.getQuery(),
                retrievalRequest.getTopK(),
                retrievalRequest.getFinalLimit()
        );
        return postForEntity(url, retrievalRequest, PythonRetrievalResponse.class, "retrieval");
    }

    private void ensureEnabled() {
        if (!ragApiEnabled) {
            throw new OpenAIException(
                    "Python RAG API is disabled. Set python-rag.api.enabled=true in application.properties",
                    503
            );
        }
    }

    /**
     * Internal method to call the Python RAG API with error handling and logging.
     */
    private PythonQueryResponse callPythonRagApi(PythonQueryRequest queryRequest) {
        String url = buildUrl(pythonRagEndpoint);
        log.info(
                "Calling Python RAG chat API. url={}, message='{}', topK={}, finalLimit={}",
                url,
                queryRequest.getMessage(),
                queryRequest.getTopK(),
                queryRequest.getFinalLimit()
        );
        return postForEntity(url, queryRequest, PythonQueryResponse.class, "chat");
    }

    private <T> T postForEntity(String url, Object body, Class<T> responseType, String operation) {
        long startTime = System.currentTimeMillis();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<T> responseEntity = restTemplate.postForEntity(url, entity, responseType);
            T response = responseEntity.getBody();
            long responseTime = System.currentTimeMillis() - startTime;

            if (response == null) {
                log.error("Python RAG {} API returned null response. url={}, responseTime={}ms", operation, url, responseTime);
                throw new OpenAIException("No response from Python RAG API", 502);
            }

            log.info("Python RAG {} API call successful. url={}, responseTime={}ms", operation, url, responseTime);
            return response;

        } catch (HttpClientErrorException e) {
            handleHttpError(url, operation, startTime, e);
            return null;
        } catch (ResourceAccessException e) {
            handleResourceError(url, operation, startTime, e);
            return null;
        } catch (OpenAIException e) {
            throw e;
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error(
                    "Python RAG {} API unexpected error. url={}, responseTime={}ms, error={}",
                    operation,
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

    private void handleHttpError(String url, String operation, long startTime, HttpClientErrorException e) {
        long responseTime = System.currentTimeMillis() - startTime;
        int code = e.getStatusCode().value();
        String errorBody = e.getResponseBodyAsString();
        log.error(
                "Python RAG {} API HTTP error. url={}, status={}, responseTime={}ms, errorBody={}",
                operation,
                url,
                code,
                responseTime,
                errorBody
        );
        String msg = code == 404
                ? "Python RAG API endpoint not found. Check python-rag.api.base-url and endpoints"
                : code == 400
                ? "Python RAG API validation error: " + errorBody
                : "Python RAG API error: " + code + " " + e.getStatusText();
        throw new OpenAIException(msg, code);
    }

    private void handleResourceError(String url, String operation, long startTime, ResourceAccessException e) {
        long responseTime = System.currentTimeMillis() - startTime;
        if (e.getCause() instanceof SocketTimeoutException) {
            log.error(
                    "Python RAG {} API timeout. url={}, timeout={}ms, responseTime={}ms",
                    operation,
                    url,
                    timeoutMs,
                    responseTime,
                    e
            );
            throw new OpenAIException(
                    "Python RAG API timeout after " + timeoutMs + "ms. The API may be slow or unreachable.",
                    504
            );
        }
        log.error(
                "Python RAG {} API connection error. url={}, responseTime={}ms, error={}",
                operation,
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

    private String buildUrl(String endpoint) {
        String baseUrl = pythonRagBaseUrl.replaceAll("/$", "");
        String path = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return baseUrl + path;
    }

    private String buildPythonRagUrl() {
        return buildUrl(pythonRagEndpoint);
    }

    /**
     * Check if the Python RAG API is enabled and accessible.
     * Useful for health checks.
     */
    public boolean isEnabled() {
        return ragApiEnabled;
    }
}
