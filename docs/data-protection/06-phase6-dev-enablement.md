# Phase 6.5 — DEV Enablement Checklist

**Status:** Validation notes for local/DEV only.  
**Default in main config:** `business-information.protection.enabled=false` (keep until QA sign-off).

---

## 1. Enable locally (DEV only)

In your local override (env var, IDE run config, or a private `application-local.properties` **not** committed as default-true):

```properties
business-information.protection.enabled=true
```

Or:

```text
BUSINESS_INFORMATION_PROTECTION_ENABLED=true
```

(if using relaxed binding / env override for `business-information.protection.enabled`)

Restart the Spring Boot app after changing the flag.

---

## 2. Validation matrix

| Scenario | Expected when enabled | Expected when disabled (default) |
|----------|----------------------|----------------------------------|
| **Live / Lex / M3** | Original business values (CUNO, ORNO, WHLO, …) — **never** call protector | Unchanged |
| **RAG answer (`Purpose.ANSWER`)** | User question values for BDI (e.g. customer `1001`) replaced with placeholders; OMD (warehouse) ALLOW; RAG **chunk bodies** unchanged | Identical pre-Phase-6 path |
| **Gap-fill** | User question portion protected like ANSWER | Identical path |
| **Query rewrite (`Purpose.REWRITE`)** | BDI/OMD **ALLOW** (retrieval override); PII/BFI/TECH still REPLACE/BLOCK | Identical path |
| **Comprehend personal PII** | Still runs as today; business protection is additional on LLM egress only | Unchanged |

### Suggested DEV probes

1. Live: `show status for customer 1001` → Lex/M3 receives `1001`.
2. RAG/general: same utterance → LLM-bound user text contains `<CUSTOMER_NUMBER>` (not `1001`).
3. Rewrite path: customer/order ids remain in rewrite input when flag on (BDI ALLOW); credit-limit style BFI still blocked/redacted.
4. Warehouse-only: `warehouse A01` remains visible in ANSWER (OMD ALLOW).

---

## 3. Rollout rule

- Main `application.properties` stays **`enabled=false`** until QA completes the matrix above.
- Do **not** enable in shared DEV/QA without recording the decision.
- Phase 7 metrics (latency, entity counts, BLOCK counts) are **out of scope** for V1.

---

## 4. Related

- Implementation design: [05-implementation-design.md](05-implementation-design.md)
- Package: `com.ai.openai_api_service.service.protection`
- Hook: `OpenAIService.protectForLlm` — Live/Lex bypass via never calling it
