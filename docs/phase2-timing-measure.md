# Phase 2 — Timing measure protocol

Measurement only. Do **not** optimize until this protocol produces clear bottleneck evidence.

## Prerequisites

- Deploy/run Spring (`Open-AI-New`) + Python RAG with INFO logs enabled for
  `com.ai.openai_api_service.service.ComprehendChatService` (and Python RAG client).
- Look for one-line summaries: `[CHAT]`, `[TIMING]`, `[TOKENS]`.
- Optional detail: `Stage Timing | stage=…`, `OpenAI Usage | stage=Planner | latency=…ms`,
  `Python RAG route API … responseTime=…ms`.

## `[TIMING]` field meanings

| Field | Meaning |
|---|---|
| `python` | Python `/route` HTTP `responseTime` (0 when DOCS skips route) |
| `pii` | Combined protect + PII anonymize (Comprehend/Presidio **not** split) |
| `openai` | Planner `understandRequest` only — **excludes** PII |
| `lex` / `m3OrQdrant` / `grounding` | Path-specific; often `0.00s` on conversational turns |
| `persistence` / `suggestions` / `total` | Existing service buckets + wall |

`openai` and `pii` must be additive (no double-count). Planner skipped on LIVE → `openai=0.00s`.

## Cases (run each several times)

| Case | Example | Mode | What to read |
|---|---|---|---|
| Conversational | `hi` | M3 or AUTO | `python`, `pii`, `openai`, `persistence`, `suggestions`, `total` |
| RAG | `What is OIS100?` | AUTO or DOCS | above + `m3OrQdrant` / `grounding` (+ Stage Timing retrieval/grounded) |
| LIVE | `Show customer Y00111` | M3 or AUTO | `python`, persistence; planner SKIP → `openai` ≈ 0 |

Network variance is expected; use medians across runs.

## Decision gate

From logs alone, answer:

1. Where is majority wall time on `hi`?
2. Where are tokens spent (`[TOKENS]` / Planner usage)?
3. Is LIVE dominated by `python` vs Lex/fulfillment vs persistence?

Pick **one** later optimization only after that. No optimization in the Phase 2 change set.
