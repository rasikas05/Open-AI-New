package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.RestTemplateFactory;
import com.ai.openai_api_service.exception.TenantQuotaExceededException;
import com.ai.openai_api_service.exception.AiServiceErrors;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatMode;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.model.QueryRewriteResult;
import com.ai.openai_api_service.model.RequestUnderstandResult;
import com.ai.openai_api_service.model.RequestUnderstandType;
import com.ai.openai_api_service.model.python_rag.ChunkItem;
import com.ai.openai_api_service.model.rag.GroundedRagCallResult;
import com.ai.openai_api_service.model.rag.GroundedRagResult;
import com.ai.openai_api_service.model.rag.RagStatus;
import com.ai.openai_api_service.service.protection.BusinessInformationProtectionService;
import com.ai.openai_api_service.service.protection.ProtectionContext;
import com.ai.openai_api_service.service.protection.ProtectionPurpose;
import com.ai.openai_api_service.service.protection.ProtectionSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OpenAIService {
    private static final Logger log = LoggerFactory.getLogger(OpenAIService.class);

    private static final String RAG_SYSTEM_PROMPT = """
            You are an Infor M3 / CloudSuite documentation-grounded assistant. Apply CLEAR strictly.

            C - Context:
            Answer using ONLY the RETRIEVED DOCUMENTATION provided in the request. Do not use general M3 knowledge.

            Only RETRIEVED DOCUMENTATION is authoritative factual evidence.
            Chat history may be used only to resolve references such as "it", "this", or "that";
            never treat previous user or assistant messages as documentation evidence.

            L - Logic:
            Identify the user's primary question first. Use only relevant supporting content from retrieved documentation.
            Never invent or alter program IDs, MI transactions, APIs, fields, tables, panels, procedures, or facts.
            If the documentation does not directly support the requested task, do not infer or complete it from general knowledge.

            E - Expectations:
            Return ONLY this JSON object (no markdown fences, no prose outside JSON):
            {"status":"FULL"|"PARTIAL"|"INSUFFICIENT","answer":"...","missingTopics":[]}

            FULL = documentation completely answers the request.
            PARTIAL = documentation directly answers part of a multi-part request; list uncovered parts in missingTopics.
            INSUFFICIENT = documentation does not answer the requested task; set "answer" to "" and "missingTopics" to [].
            For a single-task question, related information is INSUFFICIENT, not PARTIAL.

            Write a concise, direct, well-structured answer in "answer". Use Markdown when it improves readability.
            Do not include http:// or https:// URLs in "answer".

            A - Actor:
            Act as an experienced Infor M3 documentation specialist and coverage classifier.

            R - References:
            Treat the retrieved Infor M3 documentation as the source of truth.
            Use relevant program IDs, MI transactions, fields, APIs, tables, panels,
            and document titles exactly as provided in the context.
            Never invent references or URLs.""";

    private static final int MAX_REWRITTEN_QUERIES = 3;
    private static final Pattern MARKDOWN_JSON_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
    /** Narrow false-PARTIAL detector: coverage/refusal commentary in the grounded answer. */
    private static final Pattern GROUNDED_COVERAGE_REFUSAL = Pattern.compile(
            "(?i)(?:the\\s+)?(?:supplied\\s+)?documentation\\s+does\\s+not\\s+(?:describe|cover|contain|include|provide|support|answer)"
                    + "|(?:the\\s+)?docs?\\s+do\\s+not\\s+(?:describe|cover|contain|include|provide)"
                    + "|not\\s+(?:described|covered|found|available)\\s+in\\s+the\\s+(?:supplied\\s+)?documentation"
                    + "|documentation\\s+cannot\\s+(?:support|answer|provide)"
    );
    private static final Pattern NUMBERED_STEP = Pattern.compile("(?m)^\\s*\\d+[.)]\\s+\\S");
    private static final int MIN_SUBSTANTIVE_ANSWER_CHARS = 80;

    private static final String REWRITE_SYSTEM_PROMPT = """
            You are an Infor M3 / CloudSuite documentation search specialist.

            Your only job is to transform the user's question into precise, high-relevance search queries \
            for Infor M3 documentation vector retrieval.

            Preserve the user's original intent and important technical terminology.
            Do not invent facts, program names, MI transactions, APIs, fields, or tables.

            Never answer the user.
            Output ONLY a valid JSON array of search-query strings.""";

    private static final String REWRITE_USER_PROMPT_TEMPLATE = """
            Rewrite the user query into 1-3 concise search queries for Infor M3 / CloudSuite documentation.

            CLEAR:
            C - Context: Understand the user's actual M3 question and business context.
            L - Logic: Identify one primary intent: configuration, setup, location, procedure, definition, \
            explanation, troubleshooting, API, or field lookup.
            E - Expectations: Preserve the intent. Create diverse queries only when they improve retrieval. \
            Do not add unrelated workflows, troubleshooting, or configuration.
            A - Actor: Use terminology suitable for official Infor M3 documentation.
            R - References: Preserve all user-provided technical identifiers exactly. Use M3 programs, MI transactions, \
            APIs, fields, tables, panels, or business terms only when explicitly provided or unambiguous. \
            Never invent identifiers.

            Keep queries short, specific, and keyword-rich. Remove conversational filler. Do not answer the question.

            User query:
            %s

            Return ONLY a valid JSON array of 1-3 strings.""";

    private static final String ROUTER_SYSTEM_PROMPT = """
            You are an Infor M3 RAG-path planner. Classify CONVERSATIONAL, RAG, LIVE_M3, or NON_M3. \
            You do not decide Lex — Spring does after Python LIVE/RAG gating. Never identify as ChatGPT. \
            Output ONLY JSON: {"type":"...","response":"...","queries":[]}

            Mode (HIGH PRIORITY; response context only; never choose Lex/routing):
            - M3: live tenant data/operations ONLY. Must not offer documentation, how-to, procedures, or configuration help.
            - AUTO: live tenant data AND documentation/how-to.
            - DOCS: documentation ONLY. Must not offer live tenant lookups.

            Domain: Infor M3 / CloudSuite (programs, masters, orders, items, warehouses, how-to/config). \
            Classify by this domain test, not off-topic phrase lists.

            CONVERSATIONAL: greetings, identity, thanks, how are you, what can you do. \
            Mixed greeting + in-domain how-to/docs → RAG (or LIVE_M3 if executing tenant data). \
            Reply 1–2 natural sentences matching the user act and Mode. Generic; no sample IDs or "try this" examples. queries [].
            RAG: M3 documentation, explanation, configuration, procedure, definition, conceptual how-to without tenant execute. \
            LIVE_M3: semantic label for retrieve/search/create/update/execute on tenant data (id present or clear live lookup). \
            Does not authorize Lex. NON_M3: not about Infor M3 / CloudSuite (AWS+CloudSuite → RAG). \
            When unclear, prefer RAG with queries over LIVE_M3.

            Examples: "Get customer ABC" → LIVE_M3. "What is OIS100?" → RAG. "Hi, how do I create a customer order?" → RAG.

            RAG: 1–3 short search queries; never invent program/MI/field IDs; response "". \
            LIVE_M3: response "" queries []. \
            NON_M3: short redirect to M3/CloudSuite only; politely redirect; queries []. \
            Never answer documentation questions in response for CONVERSATIONAL.""";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate restTemplate;
    private final PresidioService presidioService;
    private final ChatPersistenceService chatPersistenceService;
    private final TenantQuotaService tenantQuotaService;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.model}")
    private String model;

    @Value("${openai.api.url}")
    private String openaiUrl;

    @Value("${openai.assistant.system-prompt.enabled:true}")
    private boolean systemPromptEnabled;

    @Value("${openai.assistant.system-prompt:}")
    private String systemPrompt;

    @Value("${openai.input.remove-anonymization-placeholders:true}")
    private boolean removeAnonymizationPlaceholders;

    @Value("${openai.response.include-sanitization-debug:false}")
    private boolean includeSanitizationDebug;

    @Value("${chat.history.load-from-db:true}")
    private boolean loadHistoryFromDb;

    @Value("${chat.history.max-exchanges:10}")
    private int maxHistoryExchanges;

    /** OpenAI conversational history: previous user questions only. Display GET still uses max-exchanges. */
    @Value("${chat.history.max-user-questions:5}")
    private int maxUserQuestions;

    @Value("${chat.history.allow-client-history:false}")
    private boolean allowClientHistory;

    @Value("${openai.api.timeout-ms:120000}")
    private int openAiTimeoutMs;

    @Value("${openai.api.reasoning-effort:none}")
    private String reasoningEffort;

    @Value("${openai.api.max-completion-tokens:4096}")
    private int defaultMaxCompletionTokens;

    private final BusinessInformationProtectionService businessInformationProtectionService;

    /** Test-friendly constructor; protection remains inactive when null or flag=false. */
    public OpenAIService(
            PresidioService presidioService,
            ChatPersistenceService chatPersistenceService,
            TenantQuotaService tenantQuotaService
    ) {
        this(presidioService, chatPersistenceService, tenantQuotaService, null);
    }

    @Autowired
    public OpenAIService(
            PresidioService presidioService,
            ChatPersistenceService chatPersistenceService,
            TenantQuotaService tenantQuotaService,
            @Autowired(required = false) BusinessInformationProtectionService businessInformationProtectionService
    ) {
        this.presidioService = presidioService;
        this.chatPersistenceService = chatPersistenceService;
        this.tenantQuotaService = tenantQuotaService;
        this.businessInformationProtectionService = businessInformationProtectionService;
    }

    @PostConstruct
    void initRestTemplate() {
        this.restTemplate = RestTemplateFactory.create(openAiTimeoutMs);
    }

    /**
     * Direct OpenAI chat with persistence and quota (used by /api/chat).
     */
    public ChatResponse chat(ChatRequest request) {
        validateApiKey();
        String sanitizedUserText = presidioService.sanitizeText(request.getUserMessage());
        String modelReadyUserText = protectForLlm(prepareUserContentForOpenAi(sanitizedUserText), ProtectionPurpose.ANSWER);
        List<Map<String, String>> messages = buildMessages(request, systemPromptForFallback(), modelReadyUserText);
        OpenAiCallResult result = callOpenAi(messages);

        int consumedTokens = result.usage().getTotalTokens() != null ? result.usage().getTotalTokens() : 0;
        String usageReferenceId = request.getSessionId() + ":" + System.currentTimeMillis();
        try {
            tenantQuotaService.recordUsage(request.getTenantCode(), consumedTokens, usageReferenceId);
        } catch (TenantQuotaExceededException e) {
            return blockedResponse(e);
        }

        boolean sanitizedFlag = !Objects.equals(request.getUserMessage(), modelReadyUserText);
        chatPersistenceService.persistChat(
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                request.getUserMessage(),
                modelReadyUserText,
                result.content(),
                result.usage(),
                "gpt_infor",
                sanitizedFlag,
                null,
                null
        );

        return toChatResponse(request, result, "gpt_infor", request.getUserMessage(), modelReadyUserText);
    }

    /**
     * Fallback OpenAI chat without persistence (Comprehend orchestrates persist/quota).
     */
    public ChatResponse chatWithoutPersistence(ChatRequest request) {
        return chatWithoutPersistence(request, null);
    }

    public ChatResponse chatWithoutPersistence(ChatRequest request, ProtectionSession session) {
        validateApiKey();
        String modelReadyUserText;
        if (session != null) {
            modelReadyUserText = prepareUserContentForOpenAi(session.textForLlm());
        } else {
            modelReadyUserText = protectForLlm(prepareUserContentForOpenAi(request.getUserMessage()), ProtectionPurpose.ANSWER);
        }
        List<Map<String, String>> messages = buildMessages(request, systemPromptForFallback(), modelReadyUserText, true);
        OpenAiCallResult result = callOpenAi(messages);
        return toChatResponse(request, result, "gpt_infor", request.getUserMessage(), modelReadyUserText);
    }

    /**
     * Grounded OpenAI chat using pre-filtered RAG chunks. Returns structured status JSON result.
     */
    public GroundedRagCallResult chatWithRagContext(ChatRequest request, List<ChunkItem> promptChunks) {
        return chatWithRagContext(request, promptChunks, null);
    }

    /**
     * Grounded OpenAI chat. When {@code session} is non-null, uses {@link ProtectionSession#textForLlm()}
     * only (Decision #27–#28) — does not re-apply business protection.
     */
    public GroundedRagCallResult chatWithRagContext(
            ChatRequest request,
            List<ChunkItem> promptChunks,
            ProtectionSession session
    ) {
        validateApiKey();
        if (promptChunks == null || promptChunks.isEmpty()) {
            throw new OpenAIException("promptChunks cannot be empty for grounded chat", 400);
        }

        long buildStartMs = System.currentTimeMillis();
        String userQuestion;
        if (session != null) {
            userQuestion = prepareUserContentForOpenAi(session.textForLlm());
        } else {
            userQuestion = isBusinessProtectionActive()
                    ? protectForLlm(prepareUserContentForOpenAi(request.getUserMessage()), ProtectionPurpose.ANSWER)
                    : request.getUserMessage();
        }
        String context = formatRagContext(promptChunks);
        String userPrompt = buildRagUserPrompt(context, userQuestion);

        List<Map<String, String>> messages = buildMessages(request, RAG_SYSTEM_PROMPT, userPrompt, true);
        long promptBuildMs = System.currentTimeMillis() - buildStartMs;
        int promptContextChars = context != null ? context.length() : 0;

        OpenAiCallResult result = callOpenAi(messages);
        long openAiWaitMs = result.elapsedMs();

        long parseStartMs = System.currentTimeMillis();
        GroundedRagResult grounded = parseGroundedRagResult(result.content());
        long responseParseMs = System.currentTimeMillis() - parseStartMs;

        int promptTokens = result.usage() != null && result.usage().getPromptTokens() != null
                ? result.usage().getPromptTokens()
                : 0;
        int completionTokens = result.usage() != null && result.usage().getCompletionTokens() != null
                ? result.usage().getCompletionTokens()
                : 0;
        log.info(
                "Grounded Stage Timing | promptBuildMs={} | openAiWaitMs={} | responseParseMs={} | "
                        + "totalMs={} | chunkCount={} | promptContextChars={} | promptTokens={} | completionTokens={}",
                promptBuildMs,
                openAiWaitMs,
                responseParseMs,
                promptBuildMs + openAiWaitMs + responseParseMs,
                promptChunks.size(),
                promptContextChars,
                promptTokens,
                completionTokens
        );

        return new GroundedRagCallResult(
                grounded,
                result.usage(),
                result.content(),
                promptBuildMs,
                openAiWaitMs,
                responseParseMs,
                promptContextChars,
                promptChunks.size()
        );
    }

    /**
     * General GPT fill for PARTIAL missing topics only (documentation answer is never regenerated here).
     */
    public ChatResponse chatGapFill(
            ChatRequest request,
            String documentationAnswer,
            List<String> missingTopics
    ) {
        return chatGapFill(request, documentationAnswer, missingTopics, null);
    }

    public ChatResponse chatGapFill(
            ChatRequest request,
            String documentationAnswer,
            List<String> missingTopics,
            ProtectionSession session
    ) {
        validateApiKey();
        String modelReadyUserText;
        String questionForPrompt;
        if (session != null) {
            modelReadyUserText = prepareUserContentForOpenAi(session.textForLlm());
            questionForPrompt = modelReadyUserText != null ? modelReadyUserText : "";
        } else {
            modelReadyUserText = isBusinessProtectionActive()
                    ? protectForLlm(prepareUserContentForOpenAi(request.getUserMessage()), ProtectionPurpose.ANSWER)
                    : prepareUserContentForOpenAi(request.getUserMessage());
            questionForPrompt = isBusinessProtectionActive()
                    ? modelReadyUserText
                    : (request.getUserMessage() != null ? request.getUserMessage() : "");
        }
        String topics = missingTopics == null || missingTopics.isEmpty()
                ? "(none)"
                : missingTopics.stream().map(t -> "- " + t).reduce((a, b) -> a + "\n" + b).orElse("(none)");
        String userPrompt = """
                User Question:
                %s

                Documentation Answer:
                %s

                Missing Topics:
                %s

                Answer ONLY the missing topics listed above. Generate only the missing information as a \
                natural continuation of the Documentation Answer so it can be appended directly. \
                Do not repeat the documentation answer. Do not answer topics that are already covered. \
                Do not mention documentation, missing topics, AI information, or the retrieval process.
                """.formatted(
                questionForPrompt,
                documentationAnswer != null ? documentationAnswer : "",
                topics
        );
        List<Map<String, String>> messages = buildMessages(request, systemPromptForFallback(), userPrompt, true);
        OpenAiCallResult result = callOpenAi(messages);
        return toChatResponse(request, result, "gpt_infor", request.getUserMessage(), modelReadyUserText);
    }

    GroundedRagResult parseGroundedRagResult(String content) {
        if (content == null || content.isBlank()) {
            throw new OpenAIException("Empty grounded RAG response from OpenAI", 502);
        }
        String cleaned = content.strip();
        Matcher matcher = MARKDOWN_JSON_FENCE.matcher(cleaned);
        if (matcher.find()) {
            cleaned = matcher.group(1).strip();
        }
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            if (root == null || !root.isObject()) {
                throw new OpenAIException("Grounded RAG response is not a JSON object", 502);
            }
            JsonNode statusNode = root.get("status");
            if (statusNode == null || statusNode.asText().isBlank()) {
                throw new OpenAIException("Grounded RAG response missing status", 502);
            }
            RagStatus status;
            try {
                status = RagStatus.valueOf(statusNode.asText().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new OpenAIException("Invalid grounded RAG status: " + statusNode.asText(), 502);
            }
            String answer = root.has("answer") && !root.get("answer").isNull()
                    ? root.get("answer").asText("")
                    : "";
            List<String> missingTopics = new ArrayList<>();
            if (root.has("missingTopics") && root.get("missingTopics").isArray()) {
                for (JsonNode item : root.get("missingTopics")) {
                    if (item != null && !item.asText("").isBlank()) {
                        missingTopics.add(item.asText().strip());
                    }
                }
            }
            if (status == RagStatus.FULL) {
                missingTopics = List.of();
            }
            if (status == RagStatus.INSUFFICIENT) {
                missingTopics = List.of();
                answer = "";
            }
            if (status == RagStatus.PARTIAL && isFalsePartialCoverageRefusal(answer)) {
                log.warn(
                        "False PARTIAL coerced to INSUFFICIENT | reason=coverage_refusal_without_substantive_answer | answerChars={}",
                        answer != null ? answer.length() : 0
                );
                status = RagStatus.INSUFFICIENT;
                answer = "";
                missingTopics = List.of();
            }
            return new GroundedRagResult(status, answer, missingTopics);
        } catch (OpenAIException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAIException("Failed to parse grounded RAG JSON: " + e.getMessage(), 502);
        }
    }

    /**
     * Narrow guard for the known false-PARTIAL failure: model returns PARTIAL with
     * documentation-coverage/refusal commentary and no substantive answer body.
     * Does not attempt to semantically judge whether docs match the user question.
     */
    static boolean isFalsePartialCoverageRefusal(String answer) {
        if (answer == null || answer.isBlank()) {
            return true;
        }
        if (!GROUNDED_COVERAGE_REFUSAL.matcher(answer).find()) {
            return false;
        }
        String withoutRefusal = GROUNDED_COVERAGE_REFUSAL.matcher(answer).replaceAll(" ");
        String remaining = withoutRefusal.replaceAll("\\s+", " ").strip();
        if (remaining.length() >= MIN_SUBSTANTIVE_ANSWER_CHARS) {
            return false;
        }
        return !NUMBERED_STEP.matcher(answer).find();
    }

    /**
     * CLEAR Prompt 1 — rewrite sanitized user text into 1-3 search queries for Python retrieval.
     * Falls back to a single-query list on parse or API failure.
     */
    public QueryRewriteResult rewriteQueries(String sanitizedQuery) {
        return rewriteQueries(null, sanitizedQuery);
    }

    public QueryRewriteResult rewriteQueries(ChatRequest request, String sanitizedQuery) {
        validateApiKey();
        if (sanitizedQuery == null || sanitizedQuery.isBlank()) {
            throw new OpenAIException("Sanitized query cannot be empty for rewrite", 400);
        }

        // Read-only consumer (Decision #28): do not re-run BIP when caller already protected.
        String queryForLlm = sanitizedQuery;
        List<MessageDto> userHistory = request == null ? List.of() : resolveHistory(request);
        String userPrompt = buildRewriteUserContent(queryForLlm, userHistory);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", REWRITE_SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt)
        );

        try {
            OpenAiCallResult result = callOpenAi(messages, 0.3, 256);
            List<String> queries = parseQueriesFromLlm(result.content());
            if (queries.size() > MAX_REWRITTEN_QUERIES) {
                queries = queries.subList(0, MAX_REWRITTEN_QUERIES);
            }
            log.info("Query rewrite produced {} search queries: {}", queries.size(), queries);
            return new QueryRewriteResult(queries, result.usage());
        } catch (OpenAIException e) {
            if (e.isAiServiceUnavailable()) {
                throw e;
            }
            log.warn("Query rewriting failed: {}. Falling back to original sanitized query.", e.getMessage());
            OpenAIUsage fallbackUsage = new OpenAIUsage(0, 0, 0, model);
            return new QueryRewriteResult(List.of(sanitizedQuery), fallbackUsage);
        } catch (Exception e) {
            if (AiServiceErrors.isQuotaOrCreditExhaustion(e.getMessage())) {
                throw AiServiceErrors.unavailable(e.getMessage());
            }
            log.warn("Query rewriting failed: {}. Falling back to original sanitized query.", e.getMessage());
            OpenAIUsage fallbackUsage = new OpenAIUsage(0, 0, 0, model);
            return new QueryRewriteResult(List.of(sanitizedQuery), fallbackUsage);
        }
    }

    /**
     * Request-understanding router. Returns structured type + optional user response / RAG queries.
     * Does not execute Live M3 or retrieval.
     */
    public RequestUnderstandResult understandRequest(ChatRequest request, String sanitizedQuery) {
        validateApiKey();
        if (sanitizedQuery == null || sanitizedQuery.isBlank()) {
            throw new OpenAIException("Sanitized query cannot be empty for request router", 400);
        }

        String queryForLlm = sanitizedQuery;
        List<MessageDto> userHistory = request == null ? List.of() : resolveHistory(request);
        ChatMode mode = request != null && request.getMode() != null ? request.getMode() : ChatMode.AUTO;
        String userPrompt = buildUnderstandUserContent(queryForLlm, userHistory, mode);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", ROUTER_SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt)
        );

        OpenAiCallResult result = callOpenAi(messages, 0.3, 256, true);
        RequestUnderstandResult parsed = parseUnderstandFromLlm(result.content(), result.usage());
        log.info(
                "Request router understood type={} queryCount={}",
                parsed.type(),
                parsed.queries() != null ? parsed.queries().size() : 0
        );
        return parsed;
    }

    RequestUnderstandResult parseUnderstandFromLlm(String content, OpenAIUsage usage) {
        if (content == null || content.isBlank()) {
            throw new OpenAIException("Empty request-router response from OpenAI", 502);
        }
        String cleaned = content.strip();
        Matcher matcher = MARKDOWN_JSON_FENCE.matcher(cleaned);
        if (matcher.find()) {
            cleaned = matcher.group(1).strip();
        }
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            if (root == null || !root.isObject()) {
                throw new OpenAIException("Request-router response is not a JSON object", 502);
            }
            JsonNode typeNode = root.get("type");
            if (typeNode == null || typeNode.isNull() || typeNode.asText().isBlank()) {
                throw new OpenAIException("Request-router JSON missing type", 502);
            }
            String rawType = typeNode.asText().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            RequestUnderstandType type;
            try {
                type = RequestUnderstandType.valueOf(rawType);
            } catch (IllegalArgumentException e) {
                throw new OpenAIException("Unknown request-router type: " + rawType, 502);
            }

            String response = "";
            JsonNode responseNode = root.get("response");
            if (responseNode != null && !responseNode.isNull()) {
                response = responseNode.asText("").strip();
            }

            List<String> queries = new ArrayList<>();
            JsonNode queriesNode = root.get("queries");
            if (queriesNode != null && queriesNode.isArray()) {
                for (JsonNode item : queriesNode) {
                    if (item != null && item.isTextual() && !item.asText().isBlank()) {
                        queries.add(item.asText().strip());
                    }
                }
            }
            if (queries.size() > MAX_REWRITTEN_QUERIES) {
                queries = new ArrayList<>(queries.subList(0, MAX_REWRITTEN_QUERIES));
            }

            if (type == RequestUnderstandType.RAG) {
                response = "";
            } else if (type == RequestUnderstandType.LIVE_M3) {
                response = "";
                queries = List.of();
            } else if (response.isBlank()) {
                throw new OpenAIException("Request-router " + type + " response must be non-empty", 502);
            } else {
                queries = List.of();
            }

            OpenAIUsage resolvedUsage = usage != null ? usage : new OpenAIUsage(0, 0, 0, model);
            return new RequestUnderstandResult(type, response, List.copyOf(queries), resolvedUsage);
        } catch (OpenAIException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAIException("Failed to parse request-router JSON: " + e.getMessage(), 502);
        }
    }

    List<String> parseQueriesFromLlm(String content) {
        if (content == null || content.isBlank()) {
            throw new OpenAIException("Empty rewrite response from OpenAI", 502);
        }
        String cleaned = content.strip();
        Matcher matcher = MARKDOWN_JSON_FENCE.matcher(cleaned);
        if (matcher.find()) {
            cleaned = matcher.group(1).strip();
        }
        try {
            List<String> parsed = objectMapper.readValue(cleaned, new TypeReference<List<String>>() {});
            List<String> queries = parsed.stream()
                    .filter(q -> q != null && !q.isBlank())
                    .map(String::strip)
                    .toList();
            if (queries.isEmpty()) {
                throw new OpenAIException("No valid queries in rewrite response", 502);
            }
            return new ArrayList<>(queries);
        } catch (OpenAIException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAIException("Failed to parse rewrite response as JSON array: " + e.getMessage(), 502);
        }
    }

    private ChatResponse toChatResponse(
            ChatRequest request,
            OpenAiCallResult result,
            String actionTaken,
            String originalUserText,
            String modelReadyUserText
    ) {
        ChatResponse chatResponse = new ChatResponse(result.content(), result.truncated());
        chatResponse.setHistory(request.getHistory());
        chatResponse.setActionTaken(actionTaken);
        chatResponse.setOpenAiUsage(result.usage());
        chatResponse.setSanitizationApplied(!Objects.equals(originalUserText, modelReadyUserText));
        if (includeSanitizationDebug) {
            chatResponse.setSanitizedUserMessage(modelReadyUserText);
        }
        return chatResponse;
    }

    private String systemPromptForFallback() {
        if (systemPromptEnabled && systemPrompt != null && !systemPrompt.isBlank()) {
            return systemPrompt.trim();
        }
        return "";
    }

    private List<Map<String, String>> buildMessages(ChatRequest request, String systemContent, String userContent) {
        return buildMessages(request, systemContent, userContent, false);
    }

    List<Map<String, String>> buildMessages(
            ChatRequest request,
            String systemContent,
            String userContent,
            boolean skipHistoryPresidio
    ) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemContent != null && !systemContent.isBlank()) {
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemContent);
            messages.add(systemMessage);
        }

        List<MessageDto> sourceHistory = resolveHistory(request);
        if (sourceHistory != null) {
            for (MessageDto message : sourceHistory) {
                String role = message.getRole() == null ? null : message.getRole().trim().toLowerCase(Locale.ROOT);
                String content = message.getContent() == null ? null : message.getContent().trim();
                if (!isValidRole(role) || content == null || content.isBlank()) {
                    throw new OpenAIException(
                            "Invalid history item. role must be system/user/assistant and content must be non-empty.",
                            400
                    );
                }
                Map<String, String> map = new HashMap<>();
                map.put("role", role);
                map.put("content", "user".equals(role)
                        ? protectForLlm(
                                prepareUserContentForOpenAi(
                                        skipHistoryPresidio ? content : presidioService.sanitizeText(content)),
                                ProtectionPurpose.ANSWER)
                        : content);
                messages.add(map);
            }
        }

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userContent);
        messages.add(userMessage);
        return messages;
    }

    List<MessageDto> resolveHistory(ChatRequest request) {
        List<MessageDto> clientHistory = request.getHistory() != null ? request.getHistory() : List.of();
        boolean hasClientHistory = allowClientHistory && !clientHistory.isEmpty();

        List<MessageDto> sourceHistory;
        if (hasClientHistory) {
            sourceHistory = clientHistory;
        } else if (loadHistoryFromDb) {
            sourceHistory = chatPersistenceService.loadHistoryForPrompt(
                    request.getTenantCode(),
                    request.getUserId(),
                    request.getSessionId(),
                    maxHistoryExchanges
            );
        } else {
            sourceHistory = List.of();
        }

        return toOpenAiUserHistory(sourceHistory, request.getUserMessage());
    }

    /**
     * Previous user questions only. Latest {@code maxUserQuestions}.
     * Drops only a trailing item equal to the current user message.
     */
    List<MessageDto> toOpenAiUserHistory(List<MessageDto> sourceHistory, String currentUserMessage) {
        List<MessageDto> users = new ArrayList<>();
        if (sourceHistory != null) {
            for (MessageDto message : sourceHistory) {
                if (message == null) {
                    continue;
                }
                String role = message.getRole() == null ? "" : message.getRole().trim().toLowerCase(Locale.ROOT);
                String content = message.getContent() == null ? "" : message.getContent().trim();
                if (!"user".equals(role) || content.isBlank()) {
                    continue;
                }
                users.add(new MessageDto("user", content));
            }
        }
        int cap = Math.max(0, maxUserQuestions);
        if (users.size() > cap) {
            users = new ArrayList<>(users.subList(users.size() - cap, users.size()));
        }
        String current = currentUserMessage == null ? "" : currentUserMessage.trim();
        if (!current.isEmpty() && !users.isEmpty()
                && current.equals(users.get(users.size() - 1).getContent())) {
            users.remove(users.size() - 1);
        }
        return users;
    }

    String buildRewriteUserContent(String sanitizedQuery, List<MessageDto> userHistory) {
        String currentPrompt = REWRITE_USER_PROMPT_TEMPLATE.formatted(sanitizedQuery);
        if (userHistory == null || userHistory.isEmpty()) {
            return currentPrompt;
        }
        StringBuilder previous = new StringBuilder();
        for (MessageDto message : userHistory) {
            String content = message.getContent() == null ? "" : message.getContent().trim();
            if (content.isBlank()) {
                continue;
            }
            previous.append("- ").append(content).append('\n');
        }
        if (previous.isEmpty()) {
            return currentPrompt;
        }
        return "PREVIOUS USER QUESTIONS:\n"
                + previous
                + "\nCURRENT QUESTION:\n"
                + currentPrompt;
    }

    String buildUnderstandUserContent(String sanitizedQuery, List<MessageDto> userHistory) {
        return buildUnderstandUserContent(sanitizedQuery, userHistory, ChatMode.AUTO);
    }

    String buildUnderstandUserContent(String sanitizedQuery, List<MessageDto> userHistory, ChatMode mode) {
        ChatMode resolved = mode != null ? mode : ChatMode.AUTO;
        String current = sanitizedQuery == null ? "" : sanitizedQuery.trim();
        String modeLine = "Mode: " + resolved.name() + "\n";
        if (userHistory == null || userHistory.isEmpty()) {
            return modeLine + "CURRENT QUESTION:\n" + current;
        }
        StringBuilder previous = new StringBuilder();
        for (MessageDto message : userHistory) {
            String content = message.getContent() == null ? "" : message.getContent().trim();
            if (content.isBlank()) {
                continue;
            }
            previous.append("- ").append(content).append('\n');
        }
        if (previous.isEmpty()) {
            return modeLine + "CURRENT QUESTION:\n" + current;
        }
        return modeLine
                + "PREVIOUS USER QUESTIONS:\n"
                + previous
                + "\nCURRENT QUESTION:\n"
                + current;
    }

    private OpenAiCallResult callOpenAi(List<Map<String, String>> messages) {
        return callOpenAi(messages, null, null, false);
    }

    private OpenAiCallResult callOpenAi(List<Map<String, String>> messages, Double temperature, Integer maxTokens) {
        return callOpenAi(messages, temperature, maxTokens, false);
    }

    private OpenAiCallResult callOpenAi(
            List<Map<String, String>> messages,
            Double temperature,
            Integer maxTokens,
            boolean jsonObjectResponse
    ) {
        Map<String, Object> body = OpenAiChatRequestBuilder.buildChatCompletionBody(
                model,
                reasoningEffort,
                defaultMaxCompletionTokens,
                messages,
                temperature,
                maxTokens,
                jsonObjectResponse
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        log.info(
                "Calling OpenAI chat completions. model={}, reasoningEffort={}, messageCount={}",
                model,
                OpenAiChatRequestBuilder.effectiveReasoningEffort(model, reasoningEffort),
                messages.size()
        );
        long start = System.currentTimeMillis();

        Map<String, Object> response;
        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    openaiUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );
            response = responseEntity.getBody();
        } catch (HttpClientErrorException e) {
            handleOpenAiError(e);
            throw new OpenAIException("OpenAI call failed", e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("OpenAI request failed after {}ms: {}", elapsed, e.getMessage());
            throw new OpenAIException(
                    "OpenAI request timed out or failed after " + elapsed + "ms: " + e.getMessage(),
                    504
            );
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("OpenAI chat completions completed in {}ms", elapsed);

        if (response == null) {
            throw new OpenAIException("No response from OpenAI.", 502);
        }

        String content = extractContent(response);
        boolean truncated = isTruncated(response);
        OpenAIUsage usage = extractUsage(response, model);
        return new OpenAiCallResult(content, truncated, usage, elapsed);
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAIException(
                    "OpenAI API key is missing. Set OPENAI_API_KEY env var or openai.api.key property.",
                    401
            );
        }
    }

    private void handleOpenAiError(HttpClientErrorException e) {
        int code = e.getStatusCode().value();
        HttpHeaders h = e.getResponseHeaders();
        if (h != null) {
            log.warn("OpenAI x-ratelimit-limit-requests={}", h.getFirst("x-ratelimit-limit-requests"));
            log.warn("OpenAI x-ratelimit-remaining-requests={}", h.getFirst("x-ratelimit-remaining-requests"));
            log.warn("OpenAI x-ratelimit-reset-requests={}", h.getFirst("x-ratelimit-reset-requests"));
            log.warn("OpenAI x-ratelimit-limit-tokens={}", h.getFirst("x-ratelimit-limit-tokens"));
            log.warn("OpenAI x-ratelimit-remaining-tokens={}", h.getFirst("x-ratelimit-remaining-tokens"));
            log.warn("OpenAI x-ratelimit-reset-tokens={}", h.getFirst("x-ratelimit-reset-tokens"));
        }
        String body = e.getResponseBodyAsString();
        log.warn("OpenAI error status={} body={}", e.getStatusCode(), body);
        if (AiServiceErrors.isQuotaOrCreditExhaustion(body) || AiServiceErrors.isQuotaOrCreditExhaustion(e.getMessage())) {
            throw AiServiceErrors.unavailable("OpenAI status=" + code + " body=" + body);
        }
        String msg = code == 401
                ? "OpenAI API key is invalid or missing. Check openai.api.key in application.properties (no quotes)."
                : "OpenAI API error: " + code + " " + e.getStatusText();
        throw new OpenAIException(msg, code);
    }

    private ChatResponse blockedResponse(TenantQuotaExceededException e) {
        ChatResponse blocked = new ChatResponse("Token limit reached for this tenant. Please top up to continue.", false);
        blocked.setLimitExceeded(true);
        blocked.setUsage(e.getUsage());
        blocked.setBlockReason("LIMIT_EXCEEDED");
        blocked.setUpgradeOptions(Arrays.asList("Buy 100 tokens", "Buy 500 tokens", "Buy 5000 tokens"));
        return blocked;
    }

    private String extractContent(Map<String, Object> response) {
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choicesList) || choicesList.isEmpty()) {
            throw new OpenAIException("No choices returned from OpenAI.", 502);
        }
        Object firstChoice = choicesList.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new OpenAIException("Unexpected response format from OpenAI.", 502);
        }
        Object messageObj = choiceMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw new OpenAIException("Unexpected message format from OpenAI.", 502);
        }
        Object contentObj = messageMap.get("content");
        return contentObj != null ? contentObj.toString() : "";
    }

    private boolean isTruncated(Map<String, Object> response) {
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choicesList) || choicesList.isEmpty()) {
            return false;
        }
        Object firstChoice = choicesList.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return false;
        }
        Object finishReason = choiceMap.get("finish_reason");
        return finishReason != null && "length".equals(finishReason.toString());
    }

    OpenAIUsage extractUsage(Map<String, Object> response, String modelUsed) {
        OpenAIUsage usage = new OpenAIUsage();
        usage.setModel(modelUsed);
        Object usageObj = response.get("usage");
        if (!(usageObj instanceof Map<?, ?> usageMap)) {
            usage.setPromptTokens(0);
            usage.setCompletionTokens(0);
            usage.setTotalTokens(0);
            return usage;
        }
        usage.setPromptTokens(toInt(usageMap.get("prompt_tokens")));
        usage.setCompletionTokens(toInt(usageMap.get("completion_tokens")));
        usage.setTotalTokens(toInt(usageMap.get("total_tokens")));
        return usage;
    }

    private Integer toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    String formatRagContext(List<ChunkItem> chunks) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkItem chunk = chunks.get(i);
            float score = chunk.getScore() != null ? chunk.getScore() : 0f;
            String scorePct = String.format(Locale.ROOT, "%.1f%%", score * 100);
            builder.append("### Context");
            if (chunk.getTitle() != null && !chunk.getTitle().isBlank()) {
                builder.append("\nTitle: ").append(chunk.getTitle());
            }
            if (chunk.getSectionPath() != null && !chunk.getSectionPath().isEmpty()) {
                builder.append("\nSection: ").append(String.join(" > ", chunk.getSectionPath()));
            }
            if (chunk.getProgramIds() != null && !chunk.getProgramIds().isEmpty()) {
                builder.append("\nPrograms: ").append(String.join(", ", chunk.getProgramIds()));
            }
            if (chunk.getSource() != null && !chunk.getSource().isBlank()) {
                builder.append("\nSource: ").append(chunk.getSource());
            }
            builder.append("\nRelevance: ").append(scorePct);
            builder.append("\n\n").append(chunk.getChunk() != null ? chunk.getChunk() : "").append("\n\n");
        }
        return builder.toString().trim();
    }

    String buildRagUserPrompt(String context, String question) {
        return "USER QUESTION:\n" + question + "\n\nRETRIEVED DOCUMENTATION:\n" + context;
    }

    /** Package-visible for unit tests. */
    String ragSystemPrompt() {
        return RAG_SYSTEM_PROMPT;
    }

    /** Package-visible for unit tests. */
    String rewriteSystemPrompt() {
        return REWRITE_SYSTEM_PROMPT;
    }

    /** Package-visible for unit tests. */
    String rewriteUserPromptTemplate() {
        return REWRITE_USER_PROMPT_TEMPLATE;
    }

    /** Package-visible for unit tests. External/fallback CLEAR prompt from configuration. */
    String assistantSystemPrompt() {
        return systemPromptForFallback();
    }

    /** Package-visible for unit tests. */
    String routerSystemPrompt() {
        return ROUTER_SYSTEM_PROMPT;
    }

    private boolean isValidRole(String role) {
        return "system".equals(role) || "user".equals(role) || "assistant".equals(role);
    }

    private String prepareUserContentForOpenAi(String sanitizedText) {
        if (sanitizedText == null) {
            return null;
        }
        if (!removeAnonymizationPlaceholders) {
            return sanitizedText;
        }
        String withoutTags = sanitizedText
                .replaceAll("<[A-Z_]+>", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return withoutTags.isBlank() ? sanitizedText : withoutTags;
    }

    /**
     * LLM-egress protection. Flag false / null service → return text unchanged (identical path).
     * Live/Lex never calls this helper.
     * Phase 7A: temporary PII→Business order (Decision #20). Uses {@link ProtectionSession}.
     */
    private String protectForLlm(String text, ProtectionPurpose purpose) {
        if (!isBusinessProtectionActive() || text == null) {
            return text;
        }
        ProtectionSession session = ProtectionSession.fromPiiSanitized(text);
        businessInformationProtectionService.protect(
                session,
                ProtectionContext.forPurpose(purpose, true)
        );
        return session.textForLlm();
    }

    private boolean isBusinessProtectionActive() {
        return businessInformationProtectionService != null
                && businessInformationProtectionService.isEnabled();
    }

    private record OpenAiCallResult(String content, boolean truncated, OpenAIUsage usage, long elapsedMs) {
        OpenAiCallResult(String content, boolean truncated, OpenAIUsage usage) {
            this(content, truncated, usage, 0L);
        }
    }
}
