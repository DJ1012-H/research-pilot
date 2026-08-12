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
$ErrorActionPreference = "Continue"
$output = @(& $mavenWrapper "-Dtest=RagDemoReplayTest" test 2>&1)
$exitCode = $LASTEXITCODE
$ErrorActionPreference = "Stop"

if ($exitCode -ne 0) {
    $output | Select-Object -Last 100 | ForEach-Object { Write-Host $_ }
    throw "RAG demo replay failed. Exit code: $exitCode"
}

$summaries = @($output | Where-Object {
    $_.ToString().StartsWith("[RAG_DEMO_REPLAY]")
})
if ($summaries.Count -ne 3) {
    throw "RAG demo replay did not produce exactly three redacted summaries."
}

$summaries | ForEach-Object { Write-Output $_ }
