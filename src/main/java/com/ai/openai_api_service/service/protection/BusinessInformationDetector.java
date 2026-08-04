package com.ai.openai_api_service.service.protection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metadata-driven detector: matches catalog keywords/aliases + following value token.
 * No per-entity hard-coded branches — new entities are catalog rows.
 *
 * <p>Execution order (Decision Log):
 * <ol>
 *   <li>Longest keyword first</li>
 *   <li>Connector / separator skipping</li>
 *   <li>Capture candidate (optional simple parentheses)</li>
 *   <li>Reserved-word rejection (connectors ∪ catalog keyword/alias tokens)</li>
 *   <li>Shape validation</li>
 *   <li>Overlap resolution</li>
 * </ol>
 */
@Component
public class BusinessInformationDetector {

    private static final Logger log = LoggerFactory.getLogger(BusinessInformationDetector.class);

    /** Fixed match marker — deterministic match/no-match; not confidence bands. */
    private static final double KEYWORD_CONFIDENCE = 0.9;
    private static final Pattern VALUE_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9@._+\\-]*");

    private final List<CompiledRule> rules;
    private final Set<String> reservedValueTokens;
    private final Map<String, ShapeMeta> shapeMetaByCode;
    private final ValueShapeValidator valueShapeValidator;
    private final List<KeywordProbe> keywordProbes;

    public BusinessInformationDetector(
            FieldClassificationCatalog catalog,
            ValueShapeValidator valueShapeValidator
    ) {
        this.valueShapeValidator = valueShapeValidator;
        List<CompiledRule> compiled = new ArrayList<>();
        List<KeywordProbe> probes = new ArrayList<>();
        Set<String> reserved = new HashSet<>(DetectionGrammar.CONNECTOR_WORDS);
        Map<String, ShapeMeta> shapes = new HashMap<>();
        for (FieldClassification row : catalog.all()) {
            shapes.put(row.code(), new ShapeMeta(row.valueShapeKey(), row.maxLength(), row.characterSet()));
            for (String keyword : row.detectionKeywords()) {
                compiled.add(compile(row.code(), keyword, false));
                addReservedTokens(reserved, keyword);
                probes.add(new KeywordProbe(row.code(), keyword, false, compileProbe(keyword)));
            }
            for (String alias : row.detectionAliases()) {
                compiled.add(compile(row.code(), alias, true));
                addReservedTokens(reserved, alias);
                probes.add(new KeywordProbe(row.code(), alias, true, compileProbe(alias)));
            }
        }
        compiled.sort(Comparator.comparingInt(CompiledRule::keywordLength).reversed());
        probes.sort(Comparator.comparingInt((KeywordProbe p) -> p.phrase().length()).reversed());
        this.rules = List.copyOf(compiled);
        this.keywordProbes = List.copyOf(probes);
        this.reservedValueTokens = Set.copyOf(reserved);
        this.shapeMetaByCode = Map.copyOf(shapes);
    }

    public List<DetectedSpan> detect(String text) {
        return detectWithStats(text).spans();
    }

    /**
     * Same as {@link #detect(String)} but also returns internal DEBUG counters for tests.
     */
    DetectionResult detectWithStats(String text) {
        DetectionStats stats = new DetectionStats();
        if (text == null || text.isBlank()) {
            stats.setFinalSpans(0);
            return new DetectionResult(List.of(), stats);
        }

        List<DetectedSpan> candidates = new ArrayList<>();
        for (CompiledRule rule : rules) {
            stats.incrementRulesEvaluated();
            Matcher matcher = rule.pattern().matcher(text);
            while (matcher.find()) {
                String value = matcher.group(1);
                if (value == null || value.isBlank()) {
                    continue;
                }
                stats.incrementMatchesFound();
                boolean usedParen = matcher.group(0).indexOf('(') >= 0;
                boolean usedConnector = hasConnectorBridge(
                        matcher.group(0),
                        rule.matchedKeyword(),
                        matcher.start(1) - matcher.start()
                );
                String lower = value.toLowerCase(Locale.ROOT);
                if (reservedValueTokens.contains(lower) || DetectionGrammar.isConnector(lower)) {
                    stats.incrementRejectedByReserved();
                    stats.recordMiss(rule.matchedKeyword(), DetectionMissReason.RESERVED_VALUE);
                    continue;
                }
                ShapeMeta shape = shapeMetaByCode.get(rule.code());
                if (shape == null
                        || !valueShapeValidator.isValid(
                        shape.shapeKey(), value, shape.maxLength(), shape.characterSet())) {
                    stats.incrementRejectedByShape();
                    stats.recordMiss(rule.matchedKeyword(), DetectionMissReason.SHAPE_INVALID);
                    continue;
                }
                int valueStart = matcher.start(1);
                int valueEnd = matcher.end(1);
                DetectionMatchBand band = resolveBand(rule.aliasMatch(), rule.matchedKeyword(), usedConnector, usedParen);
                candidates.add(new DetectedSpan(
                        valueStart,
                        valueEnd,
                        rule.code(),
                        KEYWORD_CONFIDENCE,
                        rule.matchedKeyword(),
                        band,
                        rule.aliasMatch()
                ));
                stats.incrementBand(band);
            }
        }
        List<DetectedSpan> finalSpans = resolveOverlaps(candidates);
        stats.setFinalSpans(finalSpans.size());
        if (finalSpans.isEmpty()) {
            classifyMissesWhenNoSpans(text, stats);
        }
        if (log.isDebugEnabled()) {
            log.debug("BusinessInformationDetector inputLength={} statistics: {}", text.length(), stats);
            if (!stats.misses().isEmpty()) {
                log.debug("BusinessInformationDetector misses: {}", stats.misses());
            }
        } else if (!stats.misses().isEmpty() && log.isInfoEnabled()) {
            // keyword + reason only — no raw IDs
            log.info(
                    "Detection miss | keywords={} | reasons={}",
                    stats.misses().stream().map(MissHint::keyword).distinct().toList(),
                    stats.misses().stream().map(MissHint::reason).distinct().toList()
            );
        }
        return new DetectionResult(finalSpans, stats);
    }

    private void classifyMissesWhenNoSpans(String text, DetectionStats stats) {
        if (stats.misses().isEmpty()) {
            for (KeywordProbe probe : keywordProbes) {
                Matcher km = probe.pattern().matcher(text);
                if (!km.find()) {
                    continue;
                }
                int after = km.end();
                String tail = text.substring(after).stripLeading();
                if (valueAppearsBeforeKeyword(text, km.start())) {
                    stats.recordMiss(probe.phrase(), DetectionMissReason.VALUE_BEFORE_KEYWORD);
                    continue;
                }
                if (tail.isEmpty()) {
                    stats.recordMiss(probe.phrase(), DetectionMissReason.VALUE_MISSING);
                    continue;
                }
                if (looksLikeConnectorInvalid(tail)) {
                    stats.recordMiss(probe.phrase(), DetectionMissReason.CONNECTOR_INVALID);
                    continue;
                }
                if (!VALUE_TOKEN.matcher(tail).lookingAt()
                        && !(tail.startsWith("(") && VALUE_TOKEN.matcher(tail.substring(1)).lookingAt())) {
                    stats.recordMiss(probe.phrase(), DetectionMissReason.VALUE_MISSING);
                }
            }
        }

        // Inverted order (2B backlog signal) — record even if other miss reasons exist
        Matcher inv = Pattern.compile(
                "\\b(" + VALUE_TOKEN.pattern() + ")\\s+(?:this\\s+)?(customer|supplier|order|warehouse|facility)\\b",
                Pattern.CASE_INSENSITIVE
        ).matcher(text);
        if (inv.find() && containsDigit(inv.group(1))) {
            boolean already = stats.misses().stream()
                    .anyMatch(m -> m.reason() == DetectionMissReason.VALUE_BEFORE_KEYWORD);
            if (!already) {
                stats.recordMiss(inv.group(2).toLowerCase(Locale.ROOT), DetectionMissReason.VALUE_BEFORE_KEYWORD);
            }
        }
    }

    private static boolean looksLikeConnectorInvalid(String tail) {
        String t = tail.toLowerCase(Locale.ROOT);
        if (t.startsWith(",") || t.startsWith(";") || t.startsWith("-")) {
            return true;
        }
        return t.startsWith("should ")
                || t.startsWith("that ")
                || t.startsWith("which ")
                || t.startsWith("associated ")
                || t.startsWith("related ")
                || t.startsWith("belongs ");
    }

    private static boolean valueAppearsBeforeKeyword(String text, int keywordStart) {
        String before = text.substring(0, keywordStart);
        Matcher m = Pattern.compile("\\b(" + VALUE_TOKEN.pattern() + ")\\s*$").matcher(before.stripTrailing());
        if (!m.find()) {
            // look further: "... 45678 this "
            Matcher m2 = Pattern.compile("\\b(" + VALUE_TOKEN.pattern() + ")\\s+(?:this\\s+)?$").matcher(before);
            if (!m2.find()) {
                return false;
            }
            return containsDigit(m2.group(1));
        }
        return containsDigit(m.group(1));
    }

    private static boolean containsDigit(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasConnectorBridge(String fullMatch, String keyword, int valueStartInMatch) {
        if (fullMatch == null || keyword == null || valueStartInMatch <= keyword.length()) {
            return false;
        }
        String between = fullMatch.substring(keyword.length(), valueStartInMatch).trim();
        if (between.isEmpty()) {
            return false;
        }
        // Strip paren / separators — leftover letters mean connector words were used
        String stripped = between.replaceAll("[=:#()\\s]+", " ").trim();
        return !stripped.isEmpty() && stripped.chars().anyMatch(Character::isLetter);
    }

    static DetectionMatchBand resolveBand(boolean aliasMatch, String matchedKeyword, boolean usedConnector, boolean usedParen) {
        if (aliasMatch) {
            return DetectionMatchBand.ALIAS;
        }
        if (isWeakKeyword(matchedKeyword)) {
            return DetectionMatchBand.WEAK;
        }
        if (usedConnector || usedParen) {
            return DetectionMatchBand.GRAMMAR;
        }
        return DetectionMatchBand.EXACT;
    }

    static boolean isWeakKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        String lower = keyword.trim().toLowerCase(Locale.ROOT);
        return lower.contains("reference")
                || lower.endsWith(" ref")
                || lower.endsWith(" code");
    }

    private static void addReservedTokens(Set<String> reserved, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        for (String part : keyword.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!part.isBlank()) {
                String token = part.replaceAll("[#:=]+$", "");
                if (!token.isBlank()) {
                    reserved.add(token);
                }
            }
        }
    }

    private static CompiledRule compile(String code, String keyword, boolean aliasMatch) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Blank detection keyword for code " + code);
        }
        char last = trimmed.charAt(trimmed.length() - 1);
        String boundaryAfterKeyword = Character.isLetterOrDigit(last) ? "\\b" : "";
        // Optional simple parentheses around value only: customer (45678)
        String patternText = "\\b" + Pattern.quote(trimmed) + boundaryAfterKeyword
                + "(?:\\s+(?:" + DetectionGrammar.connectorAlternation() + "))*"
                + "\\s*(?:" + DetectionGrammar.separatorClass() + "\\s*)?"
                + "\\(?"
                + "(" + VALUE_TOKEN.pattern() + ")"
                + "\\)?";
        Pattern pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
        return new CompiledRule(code, trimmed, trimmed.length(), pattern, aliasMatch);
    }

    private static Pattern compileProbe(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        char last = trimmed.charAt(trimmed.length() - 1);
        String boundaryAfterKeyword = Character.isLetterOrDigit(last) ? "\\b" : "";
        return Pattern.compile("\\b" + Pattern.quote(trimmed) + boundaryAfterKeyword, Pattern.CASE_INSENSITIVE);
    }

    private static List<DetectedSpan> resolveOverlaps(List<DetectedSpan> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<DetectedSpan> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparingInt(DetectedSpan::start)
                .thenComparingInt((DetectedSpan s) -> s.end() - s.start()).reversed());

        List<DetectedSpan> accepted = new ArrayList<>();
        for (DetectedSpan candidate : sorted) {
            boolean overlaps = false;
            for (DetectedSpan existing : accepted) {
                if (candidate.start() < existing.end() && candidate.end() > existing.start()) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                accepted.add(candidate);
            }
        }
        accepted.sort(Comparator.comparingInt(DetectedSpan::start));
        return List.copyOf(accepted);
    }

    private record ShapeMeta(String shapeKey, Integer maxLength, IdentifierCharacterSet characterSet) {
    }

    private record CompiledRule(
            String code,
            String matchedKeyword,
            int keywordLength,
            Pattern pattern,
            boolean aliasMatch
    ) {
    }

    private record KeywordProbe(String code, String phrase, boolean aliasMatch, Pattern pattern) {
    }

    record MissHint(String keyword, DetectionMissReason reason) {
    }

    record DetectionResult(List<DetectedSpan> spans, DetectionStats stats) {
    }
}
