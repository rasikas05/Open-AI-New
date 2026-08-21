# Phase 4 — Quota/token timing measure protocol

Measurement only. Keep Phase 2/3 baselines. **No quota SQL or logic optimization** in this phase.

## Log lines (every run)

1. `[TIMING]` — includes parent `quota=` (checkBeforeChat + recordUsage walls)
2. `[QUOTA-SPLIT]` — nested under `quota=` (do **not** add again into total)
3. `[PII-SPLIT]` / `[PERSIST-SPLIT]` — unchanged from Phase 3
4. `[TOKENS]` — including `suggestions=`
5. Stage Timing `sessionLimit` — separate from `quota=` (residual diagnosis only)

### Nesting rule

```text
quota = checkTotal + usageTotal
  checkTenant + checkQuota ≈ checkTotal
  usageTenant + usageQuotaLookup + usageUpdate + usageBalanceLookup + usageTokenTxn ≈ usageTotal
```

Explained wall (parents only):

```text
explained = python + pii + openai + lex + m3OrQdrant + grounding + quota + persistence + suggestions
gap = total - explained
```

## Measure matrix

| Case | Examples |
|---|---|
| Conversational | `Hello`, `How are you?`, `What is your name?` |
| M3 / LIVE | `Show customer Y11100` |
| DOCS/RAG | `What is OIS100?` |

Several runs each; record **median** and **p95**; tag cold vs warm.

## Decision gate (stop coding; analyze)

| Case | Finding | Next (later PR only) |
|---|---|---|
| **A** | `quota` ≈ 1.0s | One safe quota-path change; keep atomic `WHERE` |
| **B** | `quota` ≈ 0.1–0.2s | Do not touch quota; chase remaining `gap` |
| **C** | High variance | 10 runs, median/p95, then decide |

Do **not** weaken:

```sql
WHERE tenant_ref_id = ? AND status = 'ACTIVE'
  AND (tokens_used + ?) <= (base_limit + extra_tokens)
```

## Deliverable template

```text
Baseline total:       …
Quota/token (quota=): …
Other residual (gap): …
Optimization applied: none (measurement PR)
New total:            n/a until Case A fix
```
