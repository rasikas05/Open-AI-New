# Phase 7B — Protection Orchestration and Audit

**Status:** Implemented (milestones 7B.1–7B.5)  
**Decisions:** #24–#28 in [04-decision-log.md](04-decision-log.md)  
**Supersedes for RAG:** Decision #20 temporary PII→Business egress order

---

## Pipeline (RAG only)

```text
Original
  → Route (original only; BIP never participates)
  → Business Protection (flag-gated; mutates ProtectionSession)
  → PII (mutates session)
  → Rewrite / Retrieval / Grounded / Gap-fill (read-only via session.textForLlm())
  → Business Restore (replacementMap → user reply)
  → Final Response + audit fields
```

Live/Lex/M3: originals only; no BIP; no placeholders.

---

## Invariants

| # | Rule |
|---|------|
| 25 | Route on original; BIP only after `route=rag` |
| 26 | Protection stages exchange `ProtectionSession` |
| 27 | All OpenAI-bound input via `session.textForLlm()` |
| 28 | Only BIP/PII mutate protection text; LLM stages read-only |

### `textForLlm()` contract (runtime)

```java
if (piiSanitizedText != null) return piiSanitizedText;
if (businessProtectedText != null) return businessProtectedText;
return originalText;
```

**Note vs plan sketch:** When BIP flag is off, PII still runs on original and `piiSanitizedText` wins. Flag-off therefore preserves PII behavior while skipping business placeholders — not a pure “return original only” path after PII has run.

---

## Milestone map

| Milestone | Delivered in |
|-----------|----------------|
| **7B.1** Orchestration | `ComprehendChatService` RAG Business→PII; `ProtectionSession`; OpenAI overloads read `textForLlm()` |
| **7B.2** Restore | `BusinessPlaceholderRestorer`; `replyBeforeRestore` / restored `reply` |
| **7B.3** API | Additive `ChatResponse` fields (no raw IDs in entity list by default) |
| **7B.4** Persistence | Additive `request_logs` columns via `ProtectionAuditSnapshot` (placeholder metadata JSON, not full raw map) |
| **7B.5** Logging | `Protection stage \| businessApplied={} \| entityCount={} \| piiChanged={}` — no raw identifiers |

---

## Key types

- `ProtectionSession` — sole mutation surface for protected request text  
- `LlmExecutionTrace` — deferred as a dedicated type; restore artifacts live on session (`replyBeforeRestore`, `finalResponse`) for 7B.2–7B.4  
- `ProtectionAuditSnapshot` — persistence bridge  
- `BusinessProtectedEntityDto` — type + placeholder only

---

## Flag

`business-information.protection.enabled` (default **false**): when false, BIP is a no-op; PII and Live behavior unchanged.

---

## Validation reminders

| Check | Live | RAG |
|-------|------|-----|
| Route on original | Required | Required |
| Original IDs to Lex/M3 | Required | N/A |
| Business → PII | N/A | Required |
| LLM uses only `textForLlm()` | N/A | Required |
| Restore in reply | N/A | Required |
| API/DB audit additive | N/A | Required |
| Stage logs without raw IDs | N/A | Required |
