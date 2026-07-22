package com.ai.openai_api_service.service.query;

import com.ai.openai_api_service.model.QueryContext;
import com.ai.openai_api_service.model.SearchCriterion;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Assembles {@link QueryContext} from Lex pipeline outputs without altering slot or criteria logic.
 */
@Component
public class QueryContextAssembler {

    public QueryContext assembleSearch(
            String intentName,
            Map<String, String> validSlots,
            List<SearchCriterion> criteria
    ) {
        return QueryContext.forSearch(intentName, validSlots, criteria);
    }

    public QueryContext assembleRead(String intentName, Map<String, String> lexSlots) {
        return QueryContext.forRead(intentName, lexSlots != null ? lexSlots : Map.of());
    }
}
