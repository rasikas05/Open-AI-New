package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.RestTemplateFactory;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.M3RequestDto;
import com.ai.openai_api_service.model.python_rag.M3ExecuteRequest;
import com.ai.openai_api_service.model.python_rag.M3ExecuteResponse;
import com.ai.openai_api_service.model.python_rag.M3MiCallRequest;
import com.ai.openai_api_service.model.python_rag.M3MiCallResponse;
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

    @Value("${python-rag.api.m3-execute-endpoint:/m3/execute}")
    private String pythonM3ExecuteEndpoint;

    @Value("${python-rag.api.m3-call-endpoint:/m3/call}")
    private String pythonM3CallEndpoint;

    @Value("${python-rag.api.timeout-ms:180000}")
    private int timeoutMs;

    @Value("${python-rag.api.top-k:5}")
    private int defaultTopK;

    @Value("${python-rag.api.final-limit:8}")
    private int defaultFinalLimit;

    @Value("${python-rag.api.enabled:true}")
    private boolean ragApiEnabled;

    @Value("${rag.program.boost:0.08}")
    private double programBoost;

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
        PythonRouteResponse response = postForEntity(url, body, PythonRouteResponse.class, "route");
        String selectedRoute = response.getRoute() != null ? response.getRoute() : "rag";
        log.info(
                "Python RAG route selected: route='{}', nextStep='{}', message='{}'",
                selectedRoute,
                "live".equalsIgnoreCase(selectedRoute) ? "python/chat" : "python/retrieval",
                message
        );
        return response;
    }

    /**
     * Execute a live M3 tool via Python (Lex fulfillment path).
     */
    public M3ExecuteResponse executeLiveIntent(String toolName, java.util.Map<String, Object> args) {
        ensureEnabled();
        if (toolName == null || toolName.isBlank()) {
            throw new OpenAIException("Tool name cannot be empty", 400);
        }
        String url = buildUrl(pythonM3ExecuteEndpoint);
        M3ExecuteRequest body = new M3ExecuteRequest(toolName, args != null ? args : java.util.Map.of());
        log.info("Calling Python M3 execute API. url={}, tool='{}', args={}", url, toolName, args);
        return postForEntity(url, body, M3ExecuteResponse.class, "m3-execute");
    }

    /**
     * Execute a generic M3 MI transaction via Python (Lex SEARCH fulfillment path).
     */
    public M3MiCallResponse executeMi(M3RequestDto request, String company, int maxReturnedRecords) {
        ensureEnabled();
        if (request == null) {
            throw new OpenAIException("M3 request cannot be null", 400);
        }
        if (request.getProgram() == null || request.getProgram().isBlank()) {
            throw new OpenAIException("M3 program cannot be empty", 400);
        }
        if (request.getTransaction() == null || request.getTransaction().isBlank()) {
            throw new OpenAIException("M3 transaction cannot be empty", 400);
        }

        String url = buildUrl(pythonM3CallEndpoint);
        M3MiCallRequest body = new M3MiCallRequest(
                request.getProgram(),
                request.getTransaction(),
                request.getParams() != null ? request.getParams() : java.util.Map.of()
        );
        body.setCompany(company);
        body.setMaxReturnedRecords(maxReturnedRecords);

        log.info(
                "Calling Python M3 call API. url={}, program='{}', transaction='{}', params={}",
                url,
                request.getProgram(),
                request.getTransaction(),
                request.getParams()
        );
        return postForEntity(url, body, M3MiCallResponse.class, "m3-call");
    }

    /**
     * Retrieve documentation chunks (legacy callers without pre-computed queries).
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

    /**
     * Retrieve using pre-computed search queries from Spring (comprehend doc path).
     * Always skips Python-side rewrite; queries are embedded and searched directly.
     * Soft-boosts chunks matching {@code boostProgramIds} without hard-filtering.
     */
    public PythonRetrievalResponse retrieve(
            String query,
            java.util.List<String> searchQueries,
            PythonQueryRequest queryRequest,
            java.util.List<String> boostProgramIds
    ) {
        ensureEnabled();
        if (query == null || query.isBlank()) {
            throw new OpenAIException("Message cannot be empty", 400);
        }
        if (searchQueries == null || searchQueries.isEmpty()) {
            throw new OpenAIException("Search queries cannot be empty", 400);
        }

        PythonRetrievalRequest retrievalRequest = new PythonRetrievalRequest();
        retrievalRequest.setQuery(query);
        retrievalRequest.setQueries(searchQueries);
        retrievalRequest.setTopK(queryRequest.getTopK() != null ? queryRequest.getTopK() : defaultTopK);
        retrievalRequest.setFinalLimit(queryRequest.getFinalLimit() != null ? queryRequest.getFinalLimit() : defaultFinalLimit);
        retrievalRequest.setDeliverable(queryRequest.getDeliverable());
        // Hard filter stays unset on doc path; soft boost uses boost_program_ids only.
        retrievalRequest.setProgramIds(null);
        retrievalRequest.setBoostProgramIds(
                boostProgramIds != null && !boostProgramIds.isEmpty() ? boostProgramIds : null
        );
        retrievalRequest.setProgramBoost(programBoost);
        retrievalRequest.setDocVersion(queryRequest.getDocVersion());
        retrievalRequest.setSkipRewrite(true);

        String url = buildUrl(pythonRetrievalEndpoint);
        log.info(
                "Calling Python RAG retrieval API. url={}, query='{}', queryCount={}, topK={}, finalLimit={}, "
                        + "boostProgramIds={}, programBoost={}, skipRewrite=true",
                url,
                retrievalRequest.getQuery(),
                searchQueries.size(),
                retrievalRequest.getTopK(),
                retrievalRequest.getFinalLimit(),
                boostProgramIds == null || boostProgramIds.isEmpty() ? "none" : boostProgramIds,
                programBoost
        );
        return postForEntity(url, retrievalRequest, PythonRetrievalResponse.class, "retrieval");
    }

    /**
     * Legacy overload without soft boost (no detected program IDs).
     */
    public PythonRetrievalResponse retrieve(String query, java.util.List<String> searchQueries, PythonQueryRequest queryRequest) {
        return retrieve(query, searchQueries, queryRequest, null);
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
            log.info(
                    "Python RAG {} | {}ms | {}",
                    operation,
                    responseTime,
                    summarizeResponse(response, operation)
            );
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

    private String summarizeResponse(Object response, String operation) {
        if (response instanceof PythonRouteResponse routeResponse) {
            String route = routeResponse.getRoute() != null ? routeResponse.getRoute() : "rag";
            String next = "live".equalsIgnoreCase(route) ? "chat" : "retrieval";
            return "route=" + route + " next=" + next;
        }
        if (response instanceof PythonQueryResponse chatResponse) {
            StringBuilder summary = new StringBuilder();
            if (chatResponse.getActionTaken() != null) {
                summary.append("action=").append(chatResponse.getActionTaken());
            }
            if (chatResponse.getCollectingTool() != null) {
                if (summary.length() > 0) {
                    summary.append(' ');
                }
                summary.append("collecting=").append(chatResponse.getCollectingTool());
                if (chatResponse.getNextField() != null) {
                    summary.append(" field=").append(chatResponse.getNextField());
                }
            }
            if (chatResponse.getPendingTool() != null) {
                if (summary.length() > 0) {
                    summary.append(' ');
                }
                summary.append("pending=").append(chatResponse.getPendingTool());
            }
            return summary.length() > 0 ? summary.toString() : "chat ok";
        }
        if (response instanceof PythonRetrievalResponse retrievalResponse) {
            return String.format(
                    "reason=%s maxScore=%s chunks=%s",
                    retrievalResponse.getRetrievalReason(),
                    retrievalResponse.getMaxScore(),
                    retrievalResponse.getPromptChunkCount()
            );
        }
        if (response instanceof M3ExecuteResponse executeResponse) {
            return String.format(
                    "tool=%s action=%s error=%s",
                    executeResponse.getTool(),
                    executeResponse.getActionTaken(),
                    executeResponse.getError()
            );
        }
        if (response instanceof M3MiCallResponse miCallResponse) {
            int recordCount = miCallResponse.getRecords() != null ? miCallResponse.getRecords().size() : 0;
            return String.format(
                    "program=%s transaction=%s records=%s error=%s",
                    miCallResponse.getProgram(),
                    miCallResponse.getTransaction(),
                    recordCount,
                    miCallResponse.getError()
            );
        }
        return "ok";
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
