# Responses API POC — dev validation helper
# Usage:
#   $env:OPENAI_API_KEY = "your-key"   # required for comparison test
#   $env:COMPREHEND_BEARER_TOKEN = "eyJ..."   # optional; required for HTTP E2E via /api/chat/comprehend
#   .\scripts\responses-poc-validation.ps1 [-SkipHttpE2E] [-SkipComparison]

param(
    [switch]$SkipHttpE2E,
    [switch]$SkipComparison,
    [string]$SessionId = "poc-responses-test-001",
    [string]$TenantCode = "infor",
    [string]$UserId = "rasika",
    [string]$SpringBaseUrl = "http://localhost:8081",
    [string]$MySqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    [string]$CognitoDomain = "eu-central-1j7smcrywv"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

function Test-SpringHealth {
    try {
        $r = Invoke-WebRequest -Uri "$SpringBaseUrl/actuator/health" -UseBasicParsing -TimeoutSec 5
        return $r.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Invoke-ComprehendChat {
    param(
        [string]$Message,
        [string]$Mode = "docs"
    )
    $body = @{
        tenantCode  = $TenantCode
        userId      = $UserId
        sessionId   = $SessionId
        userMessage = $Message
        mode        = $Mode
    } | ConvertTo-Json
    $headers = @{
        Authorization  = "Bearer $env:COMPREHEND_BEARER_TOKEN"
        "Content-Type" = "application/json"
    }
    Invoke-RestMethod -Uri "$SpringBaseUrl/api/chat/comprehend" -Method Post -Headers $headers -Body $body
}

function Get-RequestLogChain {
    $sql = @"
SELECT rl.id, s.session_id, rl.action_taken, rl.retrieval_reason,
       rl.openai_response_id, LEFT(rl.original_text, 60) AS question, rl.created_at
FROM request_logs rl
JOIN session s ON rl.session_ref_id = s.id
WHERE s.session_id = '$SessionId'
ORDER BY rl.created_at ASC;
"@
    & $MySqlBin -uroot -proot -D openaichatprocessdb -e $sql
}

Write-Host "=== Responses POC validation ===" -ForegroundColor Cyan

if (-not (Test-SpringHealth)) {
    Write-Error "Spring is not healthy at $SpringBaseUrl. Start/restart the app with openai.api.responses-poc.enabled=true"
}

Write-Host "[OK] Spring health on $SpringBaseUrl" -ForegroundColor Green

if (-not $SkipHttpE2E) {
    if ([string]::IsNullOrWhiteSpace($env:COMPREHEND_BEARER_TOKEN)) {
        $props = Get-Content (Join-Path $Root "src\main\resources\application.properties") | Where-Object { $_ -match '^aws\.cognito\.(clientId|clientSecret|required-scope)=' }
        $clientId = ($props | Where-Object { $_ -match 'clientId=' }) -replace '.*clientId=',''
        $clientSecret = ($props | Where-Object { $_ -match 'clientSecret=' }) -replace '.*clientSecret=',''
        $scope = ($props | Where-Object { $_ -match 'required-scope=' }) -replace '.*required-scope=',''
        if ($clientId -and $clientSecret -and $CognitoDomain) {
            try {
                $tokenUri = "https://$CognitoDomain.auth.eu-central-1.amazoncognito.com/oauth2/token"
                $tokenBody = "grant_type=client_credentials&client_id=$clientId&client_secret=$clientSecret&scope=$scope"
                $tokenResp = Invoke-RestMethod -Uri $tokenUri -Method Post -ContentType "application/x-www-form-urlencoded" -Body $tokenBody
                $env:COMPREHEND_BEARER_TOKEN = $tokenResp.access_token
                Write-Host "[OK] Cognito M2M token acquired" -ForegroundColor Green
            } catch {
                Write-Warning "Could not acquire Cognito token: $($_.Exception.Message)"
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:COMPREHEND_BEARER_TOKEN)) {
        Write-Warning "COMPREHEND_BEARER_TOKEN not set — skipping HTTP E2E (turn 1/2 via /api/chat/comprehend)."
        Write-Warning "Set a Cognito M2M JWT and re-run, or validate chain via ResponsesPocComparisonTest."
    } else {
        Write-Host "Turn 1: docs-mode fallback (fictional program)..." -ForegroundColor Yellow
        $r1 = Invoke-ComprehendChat -Message "Explain the fictional M3 program ZZZ999 and its integration with quantum computing subsystems"
        Write-Host ("  action=$($r1.actionTaken) responseId=$($r1.openAiResponseId)")
        Write-Host ("  reply preview: " + ($r1.reply.Substring(0, [Math]::Min(80, $r1.reply.Length))))

        Write-Host "Turn 2: second fallback in same session (chain test)..." -ForegroundColor Yellow
        $r2 = Invoke-ComprehendChat -Message "Explain the fictional M3 program AAA111 and its warehouse integration modules in detail"
        Write-Host ("  action=$($r2.actionTaken) responseId=$($r2.openAiResponseId)")
        Write-Host ("  reply preview: " + ($r2.reply.Substring(0, [Math]::Min(80, $r2.reply.Length))))

        Write-Host "MySQL chain:" -ForegroundColor Yellow
        Get-RequestLogChain
    }
}

if (-not $SkipComparison) {
    if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
        Write-Warning "OPENAI_API_KEY not set — skipping ResponsesPocComparisonTest."
    } else {
        Write-Host "Running ResponsesPocComparisonTest..." -ForegroundColor Yellow
        Push-Location $Root
        try {
            & .\mvnw.cmd test "-Dtest=ResponsesPocComparisonTest" -q
            $jsonl = Join-Path $Root "target\responses_poc_samples.jsonl"
            if (Test-Path $jsonl) {
                Write-Host "[OK] Wrote $jsonl" -ForegroundColor Green
                Get-Content $jsonl
            }
        } finally {
            Pop-Location
        }
    }
}

Write-Host "Done. Check Spring logs for:" -ForegroundColor Cyan
Write-Host "  Calling OpenAI Responses API. model=..., previousResponseIdPresent=..."
Write-Host "  Responses POC chatWithoutPersistence: previousResponseIdPresent=..., newResponseId=..."
