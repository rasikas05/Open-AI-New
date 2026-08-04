# Business Information Detection Specification

**Version:** 1.0  
**Status:** Frozen baseline — specification only (no runtime implementation in this phase)

This document explains **how** the application would determine that a span of information is BDI, PII, BFI, OMD, etc., by consulting existing catalogs and the [Global Information Classification Catalog](01-global-information-classification.md). It does **not** change Live Lex, M3 execution, or current sanitization behaviour.

---

## 1. Purpose

Detect **business information** (identifiers, financial values, operational codes, technical payloads, UDFs, personal data tied to M3 fields) so a **future** implementation can apply [LLM Exposure Policy](02-business-data-protection-policy.md) to **LLM-bound content** only.

Personal PII detection via Amazon Comprehend and Microsoft Presidio remains in place for general personal spans. This specification covers **M3-aware** information meaning.

---

## 2. Inputs

| Input | Source | Use |
|-------|--------|-----|
| User utterance (original) | Chat request | Keyword + value candidate extraction |
| Known Lex slots / SearchCriteria | Live fulfillment | Direct code → classification lookup |
| M3 field codes | MI responses, returncols | Direct code → classification lookup |
| Information-request codes | `RequestedInformationResolver` / catalogs | Appendix A of classification catalog |
| Comprehend/Presidio entities | Existing PII pipeline | Personal spans (parallel track) |

---

## 3. Existing components to reuse (do not replace)

| Component | Role in detection |
|-----------|-------------------|
| `SearchFieldCatalog` | Lex slot ↔ M3 field + keywords/aliases |
| `FieldDefinitionRegistry` / `FieldRole` | Format and role hints (PARTY, ORDER_NUMBER, PERSON, …) |
| `ApiFieldCatalog` | Return columns per MI |
| `InformationRequestCatalog` | Concept codes + utterance patterns |
| `RequestedInformationResolver` | Business groups; `BUSINESS_GROUP_BY_M3_FIELD` |
| `KeywordUtteranceRepairRule` / `SlotKeywordRegistry` | Keyword + following token extraction |
| `CunoValueNormalizer`, `SearchValueFormatter`, `ProgramIdDetector` | Value-shape specialists |
| `ComprehendAnonymizationService` / `PresidioService` | Personal PII (unchanged) |
| `IntentApiCatalog` | Intent routing only — **not** a classification authority |

Business execution catalogs remain the source of truth for **which** fields apply to an intent. The classification catalog is the source of truth for **how** those fields may appear in LLM-bound content.

---

## 4. Resolution order (conceptual)

```text
1. If a known M3 field code or information-request code is present
     → Look up Global Information Classification Catalog
     → Return Category + LLM Exposure Policy + Placeholder Type

2. Else if a label/keyword maps to a field via SearchFieldCatalog / InformationRequestCatalog
     → Resolve to code
     → Same as step 1

3. Else if only a value shape is available (optional fallback)
     → May suggest candidate roles (e.g. CUNO-like) for REVIEW
     → Must not silently ALLOW

4. Else
     → Unclassified
     → LLM Exposure Policy = BLOCK
     → Requires Classification (Decision Log)
     → Do NOT auto-assign BCI
```

### Unclassified handling

```text
Unknown code / unrecognized span
  → Temporary classification: Unclassified
  → BLOCK for LLM-bound content
  → Requires Classification
```

Unclassified is **not** a business meaning; it is a fail-closed holding state.

---

## 5. Parallel tracks

| Track | Handles | Outcome for LLM-bound content (future) |
|-------|---------|----------------------------------------|
| Comprehend + Presidio | Personal PII shapes (name, email, phone, …) | Align with PII REPLACE/MASK conventions |
| Business Information Detection | M3 codes / keywords / catalog rows | Apply row’s LLM Exposure Policy |
| Live Lex / M3 | Slots and MI parameters | **Always original values** — no stripping |

Conflict principle: Live path never loses real business values. LLM path applies policy.

---

## 6. Future wiring default (not implemented in V1.0)

When implementation begins (separate plan):

1. Keep `originalUserText` for Lex / Live M3.
2. When building **LLM-bound** prompts or context, resolve detected codes/spans via this specification.
3. Apply ALLOW / MASK / REPLACE / BLOCK / REVIEW per catalog row.
4. Use semantic Placeholder Type; token format from configuration.
5. Do not modify `SearchFieldCatalog` / `ApiFieldCatalog` seeds to “encode” protection — consult the classification catalog instead.

Extension points already present in the architecture (for future design only): before/after Comprehend, between Comprehend and Presidio, after sanitize returns, before LLM prompt build. **No wiring choice is executed in this documentation phase.**

---

## 7. Coverage matrix expectation

Every Search Criteria and Available Information field listed for CO / PO / MO / DO / Customer Basic / Customer Financial in V1.0 must resolve via a row in [01-global-information-classification.md](01-global-information-classification.md). Appendix B of that document is the checklist.

---

## 8. Non-goals

- No Java `FieldClassificationCatalog` or pipeline code in this phase.
- No change to Intent routing, Lex slot collection, or M3 execution.
- No Custom NER model requirement for V1.0 design.
- No API-specific reclassification.

---

## 9. Related documents

| Document | Role |
|----------|------|
| [01-global-information-classification.md](01-global-information-classification.md) | Catalog rows |
| [02-business-data-protection-policy.md](02-business-data-protection-policy.md) | Policy and invariants |
| [04-decision-log.md](04-decision-log.md) | Why classifications exist |
| [README.md](README.md) | Compatibility principles |
