[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$MainRepositoryPath,
    [string]$DatasetPath,
    [string]$ManifestPath,
    [string]$OutputRoot,
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$QdrantBaseUrl = "http://127.0.0.1:6333",
    [ValidatePattern("^[A-Za-z0-9_-]{1,255}$")]
    [string]$CollectionName = "research_pilot_paper_segments_v1",
    [ValidateRange(5, 20)][int]$TopK = 5
)

$ErrorActionPreference = "Stop"
$utf8NoBom = New-Object Text.UTF8Encoding($false)

function Fail([string]$Message) { throw "Reviewed RAG eval orchestration failed closed: $Message" }

function Sha256-Bytes([byte[]]$Bytes) {
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try { return (($sha256.ComputeHash($Bytes) | ForEach-Object { $_.ToString('x2') }) -join '') }
    finally { $sha256.Dispose() }
}

function Sha256-File([string]$Path) {
    return Sha256-Bytes ([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Path).Path))
}

function Git-Value([string]$Repository, [string[]]$Arguments) {
    try {
        $value = & git -c "safe.directory=$Repository" -C $Repository @Arguments 2>$null
        if ($LASTEXITCODE -ne 0) { return "UNAVAILABLE" }
        return (@($value) -join "`n").Trim()
    } catch { return "UNAVAILABLE" }
}

function Read-Observations([string]$Path) {
    $byId = @{}
    foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $Path).Path, [Text.Encoding]::UTF8)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        try { $observation = $line | ConvertFrom-Json } catch { Fail "$Path contains invalid JSONL." }
        $caseId = [string]$observation.caseId
        if ([string]::IsNullOrWhiteSpace($caseId) -or $byId.ContainsKey($caseId)) {
            Fail "$Path contains a blank or duplicate caseId."
        }
        $byId[$caseId] = $observation
    }
    if ($byId.Count -eq 0) { Fail "$Path contains no observations." }
    return $byId
}

function Deterministic-View($Observation) {
    return ([ordered]@{
        schemaVersion = $Observation.schemaVersion
        caseId = $Observation.caseId
        split = $Observation.split
        caseIntent = $Observation.caseIntent
        request = $Observation.request
        status = $Observation.status
        activeEmbeddingVersion = $Observation.activeEmbeddingVersion
        requestedTopK = $Observation.requestedTopK
        qdrantCandidateCount = $Observation.qdrantCandidateCount
        uniquePaperCandidateCount = $Observation.uniquePaperCandidateCount
        admittedPaperCount = $Observation.admittedPaperCount
        filteredCount = $Observation.filteredCount
        rankedPaperIds = @($Observation.rankedPaperIds)
        scores = @($Observation.scores)
        rankedResults = @($Observation.rankedResults)
        failureCode = $Observation.failureCode
    } | ConvertTo-Json -Depth 10 -Compress)
}

$evalRoot = Split-Path -Parent $PSScriptRoot
$mainRoot = [IO.Path]::GetFullPath($MainRepositoryPath)
if (-not (Test-Path -LiteralPath $mainRoot -PathType Container)) { Fail "MainRepositoryPath was not found." }
if ([string]::IsNullOrWhiteSpace($DatasetPath)) {
    $DatasetPath = Join-Path $evalRoot "eval\rag-retrieval-v2-draft"
}
$datasetRoot = [IO.Path]::GetFullPath($DatasetPath)
if (-not (Test-Path -LiteralPath $datasetRoot -PathType Container)) { Fail "DatasetPath was not found." }
if ([string]::IsNullOrWhiteSpace($OutputRoot)) { $OutputRoot = Join-Path $datasetRoot "runs" }
$resolvedOutputRoot = [IO.Path]::GetFullPath($OutputRoot)
if (-not (Test-Path -LiteralPath $resolvedOutputRoot)) {
    New-Item -ItemType Directory -Path $resolvedOutputRoot | Out-Null
}
if (-not (Test-Path -LiteralPath $resolvedOutputRoot -PathType Container)) { Fail "OutputRoot is not a directory." }

