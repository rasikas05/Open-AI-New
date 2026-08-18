# Prompt optimization validation matrix
# Compare old vs new prompts on the same 20-30 M3 questions.
# Verify actual promptTokens from application logs (estimates are not sufficient).

Write-Host "Prompt optimization validation matrix" -ForegroundColor Cyan
Write-Host ""
Write-Host "Log lines to capture:"
Write-Host "  - Query rewrite produced"
Write-Host "  - Grounded Response Parsed"
Write-Host "  - OpenAI Usage | stage=Grounded | promptTokens=..."
Write-Host "  - Request Token Summary"
Write-Host ""

$metrics = @(
    @{ Name = "Rewrite quality"; Pass = "1-3 relevant queries; no invented program IDs" },
    @{ Name = "Invented identifiers"; Pass = "No OIS/PPS/etc. unless user provided them" },
    @{ Name = "Retrieval relevance"; Pass = "maxScore same or better vs baseline" },
    @{ Name = "FULL/PARTIAL/INSUFFICIENT"; Pass = "Fewer false PARTIAL from related docs" },
    @{ Name = "Hallucination / grounding"; Pass = "No procedures not in retrieved chunks" },
    @{ Name = "Answer relevance"; Pass = "Directly answers primary question" },
    @{ Name = "promptTokens (Grounded)"; Pass = "Lower than baseline from logs" },
    @{ Name = "External Source"; Pass = "Sensible when docs insufficient + flag ON" }
)

$i = 1
foreach ($m in $metrics) {
    Write-Host ("[{0,2}] {1}" -f $i, $m.Name)
    Write-Host ("     Pass: {0}" -f $m.Pass)
    Write-Host ("     Baseline: [ ]  New prompts: [ ]")
    Write-Host ""
    $i++
}

Write-Host "Merge gate: grounding accuracy >= baseline AND promptTokens reduced in logs."
