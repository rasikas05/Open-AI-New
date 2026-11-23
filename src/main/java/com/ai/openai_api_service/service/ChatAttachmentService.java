package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.ChatAttachment;
import com.ai.openai_api_service.entity.ChatMessagePreparation;
import com.ai.openai_api_service.entity.RequestLog;
import com.ai.openai_api_service.entity.Session;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatAttachmentDto;
import com.ai.openai_api_service.model.ChatAttachmentRef;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ImageAttachmentRequest;
import com.ai.openai_api_service.repository.ChatAttachmentRepository;
import com.ai.openai_api_service.repository.RequestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ChatAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(ChatAttachmentService.class);
    private static final int MAX_ATTACHMENTS = 3;
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private final ChatAttachmentRepository attachmentRepository;
    private final ChatPrepareService chatPrepareService;
    private final RequestLogRepository requestLogRepository;

    public ChatAttachmentService(
            ChatAttachmentRepository attachmentRepository,
            ChatPrepareService chatPrepareService,
            RequestLogRepository requestLogRepository
    ) {
        this.attachmentRepository = attachmentRepository;
        this.chatPrepareService = chatPrepareService;
        this.requestLogRepository = requestLogRepository;
    }

    public boolean requiresMessageId(ChatRequest request) {
        if (request == null) {
            return false;
        }
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            return true;
        }
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            return true;
        }
        return isPresent(request.getImageBase64())
                || isPresent(request.getImageMediaType())
                || isPresent(request.getImageFileName());
    }

    /**
     * Validates preparation + attachment refs. Call before vision when messageId is required.
     */
    @Transactional(readOnly = true)
    public void validateForChat(ChatRequest request) {
        if (!requiresMessageId(request)) {
            if (request != null && isPresent(request.getMessageId())) {
                // Text-only with stray messageId is ignored (backward compatible).
            }
            return;
        }
        String messageId = request.getMessageId();
        if (!isPresent(messageId)) {
            throw new OpenAIException("messageId is required when images or attachments are present", 400);
        }
        chatPrepareService.requireUsablePreparation(
                messageId.trim(),
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId()
        );
        validateAttachmentRefs(request);
    }

    /**
     * Upserts attachment rows for the message, then links request_log_id and consumes preparation.
     */
    @Transactional
    public List<ChatAttachmentDto> persistAndFinalize(ChatRequest request, Long requestLogId) {
        if (request == null || !requiresMessageId(request)) {
            return List.of();
        }
        String messageId = request.getMessageId().trim();
        chatPrepareService.requireUsablePreparation(
                messageId,
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId()
        );

        List<ChatAttachmentRef> refs = normalizeRefs(request);
        List<ChatAttachment> saved = new ArrayList<>();
        for (ChatAttachmentRef ref : refs) {
            saved.add(upsert(request, messageId, ref));
        }

        if (requestLogId != null) {
            for (ChatAttachment row : saved) {
                row.setRequestLogId(requestLogId);
            }
            attachmentRepository.saveAll(saved);
        }

        boolean consumed = chatPrepareService.consumePrepared(messageId);
        if (!consumed) {
            throw new OpenAIException("messageId was consumed by a concurrent request", 409);
        }

        log.info(
                "Persisted {} attachments for messageId={} requestLogId={}",
                saved.size(),
                messageId,
                requestLogId
        );
        return saved.stream().map(this::toDto).toList();
    }

    /**
     * GET attachment metadata. Does not use {@link ChatPrepareService#requireUsablePreparation}.
     * Lookup: attachments → RequestLog → preparation → 404.
     */
    @Transactional(readOnly = true)
    public List<ChatAttachmentDto> listByMessageId(
            String tenantCode,
            String userId,
            String sessionId,
            String messageId
    ) {
        String mid = requireNonBlank(messageId, "messageId");
        String tenant = requireNonBlank(tenantCode, "tenantCode");
        String uid = requireNonBlank(userId, "userId");
        String sid = requireNonBlank(sessionId, "sessionId");

        List<ChatAttachment> rows = attachmentRepository
                .findByTenantCodeAndMessageIdOrderByImageOrderAsc(tenant, mid);
        if (!rows.isEmpty()) {
            ChatAttachment first = rows.get(0);
            if (!first.getUserId().equals(uid) || !first.getSessionId().equals(sid)) {
                throw new OpenAIException("Not authorized for this messageId", 403);
            }
            return rows.stream().map(this::toDto).toList();
        }

        Optional<RequestLog> requestLog = requestLogRepository.findByMessageId(mid);
        if (requestLog.isPresent()) {
            assertRequestLogOwnership(requestLog.get(), tenant, uid, sid);
            return List.of();
        }

        Optional<ChatMessagePreparation> preparation = chatPrepareService.findPreparation(mid);
        if (preparation.isPresent()) {
            assertPreparationOwnership(preparation.get(), tenant, uid, sid);
            return List.of();
        }

        throw new OpenAIException("Unknown messageId", 404);
    }

    @Transactional(readOnly = true)
    public List<ChatAttachmentDto> listByRequestLogIds(String tenantCode, Collection<Long> requestLogIds) {
        if (requestLogIds == null || requestLogIds.isEmpty()) {
            return List.of();
        }
        return attachmentRepository
                .findByTenantCodeAndRequestLogIdInOrderByImageOrderAsc(tenantCode, requestLogIds)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Batch load attachments grouped by request_log_id, preserving imageOrder within each group.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<ChatAttachmentDto>> mapByRequestLogIds(
            String tenantCode,
            Collection<Long> requestLogIds
    ) {
        if (requestLogIds == null || requestLogIds.isEmpty()) {
            return Map.of();
        }
        List<ChatAttachment> rows = attachmentRepository
                .findByTenantCodeAndRequestLogIdInOrderByImageOrderAsc(tenantCode, requestLogIds);
        Map<Long, List<ChatAttachmentDto>> byRequestLogId = new LinkedHashMap<>();
        for (ChatAttachment row : rows) {
            if (row.getRequestLogId() == null) {
                continue;
            }
            byRequestLogId
                    .computeIfAbsent(row.getRequestLogId(), id -> new ArrayList<>())
                    .add(toDto(row));
        }
        return byRequestLogId;
    }

    private static void assertRequestLogOwnership(
            RequestLog requestLog,
            String tenantCode,
            String userId,
            String sessionId
    ) {
        Session session = requestLog.getSession();
        if (session == null
                || session.getTenant() == null
                || !tenantCode.equals(session.getTenant().getTenantCode())
                || session.getUser() == null
                || !userId.equals(session.getUser().getUsername())
                || !sessionId.equals(session.getSessionId())) {
            throw new OpenAIException("Not authorized for this messageId", 403);
        }
    }

    private static void assertPreparationOwnership(
            ChatMessagePreparation preparation,
            String tenantCode,
            String userId,
            String sessionId
    ) {
        if (!tenantCode.equals(preparation.getTenantCode())
                || !userId.equals(preparation.getUserId())
                || !sessionId.equals(preparation.getSessionId())) {
            throw new OpenAIException("Not authorized for this messageId", 403);
        }
    }

    private void validateAttachmentRefs(ChatRequest request) {
        List<ChatAttachmentRef> refs = request.getAttachments() == null
                ? List.of()
                : request.getAttachments();
        if (refs.size() > MAX_ATTACHMENTS) {
            throw new OpenAIException("Too many attachments. Maximum allowed is " + MAX_ATTACHMENTS + ".", 400);
        }

        int imageCount = countImages(request);
        if (refs.size() > imageCount && imageCount > 0) {
            throw new OpenAIException(
                    "attachments count cannot exceed images count",
                    400
            );
        }

        Set<Integer> orders = new HashSet<>();
        Set<String> pids = new HashSet<>();
        for (int i = 0; i < refs.size(); i++) {
            ChatAttachmentRef ref = refs.get(i);
            if (ref == null) {
                throw new OpenAIException("attachments entries must not be null", 400);
            }
            String pid = requireNonBlank(ref.getIdmPid(), "idmPid");
            if (!pids.add(pid)) {
                throw new OpenAIException("Duplicate idmPid in attachments", 400);
            }
            requireNonBlank(ref.getFileName(), "fileName");
            String mime = normalizeMime(requireNonBlank(ref.getMimeType(), "mimeType"));
            if (!ALLOWED_MIME.contains(mime)) {
                throw new OpenAIException(
                        "Unsupported attachment mimeType. Allowed: image/png, image/jpeg, image/webp.",
                        400
                );
            }
            int order = ref.getImageOrder() != null ? ref.getImageOrder() : i;
            if (order < 0 || order > 2) {
                throw new OpenAIException("imageOrder must be between 0 and 2", 400);
            }
            if (!orders.add(order)) {
                throw new OpenAIException("Duplicate imageOrder in attachments", 400);
            }
            if (imageCount > 0) {
                List<ImageAttachmentRequest> images = request.getImages();
                if (images != null && order < images.size()) {
                    ImageAttachmentRequest img = images.get(order);
                    if (img != null
                            && isPresent(img.getImageFileName())
                            && isPresent(ref.getFileName())
                            && !img.getImageFileName().trim().equals(ref.getFileName().trim())) {
                        throw new OpenAIException(
                                "attachments[" + order + "].fileName does not match images[" + order + "]",
                                400
                        );
                    }
                }
            }
            if (ref.getFileSize() != null && ref.getFileSize() < 0) {
                throw new OpenAIException("fileSize must be non-negative", 400);
            }
        }
    }

    private List<ChatAttachmentRef> normalizeRefs(ChatRequest request) {
        validateAttachmentRefs(request);
        List<ChatAttachmentRef> refs = request.getAttachments() == null
                ? List.of()
                : request.getAttachments();
        List<ChatAttachmentRef> normalized = new ArrayList<>(refs.size());
        for (int i = 0; i < refs.size(); i++) {
            ChatAttachmentRef src = refs.get(i);
            ChatAttachmentRef copy = new ChatAttachmentRef();
            copy.setIdmPid(src.getIdmPid().trim());
            copy.setFileName(src.getFileName().trim());
            copy.setMimeType(normalizeMime(src.getMimeType()));
            copy.setFileSize(src.getFileSize());
            copy.setImageOrder(src.getImageOrder() != null ? src.getImageOrder() : i);
            normalized.add(copy);
        }
        normalized.sort((a, b) -> Integer.compare(a.getImageOrder(), b.getImageOrder()));
        return normalized;
    }

    private ChatAttachment upsert(ChatRequest request, String messageId, ChatAttachmentRef ref) {
        Optional<ChatAttachment> existing = attachmentRepository.findByTenantCodeAndIdmPid(
                request.getTenantCode(),
                ref.getIdmPid()
        );
        ChatAttachment row = existing.orElseGet(ChatAttachment::new);
        if (existing.isPresent()
                && row.getRequestLogId() != null
                && !messageId.equals(row.getMessageId())) {
            throw new OpenAIException("idmPid is already linked to another message", 409);
        }
        row.setTenantCode(request.getTenantCode());
        row.setUserId(request.getUserId());
        row.setSessionId(request.getSessionId());
        row.setMessageId(messageId);
        row.setIdmPid(ref.getIdmPid());
        row.setFileName(ref.getFileName());
        row.setMimeType(ref.getMimeType());
        row.setFileSize(ref.getFileSize());
        row.setImageOrder(ref.getImageOrder());
        row.setUploadStatus(ChatAttachment.STATUS_UPLOADED);
        return attachmentRepository.save(row);
    }

    private ChatAttachmentDto toDto(ChatAttachment row) {
        return new ChatAttachmentDto(
                row.getId(),
                row.getIdmPid(),
                row.getFileName(),
                row.getMimeType(),
                row.getFileSize(),
                row.getImageOrder(),
                row.getUploadStatus()
        );
    }

    private static int countImages(ChatRequest request) {
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            return request.getImages().size();
        }
        if (isPresent(request.getImageBase64())
                || isPresent(request.getImageMediaType())
                || isPresent(request.getImageFileName())) {
            return 1;
        }
        return 0;
    }

    private static String normalizeMime(String mimeType) {
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalized)) {
            return "image/jpeg";
        }
        return normalized;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String requireNonBlank(String value, String field) {
        if (!isPresent(value)) {
            throw new OpenAIException(field + " is required", 400);
        }
        return value.trim();
    }
}
