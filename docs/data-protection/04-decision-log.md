# Decision Log — Business Data Protection

**Version:** 1.0  
**Status:** Frozen baseline  
**Purpose:** Append-only record of classification and policy decisions so future teams can see *why* a code is protected the way it is.

---

## How to add an entry

| Field | Description |
|-------|-------------|
| Decision # | Monotonic integer |
| Date | YYYY-MM-DD |
| Subject | Short title |
| Decision | What was decided |
| Reason | Why |
| Owner | Role or team (e.g. Architecture, Security, Business) |

Classification or LLM Exposure Policy changes **require** a new entry (Architectural Invariant #7).

---

## V1.0 seed decisions

### Decision #1

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Protection basis |
| **Decision** | Protection is based on the **meaning of information**, not on API, Intent, or Program. |
| **Reason** | Enterprise governance requires one global meaning per code so every future MI inherits the same policy. |
| **Owner** | Architecture |

### Decision #2

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Classification vs implementation |
| **Decision** | **Classification is immutable. Implementation is replaceable.** Changing LLM vendor (OpenAI, Claude, Bedrock, Gemini, internal) does not change the classification catalog. |
| **Reason** | Keeps governance vendor-neutral and avoids rework when the LLM stack changes. |
| **Owner** | Architecture |

### Decision #3

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Employee identifiers |
| **Decision** | `RESP`, `SMCD`, `BUYE`, `PURC` are classified as **PII** (employee / person identifiers). LLM Exposure Policy: **REPLACE**. |
| **Reason** | Internal privacy stance: employee-related identifiers are personally linked even when used as M3 codes. |
| **Owner** | Security + Business |

### Decision #4

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Operational location and status codes |
| **Decision** | `FACI`, `WHLO`, `DIVI`, status fields (`ORST`, `ORSL`, `PUST`, `PUSL`, `WHST`, `TRSH`, `TRSL`, etc.), and `CUCD` are **OMD**. LLM Exposure Policy: **ALLOW** (unless a later decision overrides). |
| **Reason** | Operational context that does not by itself identify a customer, order, or person. |
| **Owner** | Architecture + Business |

### Decision #5

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Key string |
| **Decision** | `KSTR` is **TECH**, not SYS. Default LLM Exposure Policy: **BLOCK**. |
| **Reason** | Key string payloads may embed customer numbers, order numbers, or concatenated business keys. |
| **Owner** | Architecture + Security |

### Decision #6

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | SYS and TECH exposure |
| **Decision** | SYS and TECH fields are **never automatically ALLOW**. Each row needs an explicit LLM Exposure Policy. |
| **Reason** | System/technical fields can still leak identifiers or internal structure. |
| **Owner** | Security |

### Decision #7

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Unknown / new codes |
| **Decision** | Unknown codes are **Unclassified** (temporary). LLM Exposure Policy: **BLOCK**. Requires classification via this log — **not** auto-assigned BCI. |
| **Reason** | Assigning Business Confidential prematurely invents meaning we do not have. Fail closed until reviewed. |
| **Owner** | Architecture + Security |

### Decision #8

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | LLM Exposure Policy vocabulary |
| **Decision** | Use `ALLOW` \| `MASK` \| `REPLACE` \| `BLOCK` \| `REVIEW` instead of boolean “send to OpenAI”. |
| **Reason** | Future-proofs handling (e.g. typed replace vs omit) and stays vendor-neutral for any LLM. |
| **Owner** | Architecture |

### Decision #9

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Placeholder Type |
| **Decision** | The catalog stores a **semantic Placeholder Type** (e.g. Customer Number, Email). Literal token format (`<ORDER_NUMBER>`, `[ORDER]`, etc.) is an implementation concern. |
| **Reason** | Different deployments may choose different token syntax without changing classification. |
| **Owner** | Architecture |

### Decision #10

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Business vs protection |
| **Decision** | Business functionality and information protection are **independent concerns**. A field may be used fully in Live search/Lex/MI while being protected for LLM-bound content. |
| **Reason** | Preserves existing M3 assistant behaviour while allowing governance of LLM prompts. |
| **Owner** | Architecture |

### Decision #11

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Medium-confidence operational fields |
| **Decision** | `FRE1` (Statistics Identity) and `MEAL` (Valid media) are OMD with Confidence **Medium** and LLM Exposure Policy **REVIEW** until business confirms. |
| **Reason** | Tenant-specific or ambiguous meaning; should not be auto-ALLOW without validation. |
| **Owner** | Business |

### Decision #12

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Live path |
| **Decision** | Live Lex and M3 execution always use **original** business values. Classification does not strip Lex slots or MI parameters. |
| **Reason** | Live automation requires real identifiers; protection applies to LLM-bound content only. |
| **Owner** | Architecture |

### Decision #13

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Primary enforcement point |
| **Decision** | Business information protection is enforced at **`OpenAIService` (LLM egress)**. `ComprehendChatService` remains orchestrator only. |
| **Reason** | Live/Lex never enter OpenAIService; all LLM callers inherit policy; separation of routing vs protection. |
| **Owner** | Architecture |

### Decision #14

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Classification vs runtime policy |
| **Decision** | Protection policies are evaluated at runtime based on `ProtectionContext`, but underlying information classification remains unchanged. |
| **Reason** | Keeps governance stable while allowing purpose-specific application (e.g. ANSWER vs REWRITE). |
| **Owner** | Architecture |

### Decision #15

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | REWRITE BDI override |
| **Decision** | For `Purpose.REWRITE` only, BDI (and OMD) may **ALLOW** so retrieval queries remain effective. Global BDI classification remains REPLACE for ANSWER. |
| **Reason** | Retrieval-specific override; must not be reused outside query rewriting without a new Decision Log entry. |
| **Owner** | Architecture |

### Decision #16

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Metadata-driven detector |
| **Decision** | `BusinessInformationDetector` is metadata-driven; business entity knowledge lives in catalogs, not hard-coded per-entity rules. |
| **Reason** | Adding entities should normally be catalog + tests only, for long-term maintainability. |
| **Owner** | Architecture |

### Decision #17

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | V1 catalog co-location |
| **Decision** | V1 uses a single `FieldClassificationCatalog` co-locating classification and detection metadata. Catalog returns `Optional` (no invented Unclassified rows). Future split into classification vs detection catalogs is allowed without changing the protection pipeline. |
| **Reason** | Simpler V1; preserves upgrade path for multilingual detection or independent governance lifecycles. |
| **Owner** | Architecture |

### Decision #18

| | |
|--|--|
| **Date** | 2026-07-31 |
| **Subject** | Facade naming |
| **Decision** | The OpenAIService dependency is `BusinessInformationProtectionService` (pipeline facade), not a thin “protector” name. |
| **Reason** | Reflects orchestration of detect → classify → policy → format. |
| **Owner** | Architecture |

---

### Decision #19

| | |
|--|--|
| **Date** | 2026-08-03 |
| **Subject** | Phase 7A approval (constrained) |
| **Decision** | Phase 7A is approved for implementation: `service.protection` + `OpenAIService` only; feature flag default **false**; Live/Lex/M3 and Search catalogs untouched; no DB/API contract changes. Phase 7B (Business→PII→OpenAI→Restore orchestration) is **not** approved in the same phase. |
| **Reason** | Low blast radius validates the protection framework before orchestration/persistence changes. |
| **Owner** | Architecture |

### Decision #20

| | |
|--|--|
| **Date** | 2026-08-03 |
| **Subject** | Temporary PII→Business order (7A) |
| **Decision** | Phase 7A keeps Business Protection as an **additive stage at OpenAI egress** (after upstream PII). This is an **implementation constraint for this phase and not the target end-state architecture**. Target remains Business → PII → OpenAI → Restore (Phase 7B). |
| **Reason** | Avoids touching `ComprehendChatService` in 7A while documenting that PII-first can mask identifiers (e.g. `45678`→`[NUMBER]`) before business detection. |
| **Owner** | Architecture |

### Decision #21

| | |
|--|--|
| **Date** | 2026-08-03 |
| **Subject** | ProtectionContext vs ProtectionSession |
| **Decision** | Split runtime types: **`ProtectionContext`** = execution metadata (`purpose`, enabled snapshot, `policyVersion`, `debug`, optional tenant). **`ProtectionSession`** = mutable request state (`originalText`, `businessProtectedText`, `piiSanitizedText`, `replacementMap`, `actions`). Do not mix them in one type. |
| **Reason** | “Context” is immutable invocation settings; texts and maps evolve through the pipeline. Prevents Phase 7B technical debt. |
| **Owner** | Architecture |

### Decision #22

| | |
|--|--|
| **Date** | 2026-08-03 |
| **Subject** | Phase 7A non-persistence of replacement map |
| **Decision** | `replacementMap` may be populated at runtime for forward compatibility. Phase 7A does **not** persist it, does **not** restore placeholders to the UI, and does **not** add `request_logs` columns. |
| **Reason** | Persistence and restore are Phase 7B / separate persistence phases with different blast radius. |
| **Owner** | Architecture |

### Decision #23

| | |
|--|--|
| **Date** | 2026-08-03 |
| **Subject** | Detector precision hardening (execution order + operational debug) |
| **Decision** | `BusinessInformationDetector` hardens precision via `DetectionGrammar` (connectors + separators), catalog-derived reserved value tokens, and a tiny `ValueShapeValidator` keyed by existing `valueShapeKey`. Execution order is: (1) longest keyword first, (2) connector/separator skipping, (3) capture candidate, (4) reserved-word rejection, (5) shape validation, (6) overlap resolution. `DetectedSpan` stores `matchedKeyword` for alias debugging. Internal `DetectionStats` may be logged at DEBUG / asserted in tests only — not API or DB. Detection remains deterministic match/no-match; do **not** introduce HIGH/MEDIUM/LOW confidence bands. |
| **Reason** | Fixes Phase 7A DEV false positives (`SMCD=is`, `ORNO=for`, `CUNO=how`, `customer supplier`) without entity `if` branches or Phase 7B / OpenAI redesign. |
| **Owner** | Architecture |

### Decision #24

| | |
|--|--|
| **Date** | 2026-08-03 |
| **Subject** | Phase 7B Protection Orchestration & Audit |
| **Decision** | Phase 7B is approved as the full Protection Orchestration & Audit phase for the RAG/OpenAI path: Business → PII → Rewrite/Retrieval/Grounded → (later) Restore + API/DB/logging. Delivered in milestones **7B.1** (orchestration) → **7B.2** (restore) → **7B.3** (API) → **7B.4** (persistence) → **7B.5** (logging). Live/Lex/M3 remain on original IDs. Decision #20 temporary PII→Business egress order is **superseded for RAG**. |
| **Reason** | 7A proved components but PII-first destroyed business IDs before detection; observability of the business stage was missing. |
| **Owner** | Architecture |

### Decision #25

| | |
|--|--|
| **Date** | 2026-08-03 |
| **Subject** | Routing before Business Protection |
| **Decision** | Routing always executes on the **original** request. Business Protection begins only after `route=rag`. Live/Lex must never consume business placeholders. BIP must never participate in routing decisions. |
| **Reason** | Lex/M3 require real identifiers; routing must not depend on protected text. |
| **Owner** | Architecture |

### Decision #26

| | |
|--|--|
| **Date** | 2026-08-03 |
| **Subject** | ProtectionSession owns the protection pipeline |
| **Decision** | After `route=rag`, protection stages exchange **`ProtectionSession`**, not naked strings between BIP and PII. |
| **Reason** | Prevents spaghetti and lost stage visibility. |
| **Owner** | Architecture |

### Decision #27

| | |
|--|--|
| **Date** | 2026-08-03 |
| **Subject** | Canonical OpenAI-bound accessor |
| **Decision** | Rewrite, grounded answer, gap-fill, and future LLM features obtain prompt text exclusively via **`ProtectionSession.textForLlm()`**. |
| **Reason** | One rule for all LLM entry points; avoids picking the wrong intermediate field. |
| **Owner** | Architecture |

### Decision #28

| | |
|--|--|
| **Date** | 2026-08-03 |
| **Subject** | Single mutation owner for protection text |
| **Decision** | Only protection services may mutate `businessProtectedText` / `piiSanitizedText` on the session. Rewrite, retrieval, OpenAIService, gap-fill, and prompt builders are **read-only** consumers of `textForLlm()`. |
| **Reason** | Prevents silent rewrite of the protected prompt by LLM-adjacent code. |
| **Owner** | Architecture |

---

## Future entries

Append new decisions below this line. Do not edit historical entries except for typographical corrections; supersede with a new Decision # instead.
