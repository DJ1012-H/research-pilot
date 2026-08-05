[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    throw "PSScriptRoot is empty. Run this file as a .ps1 script."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"
if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw "mvnw.cmd was not found in project directory: $repoRoot"
}

Set-Location -LiteralPath $repoRoot
$tests = "AgentExecutionLoopTest,LiteratureSearchServiceTest,LiteraturePersistenceFacadeIntegrationTest,*Cache*,RequestCorrelationFilterTest,LiteratureObservationMetricsTest"

Write-Host "Running deterministic trusted-demo regressions only; no live provider configuration is supplied." -ForegroundColor Cyan
& $mavenWrapper "-Dtest=$tests" test
if ($LASTEXITCODE -ne 0) {
    throw "Focused trusted-demo regression suite failed. Exit code: $LASTEXITCODE"
}
