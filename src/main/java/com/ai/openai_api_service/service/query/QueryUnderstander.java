package com.ai.openai_api_service.service.query;

import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.QueryContext;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.RequestedInformationResolver;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enriches {@link QueryContext} from user utterance and Lex session attributes.
 * Does not invoke Lex or alter slot repair output.
 */
@Service
public class QueryUnderstander {

    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "\\b(?:last|top|recent|first)\\s+(\\d{1,4})\\b"
                    + "|\\b(?:show|display|list|give\\s+me|return|fetch|only)\\s+(\\d{1,4})\\s+"
                    + "(?:orders?|purchase\\s+orders?|manufacturing\\s+orders?|distribution\\s+orders?"
                    + "|customers?|results?|records?)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONTINUATION_PATTERN = Pattern.compile(
            "\\b(?:show\\s+more|next\\s+page|load\\s+more|more\\s+results?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final RequestedInformationResolver requestedInformationResolver;
    private final IntentApiCatalog intentApiCatalog;
    private final ReturnColumnCatalog returnColumnCatalog;

    public QueryUnderstander(
            RequestedInformationResolver requestedInformationResolver,
            IntentApiCatalog intentApiCatalog,
            ReturnColumnCatalog returnColumnCatalog
    ) {
        this.requestedInformationResolver = requestedInformationResolver;
        this.intentApiCatalog = intentApiCatalog;
        this.returnColumnCatalog = returnColumnCatalog;
    }

    public QueryContext enrich(
            QueryContext base,
            String userUtterance,
            Map<String, String> lexSessionAttributes
    ) {
        if (base == null) {
            return null;
        }

        List<String> requestedInformation = resolveRequestedInformation(
                base,
                userUtterance,
                lexSessionAttributes
        );
        Integer limit = parseLimit(userUtterance);
        boolean continuation = parseContinuation(userUtterance);
        List<String> returnColumns = returnColumnCatalog.columnsFor(
                base.intentName(),
                requestedInformation
        );

        return new QueryContext(
                base.intentName(),
                base.slots(),
                base.criteria(),
                requestedInformation,
                limit,
                returnColumns,
                base.positionKey(),
                base.sort(),
                base.aggregation(),
                base.filters(),
                continuation
        );
    }

    private List<String> resolveRequestedInformation(
            QueryContext base,
            String userUtterance,
            Map<String, String> lexSessionAttributes
    ) {
        if (base.intentName() == null || base.intentName().isBlank()) {
            return List.of();
        }

        boolean isSearch = intentApiCatalog.find(base.intentName())
                .map(IntentDefinition::requestType)
                .filter(type -> type == RequestType.SEARCH)
                .isPresent();

        if (isSearch) {
            return requestedInformationResolver.resolveForSearch(userUtterance, base.criteria());
        }

        return requestedInformationResolver.resolve(
                userUtterance,
                base.intentName(),
                lexSessionAttributes
        );
    }

    static Integer parseLimit(String userUtterance) {
        if (userUtterance == null || userUtterance.isBlank()) {
            return null;
        }
        Matcher matcher = LIMIT_PATTERN.matcher(userUtterance.toLowerCase(Locale.ROOT));
        if (!matcher.find()) {
            return null;
        }
        try {
            String raw = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            int value = Integer.parseInt(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean parseContinuation(String userUtterance) {
        if (userUtterance == null || userUtterance.isBlank()) {
            return false;
        }
        return CONTINUATION_PATTERN.matcher(userUtterance).find();
    }
}