$runner = Join-Path $mainRoot "scripts\run-rag-retrieval-eval.ps1"
$metricScript = Join-Path $PSScriptRoot "compute-rag-reviewed-metrics.ps1"
$sourceCases = Join-Path $datasetRoot "cases.jsonl"
$sourceManifest = if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
    $immutableInput = Join-Path $datasetRoot "manifest-input-v0.2.json"
    if (Test-Path -LiteralPath $immutableInput -PathType Leaf) {
        $immutableInput
    } else {
        Join-Path $datasetRoot "manifest.json"
    }
} else {
    [IO.Path]::GetFullPath($ManifestPath)
}
foreach ($path in @($runner, $metricScript, $sourceCases, $sourceManifest)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Fail "required file was not found: $path" }
}
try { $manifest = Get-Content -Raw -Encoding UTF8 -LiteralPath $sourceManifest | ConvertFrom-Json }
catch { Fail "source manifest is invalid JSON." }
$catalogValue = [string]$manifest.candidateCatalogPath
if ([string]::IsNullOrWhiteSpace($catalogValue)) { Fail "manifest.candidateCatalogPath is required." }
$sourceCatalog = if ([IO.Path]::IsPathRooted($catalogValue)) {
    [IO.Path]::GetFullPath($catalogValue)
} else {
    [IO.Path]::GetFullPath((Join-Path $datasetRoot $catalogValue))
}
if (-not (Test-Path -LiteralPath $sourceCatalog -PathType Leaf)) { Fail "candidate catalog was not found." }

