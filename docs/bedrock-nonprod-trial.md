# Bedrock Chat Completions — enablement and rollback

Java LLM calls go through `LlmChatCompletionsClient`. Active provider is controlled by `llm.provider`.

## Current defaults

| Profile | `llm.provider` | Required env |
|---------|----------------|--------------|
| Default (local) | `bedrock` | `BEDROCK_API_KEY` |
| `prod` | `bedrock` | `BEDROCK_API_KEY` |
| Rollback | `openai` | `OPENAI_API_KEY` |

Endpoint: `https://bedrock-runtime.eu-west-1.amazonaws.com/openai/v1/chat/completions`  
Model: `global.openai.gpt-5.6-terra`  
Auth: Bearer `BEDROCK_API_KEY` (SigV4 later)

## Local enablement (IntelliJ / OS)

Spring does **not** load `.env.example`. Set environment variables in the Run Configuration or OS:

1. `BEDROCK_API_KEY=<your key>` (required)
2. Confirm `llm.provider=bedrock` in `application.properties` (already set)
3. Restart the app
4. Smoke: chat reply, planner JSON, suggestions, Lex/M3, quota tokens

## Rollback to OpenAI

1. Set `llm.provider=openai` in properties (or override via env / profile)
2. Set `OPENAI_API_KEY`
3. Restart and confirm chat + suggestions on the OpenAI path

## Provider rules

- Allowed values: `openai`, `bedrock` only (unsupported values fail fast)
- Bedrock with a blank `BEDROCK_API_KEY` does **not** fall back to `OPENAI_API_KEY`
