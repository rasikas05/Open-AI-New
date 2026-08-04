package com.ai.openai_api_service.service.protection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Internal detector counters for DEBUG logs and unit tests. Not exposed via API or DB.
 */
public final class DetectionStats {

    private int rulesEvaluated;
    private int matchesFound;
    private int rejectedByReserved;
    private int rejectedByShape;
    private int finalSpans;
    private final Map<DetectionMatchBand, Integer> bandCounts = new EnumMap<>(DetectionMatchBand.class);
    private final List<BusinessInformationDetector.MissHint> misses = new ArrayList<>();

    void incrementRulesEvaluated() {
        rulesEvaluated++;
    }

    void incrementMatchesFound() {
        matchesFound++;
    }

    void incrementRejectedByReserved() {
        rejectedByReserved++;
    }

    void incrementRejectedByShape() {
        rejectedByShape++;
    }

    void setFinalSpans(int finalSpans) {
        this.finalSpans = finalSpans;
    }

    void incrementBand(DetectionMatchBand band) {
        if (band == null) {
            return;
        }
        bandCounts.merge(band, 1, Integer::sum);
    }

    void recordMiss(String keyword, DetectionMissReason reason) {
        if (keyword == null || reason == null) {
            return;
        }
        misses.add(new BusinessInformationDetector.MissHint(keyword, reason));
    }

    public int rulesEvaluated() {
        return rulesEvaluated;
    }

    public int matchesFound() {
        return matchesFound;
    }

    public int rejectedByReserved() {
        return rejectedByReserved;
    }

    public int rejectedByShape() {
        return rejectedByShape;
    }

    public int finalSpans() {
        return finalSpans;
    }

    public Map<DetectionMatchBand, Integer> bandCounts() {
        return Map.copyOf(bandCounts);
    }

    public List<BusinessInformationDetector.MissHint> misses() {
        return List.copyOf(misses);
    }

    @Override
    public String toString() {
        return "Rules evaluated: " + rulesEvaluated
                + ", Matches found: " + matchesFound
                + ", Rejected by reserved words: " + rejectedByReserved
                + ", Rejected by shape: " + rejectedByShape
                + ", Final spans: " + finalSpans
                + ", Bands: " + bandCounts
                + ", Misses: " + misses.size();
    }
}
