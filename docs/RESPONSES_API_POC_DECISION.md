# Responses API POC — Decision Record



**Status:** Dev validation completed 2026-09-02. Flag rolled back to OFF.



## Scope implemented



- Opt-in flag: `openai.api.responses-poc.enabled=false` (default)

- Target method only: `OpenAIService.chatWithoutPersistence()`

- Chain storage: `request_logs.openai_response_id` + `previous_response_id` on next turn

- Unchanged: rewrite, router, grounded RAG, gap-fill, suggestions, legacy `chat()`, Python



## Dev validation results (2026-09-02)



### E2E chain proof (Comprehend + MySQL)



Session `poc-responses-test-002`, `mode=docs`, fallback via `retrieval_error` → `gpt_infor`:



| Turn | Log `previousResponseIdPresent` | DB `openai_response_id` |

|---|---|---|

| 1 | `false` | `resp_...daa534c87...` |

| 2 | `true` | `resp_...de97e0887...` (different id) |



**Verdict:** POC works end-to-end — Responses API called, ids persisted, chain continued on turn 2.



**How fallback was triggered:** `mode=docs` + fictional M3 program questions (no RAG threshold changes).



**Note:** Follow-up questions routed to `conversational` unless they also miss retrieval; use two consecutive fallback-triggering docs questions to test the chain.



### Isolated comparison (`ResponsesPocComparisonTest`, model `gpt-5.6-terra`)



Output: `target/responses_poc_samples.jsonl` (10 rows, 5 turns × 2 modes).



| Criterion | Completions | Responses POC | Notes |

|---|---|---|---|

| Total tokens (5-turn sum) | **392** (72 prompt + 320 completion) | **5,297** (3,613 prompt + 1,684 completion) | Responses chain bills growing prompt context each turn |

| Follow-up coherence | **Poor** — turns 2–5 asked for clarification ("what is *it*?") | **Good** — correct OIS300 follow-ups and summary | Isolated test had `loadHistoryFromDb=false`; Completions did not replay DB history |

| Latency (5-turn sum) | **21,261 ms** | **34,213 ms** | Turn 1 Completions slower (cold); Responses turn 4 slowest (11.8s) |

| Code complexity acceptable | — | **Yes** | Isolated client + flag branch; no regressions when OFF |



## Decision gate



**Decision:** [ ] Adopt for fallback  [x] Defer  [ ] Reject



**Rationale:** E2E chain proof passed. Responses mode delivers clearly better multi-turn coherence in the isolated script, but **token cost is ~13× higher** over 5 turns because `previous_response_id` carries full chained context into billed prompt tokens. Defer production enable until:



1. A fair Completions baseline is measured **with** production `toOpenAiUserHistory()` (5 user questions) on the same fallback script.

2. Real fallback traffic (not isolated test) is profiled for token share vs rewrite/grounded stages.



**Do not add summarization** until token breakdown shows conversation history is a dominant cost in production fallback paths.



## How to re-run validation



```powershell

cd Open-AI-New

# Enable flag in application.properties, restart Spring, then:

$env:OPENAI_API_KEY = "your-key"

$env:COMPREHEND_BEARER_TOKEN = "..."   # Cognito M2M JWT

.\scripts\responses-poc-validation.ps1

.\mvnw.cmd test -Dtest=ResponsesPocComparisonTest

```



Cognito M2M domain (dev): `eu-central-1j7smcrywv.auth.eu-central-1.amazoncognito.com`



Optional: `-Dresponses.poc.out=path/to/file.jsonl`



## Preflight (completed)



| Check | Result |

|---|---|

| Model `gpt-5.6-terra` supports Responses API | **Yes** — account confirmed via live `/v1/responses` calls |

| Multi-turn test script | **Defined** — 5 M3 follow-up questions in `ResponsesPocComparisonTest.TURN_QUESTIONS` |

| Metrics template | **Defined** — JSONL columns + `target/responses_poc_samples.jsonl` |



**Test script (5 turns):**



1. What is OIS300 in Infor M3?

2. What are its main functions?

3. How does it relate to customer orders?

4. What programs are commonly used with it?

5. Summarize our discussion in two sentences.



## Enable POC in dev (only after review)



```properties

openai.api.responses-poc.enabled=true

```



Restart Spring with prod/dev profile as usual. **Roll back to `false` after testing.**

