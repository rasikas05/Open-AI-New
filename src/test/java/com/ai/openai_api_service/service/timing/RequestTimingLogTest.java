package com.ai.openai_api_service.service.timing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestTimingLogTest {

    @Test
    void computeResidual_basic() {
        RequestTimingLog.Residual r = RequestTimingLog.computeResidual(14700, 27600);
        assertEquals(14700, r.measuredSumMs());
        assertEquals(27600, r.totalMs());
        assertEquals(12900, r.residualMs());
        assertEquals(46.739, r.residualPct(), 0.01);
    }

    @Test
    void phase4Accounting_closesGapWhenSpringWallAndHiddenBucketsIncluded() {
        // Historical-style phantom gap: summary used python retrieval, omitted suggestions/serialize/glue
        List<Map<String, Long>> samples = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long springRetrieval = 4200 + (i % 5) * 200L;
            long pythonRetrieval = 1800 + (i % 4) * 100L;
            long grounded = 5500 + (i % 6) * 300L;
            long rewrite = 1200 + (i % 3) * 100L;
            long pii = 200 + (i % 2) * 50L;
            long route = 150;
            long persistence = 80;
            long suggestions = 800 + (i % 5) * 100L;
            long liveHistory = 40;
            long restore = 20;
            long glue = 15;
            long serialize = 30;
            long preService = 25;
            long otherExternal = grounded + rewrite + pii + route + springRetrieval + persistence
                    + suggestions + liveHistory + restore + glue + serialize + preService;
            // wall ≈ sum of real work (no mystery)
            long total = otherExternal + (i % 3); // tiny jitter

            long oldMeasured = pii + route + rewrite + pythonRetrieval + grounded + persistence;
            RequestTimingLog.Residual oldResidual = RequestTimingLog.computeResidual(oldMeasured, total);

            long newMeasured = pii + route + rewrite + springRetrieval + grounded + persistence
                    + suggestions + liveHistory + restore + glue + serialize + preService;
            RequestTimingLog.Residual newResidual = RequestTimingLog.computeResidual(newMeasured, total);

            Map<String, Long> row = new LinkedHashMap<>();
            row.put("sample", (long) i);
            row.put("httpTaxMs", springRetrieval - pythonRetrieval);
            row.put("oldResidualMs", oldResidual.residualMs());
            row.put("newResidualMs", newResidual.residualMs());
            row.put("oldResidualPct", Math.round(oldResidual.residualPct()));
            row.put("newResidualPct", Math.round(newResidual.residualPct()));
            row.put("totalMs", total);
            samples.add(row);

            assertTrue(oldResidual.residualPct() > 15.0, "old residual should be large");
            assertTrue(newResidual.residualPct() <= 5.0, "new residual should be ≤5%, got " + newResidual.residualPct());
        }
        assertEquals(20, samples.size());
    }
}
