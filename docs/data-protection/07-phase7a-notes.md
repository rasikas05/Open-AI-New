# Phase 7A — Implementation Notes

**Status:** Implemented and closeout-validated under Decision Log #19–#23.  
**Contract:** [05-implementation-design.md](05-implementation-design.md)

---

## Scope

- Package `com.ai.openai_api_service.service.protection`
- Integration only in `OpenAIService` (`protectForLlm`)
- Feature flag: `business-information.protection.enabled=false` (default)

## Temporary order (not target architecture)

> Phase 7A implements Business Protection as an additive stage at OpenAI egress. This is an implementation constraint for this phase and not the target end-state architecture.

```text
Original → PII → Route
  Live: original (never calls protector)
  RAG: sanitized → OpenAIService → [flag ON] Business → OpenAI
```

**Target (Phase 7B — not implemented):** Business → PII → OpenAI → Restore.

## Runtime types (Decision #21)

| Type | Role |
|------|------|
| `ProtectionContext` | purpose, enabled, policyVersion, debug, tenant |
| `ProtectionSession` | originalText, piiSanitizedText, businessProtectedText, replacementMap, actions |

`replacementMap` is populated for forward compatibility; **not** persisted and **not** used for UI restore in 7A.

## DEV enable

See [06-phase6-dev-enablement.md](06-phase6-dev-enablement.md). Do not commit `enabled=true` to main properties.

---

## Closeout validation (2026-08-03)

Phase 7A BIP track is **frozen**. Do not mix Lex or routing fixes into this track.

### Proven

| Check | Evidence |
|-------|----------|
| ANSWER REPLACE (OpenAI-bound text) | `BusinessInformationProtectionServiceTest.protect_answer_replacesSupplierAlphanumeric_openaiBoundText` — `supplier ABC001` → `supplier <SUPPLIER_NUMBER>` via `ProtectionSession.textForLlm()` |
| Customer REPLACE + warehouse ALLOW | `protect_answerPipeline_replacesCustomerAllowsWarehouse` |
| PII-first masks ID → no business span | `protect_answer_piiMaskedNumber_noBusinessPlaceholder` — documents Decision #20 when sanitized is `customer order [NUMBER]` |
| Flag OFF = passthrough | `ProtectForLlmFlagOffTest` + `protect_whenDisabled_returnsOriginal` |
| Detector precision | Decision #23 + `BusinessInformationDetectorTest` (connectors, reserved, shapes, regressions) |

### Known 7A limit (motivates 7B)

Live Comprehend DEV sessions showed Presidio often masking REPLACE candidates before BIP (`[NUMBER]`, `[PHONE]`, `[Name]`). Egress wiring is correct; REPLACE happy-path on RAG requires an ID that survives PII **or** Phase 7B (Business → PII).

### Next

Plan **Phase 7B** orchestration only (Business protect → PII → OpenAI; Live keeps originals). Lex utterance quality and routing remain separate later tracks.
