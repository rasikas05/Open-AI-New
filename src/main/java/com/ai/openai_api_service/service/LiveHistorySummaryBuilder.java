package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.LiveHistoryAuditMetadata;
import com.ai.openai_api_service.model.LiveHistoryResult;
import com.ai.openai_api_service.model.M3RequestDto;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class LiveHistorySummaryBuilder {

    static final String FOOTER =
            "Live M3 results are not stored. Run the search again to retrieve the latest information.";

    public Optional<LiveHistoryResult> build(ChatResponse chatResponse) {
        if (!isLiveM3Execute(chatResponse)) {
            return Optional.empty();
        }

        String lexIntent = chatResponse.getLexIntent();
        String actionTaken = chatResponse.getActionTaken();
        Map<String, Object> params = chatResponse.getM3Request().getParams();

        if ("GetCustomer".equals(lexIntent) && "read".equals(actionTaken)) {
            return Optional.of(buildGetCustomerRead(params));
        }

        return Optional.of(buildGenericLiveSummary(lexIntent, actionTaken));
    }

    private LiveHistoryResult buildGetCustomerRead(Map<String, Object> params) {
        String cuno = stringParam(params, "CUNO");
        String headline = cuno != null
                ? "Viewed customer " + cuno + "."
                : "Viewed customer.";
        LiveHistoryAuditMetadata metadata = new LiveHistoryAuditMetadata(
                "GetCustomer",
                "Customer",
                cuno
        );
        return new LiveHistoryResult(formatSummary(headline), metadata);
    }

    private LiveHistoryResult buildGenericLiveSummary(String lexIntent, String actionTaken) {
        String headline = "Performed live M3 action.";
        LiveHistoryAuditMetadata metadata = new LiveHistoryAuditMetadata(
                lexIntent,
                null,
                null
        );
        return new LiveHistoryResult(formatSummary(headline), metadata);
    }

    private String formatSummary(String headline) {
        return headline + "\n\n" + FOOTER;
    }

    private String stringParam(Map<String, Object> params, String key) {
        if (params == null) {
            return null;
        }
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isLiveM3Execute(ChatResponse chatResponse) {
        M3RequestDto m3Request = chatResponse != null ? chatResponse.getM3Request() : null;
        return m3Request != null && m3Request.isExecute();
    }
}
