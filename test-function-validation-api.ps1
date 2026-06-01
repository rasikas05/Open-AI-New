# Function Validation API - Testing Script (PowerShell)
# This script can be used to test the API endpoint on Windows

$apiBaseUrl = "http://localhost:8080/api/functions"
$endpoint = "$apiBaseUrl/validate"

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "Function Validation API - Test Script" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Function to display test results
function Test-Api($testName, $functionIds) {
    Write-Host "Test: $testName" -ForegroundColor Yellow
    Write-Host "---"
    
    $body = @{
        functionIds = $functionIds
    } | ConvertTo-Json
    
    Write-Host "Request Body:"
    Write-Host $body -ForegroundColor Gray
    Write-Host ""
    Write-Host "Response:"
    
    try {
        $response = Invoke-WebRequest -Uri $endpoint `
            -Method Post `
            -Headers @{"Content-Type"="application/json"} `
            -Body $body
        
        $jsonResponse = $response.Content | ConvertFrom-Json
        Write-Host ($jsonResponse | ConvertTo-Json -Depth 10) -ForegroundColor Green
    }
    catch {
        Write-Host "Error: $_" -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host ""
}

# Test 1: Mixed Valid and Invalid
Test-Api "Mixed Valid and Invalid FNIDs" @("MMS001", "MMS200", "OIS100", "INVALID001")

# Test 2: All Valid
Test-Api "All Valid FNIDs" @("MMS001", "MMS200")

# Test 3: All Invalid
Test-Api "All Invalid FNIDs" @("INVALID001", "INVALID002", "INVALID003")

# Test 4: Empty List
Test-Api "Empty List" @()

# Test 5: With Duplicates
Test-Api "With Duplicates" @("MMS001", "MMS001", "MMS200", "MMS200")

# Test 6: Single Valid
Test-Api "Single Valid FNID" @("MMS001")

# Test 7: Single Invalid
Test-Api "Single Invalid FNID" @("INVALID001")

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "All tests completed!" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
