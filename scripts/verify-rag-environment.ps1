[CmdletBinding()]
param(
    [switch]$RestartQdrant
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    throw "PSScriptRoot is empty. Run this file as a .ps1 script."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot "infra\docker-compose.rag.yml"
$ollamaBaseUrl = "http://127.0.0.1:11434"
$qdrantBaseUrl = "http://127.0.0.1:6333"
$embeddingModel = "qwen3-embedding:0.6b"

function Resolve-CommandPath {
    param(
        [Parameter(Mandatory)][string]$Name,
        [string[]]$FallbackPaths = @()
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }
    foreach ($fallbackPath in $FallbackPaths) {
        if (-not [string]::IsNullOrWhiteSpace($fallbackPath) -and
                (Test-Path -LiteralPath $fallbackPath)) {
            return $fallbackPath
        }
    }

    throw "Required command '$Name' is not available on PATH or at a known local installation path."
}

function Measure-Embedding {
    param([Parameter(Mandatory)][string]$InputText)

    $body = @{
        model = $embeddingModel
        input = $InputText
    } | ConvertTo-Json

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $response = Invoke-RestMethod `
        -Method Post `
        -Uri "$ollamaBaseUrl/api/embed" `
        -ContentType "application/json" `
        -Body $body `
        -TimeoutSec 60
    $stopwatch.Stop()

    if ($null -eq $response.embeddings -or $response.embeddings.Count -ne 1) {
        throw "Ollama returned an unexpected embeddings array."
    }

    $dimension = $response.embeddings[0].Count
    if ($dimension -le 0) {
        throw "Ollama returned an empty embedding."
    }

    [pscustomobject]@{
        Dimension = $dimension
        ElapsedMs = $stopwatch.ElapsedMilliseconds
    }
}

function Wait-QdrantReady {
    $lastError = $null
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        try {
            $null = Invoke-RestMethod -Method Get -Uri "$qdrantBaseUrl/readyz" -TimeoutSec 2
            return
        } catch {
            $lastError = $_
            Start-Sleep -Seconds 2
        }
    }

    throw "Qdrant did not become ready within 60 seconds. Last error: $($lastError.Exception.Message)"
}

$dockerCommand = Resolve-CommandPath `
    -Name "docker" `
    -FallbackPaths @(
        (Join-Path $env:LOCALAPPDATA "Programs\DockerDesktop\resources\bin\docker.exe"),
        (Join-Path $env:ProgramFiles "Docker\Docker\resources\bin\docker.exe")
    )
$ollamaCommand = Resolve-CommandPath `
    -Name "ollama" `
    -FallbackPaths @(
        (Join-Path $env:LOCALAPPDATA "Programs\Ollama\ollama.exe")
    )

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "RAG Compose file was not found: $composeFile"
}

Write-Host "Validating Docker and the pinned Qdrant Compose definition..." -ForegroundColor Cyan
& $dockerCommand version
if ($LASTEXITCODE -ne 0) {
    throw "docker version failed with exit code $LASTEXITCODE."
}
& $dockerCommand compose version
if ($LASTEXITCODE -ne 0) {
    throw "docker compose version failed with exit code $LASTEXITCODE."
}
& $dockerCommand compose -f $composeFile config --quiet
if ($LASTEXITCODE -ne 0) {
    throw "docker compose config validation failed with exit code $LASTEXITCODE."
}

Write-Host "Checking the configured Ollama model without logging input text or vectors..." -ForegroundColor Cyan
& $ollamaCommand show $embeddingModel
if ($LASTEXITCODE -ne 0) {
    throw "Ollama model '$embeddingModel' is not available. Run: ollama pull $embeddingModel"
}

$chineseText = -join ([char[]]@(0x9065, 0x611F, 0x5F71, 0x50CF, 0x53D8, 0x5316, 0x68C0, 0x6D4B))
$chinese = Measure-Embedding -InputText $chineseText
$english = Measure-Embedding -InputText "remote sensing image change detection"
if ($chinese.Dimension -ne $english.Dimension) {
    throw "Embedding dimension mismatch: Chinese=$($chinese.Dimension), English=$($english.Dimension)."
}

Write-Host "Embedding smoke passed: model=$embeddingModel, dimension=$($chinese.Dimension), ChineseMs=$($chinese.ElapsedMs), EnglishMs=$($english.ElapsedMs)." -ForegroundColor Green

Write-Host "Starting the pinned Qdrant service..." -ForegroundColor Cyan
& $dockerCommand compose -f $composeFile up -d qdrant
if ($LASTEXITCODE -ne 0) {
    throw "Qdrant startup failed with exit code $LASTEXITCODE."
}
Wait-QdrantReady
$serviceInfo = Invoke-RestMethod -Method Get -Uri $qdrantBaseUrl -TimeoutSec 5
$collections = Invoke-RestMethod -Method Get -Uri "$qdrantBaseUrl/collections" -TimeoutSec 5

if ($RestartQdrant) {
    Write-Host "Stopping and restarting Qdrant while preserving the named volume..." -ForegroundColor Cyan
    & $dockerCommand compose -f $composeFile stop qdrant
    if ($LASTEXITCODE -ne 0) {
        throw "Qdrant stop failed with exit code $LASTEXITCODE."
    }
    & $dockerCommand compose -f $composeFile start qdrant
    if ($LASTEXITCODE -ne 0) {
        throw "Qdrant restart failed with exit code $LASTEXITCODE."
    }
    Wait-QdrantReady
}

$containerState = & $dockerCommand inspect --format "{{.State.Status}}/{{.State.Health.Status}}" research-pilot-qdrant
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect the Qdrant container."
}

Write-Host "Qdrant smoke passed: title=$($serviceInfo.title), container=$containerState, collections=$($collections.result.collections.Count)." -ForegroundColor Green
Write-Host "No Collection was created. Its vector size remains gated on the measured dimension above." -ForegroundColor Yellow
