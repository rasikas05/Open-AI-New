package com.ai.openai_api_service.m3.controller;

import com.ai.openai_api_service.m3.dto.M3FunctionResponse;
import com.ai.openai_api_service.m3.service.M3ApiClient;
import com.ai.openai_api_service.m3.service.M3ApiClient.M3ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@RestController
@RequestMapping("/api/m3")
public class M3FunctionController {

    private static final Logger log = LoggerFactory.getLogger(M3FunctionController.class);

    private final M3ApiClient m3ApiClient;

    public M3FunctionController(M3ApiClient m3ApiClient) {
        this.m3ApiClient = m3ApiClient;
    }

    @GetMapping("/function/{fnid}")
    public ResponseEntity<?> getFunction(
            @PathVariable("fnid") String fnid,
            @RequestParam(value = "cono", required = false) Integer cono,
            @RequestParam(value = "divi", required = false) String divi
    ) {
        try {
            Map<String, Object> record = new HashMap<>();
            record.put("FNID", fnid);
            if (divi != null && !divi.isBlank()) {
                record.put("DIVI", divi);
            }

            JsonNode resp;
            if (cono != null) {
                resp = m3ApiClient.callMi("MNS110MI", "Get", record, cono, 1);
            } else {
                resp = m3ApiClient.callMi("MNS110MI", "Get", record);
            }

            log.info("Full M3 API Response: {}", resp);

            JsonNode first = extractFirstRecord(resp);
            if (first == null || first.isMissingNode()) {
                log.warn("No record found in M3 response for FNID: {}", fnid);
                return ResponseEntity.notFound().build();
            }

            log.info("Extracted record for FNID {}: {}", fnid, first);

            logAvailableFields(first);

            String functionId = getTextSafe(first, "FNID");
            String programName = getTextSafe(first, "PGNM");
            String functionGroup = getTextSafe(first, "SEID");
            String functionCategory = getTextSafe(first, "FNT3");
            String componentGroup = getTextSafe(first, "APP3");
            String authorityRequired = getTextSafe(first, "AUTY");
            String changedBy = getTextSafe(first, "CHID");
            String changeDate = getTextSafe(first, "LMDT");

            log.info("Extracted fields - FNID: {}, PGNM: {}, SEID: {}, FNT3: {}, APP3: {}, AUTY: {}, CHID: {}, LMDT: {}",
                    functionId, programName, functionGroup, functionCategory, componentGroup, authorityRequired, changedBy, changeDate);

            M3FunctionResponse response = new M3FunctionResponse(
                    functionId,
                    programName,
                    functionGroup,
                    functionCategory,
                    componentGroup,
                    authorityRequired,
                    changedBy,
                    changeDate
            );

            return ResponseEntity.ok(response);
        } catch (M3ApiException e) {
            log.warn("M3 API error: {}", e.getMessage());
            if (e.getStatusCode() == 404) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error while fetching function {}: {}", fnid, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal error"));
        }
    }

    private JsonNode extractFirstRecord(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return null;
        }

        // M3 API structure: results -> [0] -> records -> [0]
        if (root.has("results") && root.get("results").isArray() && root.get("results").size() > 0) {
            JsonNode result = root.get("results").get(0);
            if (result.has("records") && result.get("records").isArray() && result.get("records").size() > 0) {
                return result.get("records").get(0);
            }
        }

        // Common structure: transactions -> [0] -> records -> [0]
        if (root.has("transactions") && root.get("transactions").isArray() && root.get("transactions").size() > 0) {
            JsonNode tx = root.get("transactions").get(0);
            if (tx.has("records") && tx.get("records").isArray() && tx.get("records").size() > 0) {
                return tx.get("records").get(0);
            }
            if (tx.has("record")) {
                return tx.get("record");
            }
        }

        // Some responses use transactionResponses
        if (root.has("transactionResponses") && root.get("transactionResponses").isArray() && root.get("transactionResponses").size() > 0) {
            JsonNode tr = root.get("transactionResponses").get(0);
            if (tr.has("records") && tr.get("records").isArray() && tr.get("records").size() > 0) {
                return tr.get("records").get(0);
            }
        }

        // Direct records array
        if (root.has("records") && root.get("records").isArray() && root.get("records").size() > 0) {
            return root.get("records").get(0);
        }

        // Fallback: find first child array of objects
        Iterator<JsonNode> iter = root.elements();
        while (iter.hasNext()) {
            JsonNode node = iter.next();
            if (node != null && node.isArray() && node.size() > 0 && node.get(0).isObject()) {
                return node.get(0);
            }
        }

        return null;
    }

    private String getTextSafe(JsonNode node, String field) {
        if (node == null || field == null) return null;
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        return val.asText();
    }

    private void logAvailableFields(JsonNode node) {
        if (node == null || !node.isObject()) {
            log.info("Available fields: None (not an object)");
            return;
        }
        Iterator<String> fields = node.fieldNames();
        StringBuilder sb = new StringBuilder("Available fields in M3 response: ");
        while (fields.hasNext()) {
            sb.append(fields.next()).append(", ");
        }
        log.info(sb.toString());
    }
}
