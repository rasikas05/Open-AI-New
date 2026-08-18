# GPT-5.6 Terra vs gpt-4.1 comparison matrix (mail branch)
# Run AFTER deploying this branch with OPENAI_API_KEY set.
# Baseline: temporarily set openai.api.model=gpt-4.1, run each scenario, record results.
# Then set openai.api.model=gpt-5.6-terra and repeat with the same prompts/session.

Write-Host "GPT-5.6 Terra comparison matrix - manual checklist" -ForegroundColor Cyan
Write-Host ""
Write-Host "Config (mail branch): openai.api.model=gpt-5.6-terra, reasoning-effort=none, max-completion-tokens=4096"
Write-Host "Logs to capture: Calling OpenAI chat completions (model, reasoningEffort) and Request Token Summary"
Write-Host ""

$scenarios = @(
    @{ Name = "Normal Docs question"; Pass = "Grounded answer + sources; no errors" },
    @{ Name = "RAG FULL"; Pass = "finalAction=rag, sensible doc answer" },
    @{ Name = "CLEAR rewrite"; Pass = "Valid JSON query list from rewriteQueries" },
    @{ Name = "FULL / PARTIAL / INSUFFICIENT"; Pass = "Reasonable status distribution; no parse failures" },
    @{ Name = "External Source ON + insufficient"; Pass = "generalGPT=true, gpt_infor path" },
    @{ Name = "External Source OFF + insufficient"; Pass = "generalGPT=false, standard insufficient message" },
    @{ Name = "Auto route to rag"; Pass = "Unchanged; external flag ignored" },
    @{ Name = "Live M3"; Pass = "Unchanged" },
    @{ Name = "Suggestions"; Pass = "Non-empty when LLM enabled" },
    @{ Name = "Latency"; Pass = "Record p50/p95 per stage from timing logs" },
    @{ Name = "Token cost"; Pass = "Compare Request Token Summary vs gpt-4.1 baseline" }
)

$i = 1
foreach ($s in $scenarios) {
    Write-Host ("[{0,2}] {1}" -f $i, $s.Name)
    Write-Host ("     Pass: {0}" -f $s.Pass)
    Write-Host ("     gpt-4.1 baseline: [ ]  gpt-5.6-terra: [ ]")
    Write-Host ""
    $i++
}

Write-Host "Merge gate:" -ForegroundColor Yellow
Write-Host "  - No regressions on Auto/M3/External-OFF gating"
Write-Host "  - Grounded JSON parse success rate >= baseline"
Write-Host "  - Acceptable latency/cost for product"
Write-Host ""
Write-Host "Rollback: set openai.api.model=gpt-4.1 and remove reasoning-effort properties."
