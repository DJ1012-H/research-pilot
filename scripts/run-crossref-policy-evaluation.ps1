[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$resultPath = Join-Path $repositoryRoot 'target\evaluation\crossref-verification-v1\policy-benchmark-v0.1.json'

Push-Location $repositoryRoot
try {
    & .\mvnw.cmd '-Dtest=CrossrefPolicyBenchmarkTest' test
    if ($LASTEXITCODE -ne 0) {
        throw "Crossref policy benchmark test failed with exit code $LASTEXITCODE"
    }

    if (-not (Test-Path -LiteralPath $resultPath)) {
        throw "Crossref policy benchmark result was not generated: $resultPath"
    }

    $result = Get-Content -LiteralPath $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Write-Host ("Crossref policy benchmark: {0}" -f $(if ($result.acceptance_passed) { 'PASS' } else { 'FAIL' }))
    Write-Host ("Status matches: {0}/{1}" -f $result.overall_status_match_count, $result.evaluated_case_count)
    Write-Host ("Frozen acceptance matches: {0}/{1}" -f $result.acceptance_status_match_count, $result.acceptance_case_count)
    Write-Host ("False VERIFIED: {0}" -f $result.false_verified_count)
    Write-Host ("False formal admission: {0}" -f $result.false_formal_admission_count)
    Write-Host ("False formal exclusion: {0}" -f $result.false_formal_exclusion_count)
    Write-Host ("Exceptions: {0}" -f $result.exception_count)
    Write-Host ("Result: {0}" -f $resultPath)

    if (-not $result.acceptance_passed) {
        $reasonText = $result.failure_reasons -join ', '
        Write-Host "FAIL_CLOSED: Crossref policy acceptance failed: $reasonText" -ForegroundColor Red
        exit 1
    }
}
finally {
    Pop-Location
}
