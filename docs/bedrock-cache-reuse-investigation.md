# Bedrock cache reuse investigation (observation only)

This guide covers controlled A/B/C/D tests and stage aggregation after deploying the observation-only build. It does **not** change prompt construction, explicit caching, `OpenAIUsage`, or DB persistence.

## What each Bedrock call logs

### Outbound config (before `exchange`)

Look for:

```text
Bedrock outbound config | cacheMode=implicit_default explicitCacheOptionsPresent=false model=... endpoint=... messageCount=... jsonObject=...
```

Confirms runtime path: same model/endpoint as controlled tests, no `prompt_cache_options` / `prompt_cache_key` / `prompt_cache_breakpoint`.

### Usage + prefix probe (after success)

Look for:

```text
Bedrock usage dump | correlationId=... sessionId=... userId=... stage=... model=... endpoint=...
  elapsedMs=... call=... messageCount=... promptTokens=... cacheWriteTokens=... cachedTokens=...
  systemPromptHash=... historyHash=... ragContextHash=... fullPromptHash=...
  cumSys=... cumSysHist=... cumSysHistQuestion=... cumFull=...
  systemPromptChars=... systemPromptTokensEst=...
  prefixCharsBeforeRag=... prefixTokensBeforeRagEst=... usage=...
```

| Field | Meaning |
|-------|---------|
| `stage` | Explicit call-site stage (preferred) or MDC `llm.stage`; else `Unknown` |
| `promptTokens` / `cacheWriteTokens` / `cachedTokens` | Bedrock-reported (ground truth for cost/hits) |
| `fullPromptHash` | Hash of entire serialized messages |
| `cumSys` / `cumSysHist` / `cumSysHistQuestion` / `cumFull` | Cumulative prefix hashes at message boundaries |
| `*TokensEst` | Rough `ceil(chars/4)` only — **not** proof of the 1,024-token cache minimum |

**Stages:** `Planner`, `Rewrite`, `Grounded`, `Suggestion`, `General`, `GapFill`, `RomanClassify`, `Translate`, `VisionQuery`, `VisionAnswer`.

Privacy: hashes and lengths only — never raw prompts or documents.

## Controlled tests A / B / C / D

Use the **same** model, endpoint, and tenant. Capture dumps (especially `Grounded` / `Planner` as relevant). Prefer one fixed question text for A/B/D.

| Request | Scenario |
|---------|----------|
| **A** | Same question, **new** session |
| **B** | Same question, **same** session (after A or a prior turn that created history) |
| **C** | Follow-up question, same session |
| **D** | Same question again after a short delay, same session |

### Interpretation

| Result | Conclusion |
|--------|------------|
| Same cumulative hash at boundary N, `cachedTokens` > 0 | Reuse works for that prefix |
| Same cumulative / full hash, `cachedTokens` = 0 | Routing / availability / config issue (not content divergence) |
| First differing cumulative boundary | Content divergence location (reusable shared length = everything before that) |
| Same `ragContextHash`, different earlier cumulative hash | System / history / serialization change |
| A vs B: `cumSys` match, `cumSysHist` differ | History drift (expected when prior user questions are appended) |
| B vs D: full / question hashes match, compare `cachedTokens` | Separates history drift from repeat-hit behavior |

Use Bedrock `promptTokens` / cache fields for cost and hit conclusions. Use `*TokensEst` only as rough “near 1024?” context.

### Comparing two dumps

1. Compare `cumSys` → then `cumSysHist` → then `cumSysHistQuestion` → then `cumFull` / `fullPromptHash`.
2. First mismatch = divergence point.
3. If all match and `cachedTokens` is still 0, do not blame RAG uniqueness alone.

## Stage aggregation report

From post-deploy logs, aggregate `Bedrock usage dump` lines:

| Stage | Calls | Prompt tokens | Cache writes | Cache reads |
|-------|-------|---------------|--------------|-------------|
| Planner | | | | |
| Rewrite | | | | |
| Grounded | | | | |
| Suggestion | | | | |
| General | | | | |
| GapFill | | | | |
| RomanClassify / Translate | | | | |
| VisionQuery / VisionAnswer | | | | |

Plus from A/B/C/D:

- First diverging cumulative boundary for each pair (A↔B, B↔C, B↔D).
- Whether any same-prefix pair produced `cachedTokens` > 0.

### Example log scrape (PowerShell)

```powershell
Select-String -Path .\app.log -Pattern "Bedrock usage dump" |
  ForEach-Object { $_.Line } |
  Out-File .\bedrock-usage-dumps.txt
```

Sum `cacheWriteTokens` / `cachedTokens` / `promptTokens` by `stage=` to fill the table.

## Success criteria for this phase

1. Which stage generates cache writes? → stage table.
2. How much of the prompt is reusable / where prefixes diverge? → cumulative hashes on A/B/C/D.
3. Does Bedrock reuse matching content? → `cachedTokens` > 0 when prefixes match.

**Do not** optimize prompt structure or add explicit breakpoints until these three questions have evidence-based answers.
