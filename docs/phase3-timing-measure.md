# Phase 3 — PII and persistence split measure protocol

Measurement only. Keep Phase 2 baseline rows. Do **not** optimize until splits identify one bottleneck.

## Log lines to capture (every run)

1. `[TIMING]` — Phase 2 parent buckets (unchanged meaning)
2. `[PII-SPLIT]` — nested under `pii=` (do **not** add again into total)
3. `[PERSIST-SPLIT]` — nested under `persistence=` (do **not** add again into total)
4. `[TOKENS]` — including `suggestions=`

### Nesting rule

```text
pii=2.00s
  businessProtect + comprehend + presidio ≈ piiTotal   → still 2.00s wall, not 4.00s

persistence=1.61s
  tenant + user + session + title + sessionSave + requestLogSave ≈ persistTotal
```

Explained wall (parents only):

```text
explained = python + pii + openai + lex + m3OrQdrant + grounding + persistence + suggestions
gap = total - explained
```

Always record **`suggestions=`**. Sub-splits are diagnostic only.

## Measure matrix

| Test | Why |
|---|---|
| `hi` → AUTO | baseline vs Phase 2 |
| `hi` → M3 | baseline vs Phase 2 |
| `hi` → DOCS | baseline vs Phase 2 (python=0) |
| Longer greeting → AUTO | PII vs text length |
| First `hi` new session | title path + persistence |
| Second `hi` same session | persistence without title |
| Several runs | use **median** |
| Tag cold vs warm | first after idle vs later |

## Stop condition

Stop instrumenting further when logs answer:

- Which of `businessProtect` / `comprehend` / `presidio` dominates `pii`
- Which of the persist sub-ops dominates `persistence`

Then choose **one** safe optimization in a separate change. Re-run the same matrix and compare before vs after.

## Non-goals

No routing changes, conversation gate, OpenAI prompt changes, async DB, or skipping PII in this phase.
