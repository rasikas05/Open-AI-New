# Bedrock Explicit Prompt Caching — Implementation Report

**Date:** 2026-11-25  
**Default:** `bedrock.prompt-cache.mode=off` (do **not** enable in production until controlled tests confirm Bedrock write/read metrics)

## 1. Files changed

| File | Change |
|------|--------|
| `OpenAIService.java` | Approved expanded `RAG_SYSTEM_PROMPT` |
| `PromptCacheRequestSupport.java` | **New** — Grounded-only explicit body transform |
| `LlmChatCompletionsClient.java` | Apply cache when mode=explicit + stage=Grounded; persist cache fields in `extractUsage` |
| `PromptCacheProbe.java` | Hash structured text content parts |
| `OpenAIUsage.java` | `cacheWriteTokens`, `cachedTokens`, `cacheMetricsPresent` |
| `RequestLog.java` | Matching DB columns |
| `ChatPersistenceService.java` | Persist cache metrics |
| `application.properties` | `bedrock.prompt-cache.mode=off`, `ttl=30m` |
| `docs/sql/request_logs_add_cache_metrics.sql` | Prod ALTER script |
| `docs/grounded-rag-system-prompt-review.md` | Marked approved/applied |
| Unit tests | Client, support helper, RAG prompt assertions |

## 2. Request structure

### Before (`mode=off` or non-Grounded)

```json
{
  "model": "global.openai.gpt-5.6-terra",
  "messages": [
    { "role": "system", "content": "<string prompt>" },
    { "role": "user", "content": "<history...>" },
    { "role": "user", "content": "USER QUESTION:\n...\n\nRETRIEVED DOCUMENTATION:\n..." }
  ],
  "reasoning_effort": "none",
  "max_completion_tokens": 4096
}
```

### After (`mode=explicit` **and** stage=`Grounded`)

```json
{
  "model": "global.openai.gpt-5.6-terra",
  "messages": [
    {
      "role": "system",
      "content": [{
        "type": "text",
        "text": "<approved grounded system prompt>",
        "prompt_cache_breakpoint": { "mode": "explicit" }
      }]
    },
    "...history/user unchanged as strings..."
  ],
  "prompt_cache_options": { "mode": "explicit", "ttl": "30m" },
  "reasoning_effort": "none",
  "max_completion_tokens": 4096
}
```

No `prompt_cache_key` in v1.

## 3. Stable-prefix token estimate

| Text | `o200k_base` | ≥1024? |
|------|--------------|--------|
| Approved `RAG_SYSTEM_PROMPT` | **1427** | Yes |
| + FR locale suffix | **1472** | Yes |

**Confirmation still required from Bedrock** `cache_write_tokens` / `cached_tokens` in live tests. Do not treat tokenizer estimate as proof of cache hits.

## 4. Configuration properties

```properties
bedrock.prompt-cache.mode=off
bedrock.prompt-cache.ttl=30m
```

## 5. Database changes

Columns on `request_logs`:

- `cache_write_tokens` (INT NULL)
- `cached_tokens` (INT NULL)
- `cache_metrics_present` (BIT(1) NULL)

Script: [`docs/sql/request_logs_add_cache_metrics.sql`](sql/request_logs_add_cache_metrics.sql)

Semantics:

- `prompt_tokens_details` **absent** → `cache_metrics_present=false/0`, token columns **null**
- `prompt_tokens_details` **present** → `cache_metrics_present=true/1`, write/read ints (0 allowed)

## 6–7. First-request write / second-request read

**Not yet measured** — requires controlled Bedrock runs with `mode=explicit` in a non-prod environment.

Suggested:

1. Test A: `mode=off` — body unchanged  
2. Test B: first Grounded with `explicit` — expect `cache_write_tokens` > 0  
3. Test C: repeat identical stable prefix — check `cached_tokens`  
4. Test D: change RAG/question — prefix stable  
5. Test E: Planner — no cache fields  
6. Test F: DOCS / AUTO / M3 regression  

## 8. Unit test results

`.\mvnw.cmd -Dtest=LlmChatCompletionsClientTest,PromptCacheRequestSupportTest,OpenAIServiceRagContextTest,OpenAiChatRequestBuilderTest,PromptCacheProbeTest test` — **PASSED** (exit 0).

## 9. Limitations / cost / CRIS

- Cache hit is **not** guaranteed.
- **Global CRIS** (`global.openai.gpt-5.6-terra`): cache entries are server-local; cross-Region routing can increase writes.
- **Cost (Global CRIS, GPT-5.6 Terra, ≤272K input):** input ~$2.00/1M; **30m cache write ~$2.50/1M**; cache read ~$0.20/1M (AWS model card). Write ≈ 1.25× uncached input.
- `prompt_cache_key` omitted until AWS confirms Chat Completions support for this endpoint/model.
- Planner / Rewrite / General / GapFill / multimodal / Suggestion: **unchanged**.

## Production enablement gate

Do **not** set `bedrock.prompt-cache.mode=explicit` in production until Tests B/C show actual Bedrock `cache_write_tokens` and `cached_tokens`.