$directoryName = "rag-eval-{0}-{1}" -f [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssfffZ"),
    ([guid]::NewGuid().ToString("N").Substring(0, 8))
$runDirectory = Join-Path $resolvedOutputRoot $directoryName
if (Test-Path -LiteralPath $runDirectory) { Fail "generated run directory already exists." }
New-Item -ItemType Directory -Path $runDirectory | Out-Null

$casesSnapshot = Join-Path $runDirectory "cases.jsonl"
$manifestSnapshot = Join-Path $runDirectory "manifest-input.json"
$catalogSnapshot = Join-Path $runDirectory ([IO.Path]::GetFileName($sourceCatalog))
Copy-Item -LiteralPath $sourceCases -Destination $casesSnapshot
Copy-Item -LiteralPath $sourceManifest -Destination $manifestSnapshot
Copy-Item -LiteralPath $sourceCatalog -Destination $catalogSnapshot

$primaryObservation = Join-Path $runDirectory "retrieval-observation-primary.jsonl"
$primaryMetadata = Join-Path $runDirectory "retrieval-run-primary.json"
$metricsPath = Join-Path $runDirectory "reviewed-metrics.json"
$repeatObservation = Join-Path $runDirectory "retrieval-observation-repeat.jsonl"
$repeatMetadata = Join-Path $runDirectory "retrieval-run-repeat.json"
$comparisonPath = Join-Path $runDirectory "reproducibility-check.json"

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner `
    -CasesPath $casesSnapshot -ManifestPath $manifestSnapshot -BaseUrl $BaseUrl `
    -QdrantBaseUrl $QdrantBaseUrl -CollectionName $CollectionName -TopK $TopK `
    -OutputPath $primaryObservation -RunMetadataPath $primaryMetadata
if ($LASTEXITCODE -ne 0) { Fail "primary retrieval capture failed." }

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $metricScript `
    -CasesPath $casesSnapshot -ObservationPath $primaryObservation `
    -ManifestPath $manifestSnapshot -OutputPath $metricsPath
if ($LASTEXITCODE -ne 0) { Fail "reviewed metric computation failed." }

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner `
    -CasesPath $casesSnapshot -ManifestPath $manifestSnapshot -BaseUrl $BaseUrl `
    -QdrantBaseUrl $QdrantBaseUrl -CollectionName $CollectionName -TopK $TopK `
    -OutputPath $repeatObservation -RunMetadataPath $repeatMetadata
if ($LASTEXITCODE -ne 0) { Fail "repeat retrieval capture failed." }

$primary = Read-Observations $primaryObservation
$repeat = Read-Observations $repeatObservation
$differences = @()
$allCaseIds = @($primary.Keys + $repeat.Keys | Sort-Object -Unique)
foreach ($caseId in $allCaseIds) {
    if (-not $primary.ContainsKey($caseId) -or -not $repeat.ContainsKey($caseId)) {
        $differences += [ordered]@{ caseId = $caseId; reason = "MISSING_FROM_ONE_RUN" }
    } elseif ((Deterministic-View $primary[$caseId]) -cne (Deterministic-View $repeat[$caseId])) {
        $differences += [ordered]@{ caseId = $caseId; reason = "DETERMINISTIC_FIELD_DRIFT" }
    }
}

$primaryRun = Get-Content -Raw -Encoding UTF8 -LiteralPath $primaryMetadata | ConvertFrom-Json
$repeatRun = Get-Content -Raw -Encoding UTF8 -LiteralPath $repeatMetadata | ConvertFrom-Json
$evalDirty = Git-Value $evalRoot @("status", "--porcelain", "--untracked-files=no")
$comparison = [ordered]@{
    schemaVersion = "rag-retrieval-reproducibility-check-v0.1"
    checkedAtUtc = [DateTime]::UtcNow.ToString("o")
    status = if ($differences.Count -eq 0) { "PASS" } else { "FAIL" }
    comparedCaseCount = $allCaseIds.Count
    deterministicFieldDifferenceCount = $differences.Count
    differences = $differences
    primaryRunId = [string]$primaryRun.runId
    repeatRunId = [string]$repeatRun.runId
    comparedFields = @(
        "schemaVersion", "caseId", "split", "caseIntent", "request", "status",
        "activeEmbeddingVersion", "requestedTopK", "candidate/admission counts",
        "rankedPaperIds", "scores", "rankedResults", "failureCode")
    excludedVolatileFields = @("runId", "observedAtUtc", "serviceElapsedMs", "clientElapsedMs")
    inputEvidence = [ordered]@{
        casesSha256 = Sha256-File $casesSnapshot
        manifestSha256 = Sha256-File $manifestSnapshot
        candidateCatalogSha256 = Sha256-File $catalogSnapshot
        runnerSha256 = Sha256-File $runner
        metricScriptSha256 = Sha256-File $metricScript
        orchestrationScriptSha256 = Sha256-File $PSCommandPath
        primaryObservationSha256 = Sha256-File $primaryObservation
        repeatObservationSha256 = Sha256-File $repeatObservation
        reviewedMetricsSha256 = Sha256-File $metricsPath
    }
    repositories = [ordered]@{
        mainCommit = Git-Value $mainRoot @("rev-parse", "HEAD")
        mainTrackedWorktreeState = if ([string]$primaryRun.trackedWorktreeState) { [string]$primaryRun.trackedWorktreeState } else { "UNAVAILABLE" }
        evalCommit = Git-Value $evalRoot @("rev-parse", "HEAD")
        evalTrackedWorktreeState = if ([string]::IsNullOrWhiteSpace($evalDirty)) { "CLEAN" } else { "DIRTY" }
    }
}
[IO.File]::WriteAllText($comparisonPath, (($comparison | ConvertTo-Json -Depth 10) + "`n"), $utf8NoBom)
if ($differences.Count -ne 0) { Fail "repeat run changed deterministic retrieval fields; inspect $comparisonPath" }

Write-Output "[RAG_REVIEWED_EVAL] status=PASS runDirectory=$runDirectory primaryRunId=$($primaryRun.runId) repeatRunId=$($repeatRun.runId) caseCount=$($allCaseIds.Count)"
