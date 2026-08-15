[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DatasetPath,
    [Parameter(Mandatory = $true)][string]$TuningReportPath,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [ValidateRange(5, 20)][int]$TopK = 5,
    [ValidateRange(0.0, 1.0)][double]$CaseOutcomeAccuracy = 0.8333333333,
    [ValidateRange(0.0, 1.0)][double]$PositiveAnswerSuccessRate = 0.8333333333,
    [ValidateRange(0.0, 1.0)][double]$PositiveEvidenceHitRate = 0.8333333333,
    [ValidateRange(0.0, 1.0)][double]$PositiveCitationPrecision = 0.9,
    [ValidateRange(0.0, 1.0)][double]$PositiveRetrievalHitAt5 = 0.8333333333,
    [ValidateRange(0.0, 1.0)][double]$SemanticNegativeRefusalRate = 0.8,
    [ValidateRange(0.0, 1.0)][double]$DeterministicEmptyRefusalRate = 1.0,
    [switch]$ConfirmTuningReviewed
)

$ErrorActionPreference = "Stop"
$utf8NoBom = New-Object Text.UTF8Encoding($false)

function Fail([string]$Message) { throw "RAG v3-lite parameter freeze failed closed: $Message" }

function Sha256-File([string]$Path) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Path).Path)
        return (($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join "")
    } finally { $sha.Dispose() }
}

function Read-Json([string]$Path, [string]$Name) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { Fail "$Name was not found." }
    try { return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json }
    catch { Fail "$Name is not valid JSON." }
}

if (-not $ConfirmTuningReviewed) { Fail "-ConfirmTuningReviewed is required." }
$dataset = [IO.Path]::GetFullPath($DatasetPath)
$manifestPath = Join-Path $dataset "manifest.json"
$manifest = Read-Json $manifestPath "dataset manifest"
$tuningPath = [IO.Path]::GetFullPath($TuningReportPath)
$tuning = Read-Json $tuningPath "tuning report"
if ([string]$manifest.datasetId -ne "rag-retrieval-v3-lite") { Fail "unexpected datasetId." }
if ([string]$tuning.datasetId -ne [string]$manifest.datasetId -or [string]$tuning.split -ne "TUNING") {
    Fail "tuning report does not belong to the frozen tuning split."
}
if ([string]$tuning.status -ne "MEASURED_TUNING_NOT_ACCEPTANCE" -or [int]$tuning.caseCount -ne 12) {
    Fail "tuning report is incomplete or mislabeled."
}
if ([int]$tuning.topK -ne $TopK) { Fail "requested TopK differs from the measured tuning run." }
$tuningFailedResponseCount = if ($null -ne $tuning.metrics.failedResponseCount) {
    [int]$tuning.metrics.failedResponseCount
} elseif ($null -ne $tuning.metrics.unexpectedFailureCount) {
    [int]$tuning.metrics.unexpectedFailureCount
} else {
    Fail "tuning report does not disclose failed response count."
}

$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $resolvedOutput) { Fail "OutputPath already exists and will not be overwritten." }
$parent = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $parent -PathType Container)) { Fail "OutputPath parent directory does not exist." }

$mainRoot = Split-Path -Parent $PSScriptRoot
$mainCommit = (& git -C $mainRoot rev-parse HEAD 2>$null | Select-Object -First 1)
$decision = [ordered]@{
    schemaVersion = "rag-v3-lite-parameter-decision-v0.1"
    status = "FROZEN_AFTER_TUNING"
    frozenAtUtc = [DateTime]::UtcNow.ToString("o")
    datasetId = [string]$manifest.datasetId
    datasetManifestSha256 = Sha256-File $manifestPath
    tuningReportSha256 = Sha256-File $tuningPath
    tuningRunId = [string]$tuning.runId
    topK = $TopK
    evidenceAdmissionPromptVersion = "rag-evidence-admission-v2"
    answerPromptVersion = "rag-answer-draft-v1"
    relatednessScoreThreshold = "NOT_USED_UNCALIBRATED"
    fixedHoldoutRunAllowed = $true
    tuningObservation = [ordered]@{
        caseOutcomeAccuracy = $tuning.metrics.caseOutcomeAccuracy
        positiveAnswerSuccessRate = $tuning.metrics.positiveAnswerSuccessRate
        positiveEvidenceHitRate = $tuning.metrics.positiveEvidenceHitRate
        positiveCitationPrecision = $tuning.metrics.positiveCitationPrecision
        positiveRetrievalRecallAt5 = $tuning.metrics.positiveRetrievalRecallAt5
        positiveRetrievalHitAt5 = $tuning.metrics.positiveRetrievalHitAt5
        semanticNegativeRefusalRate = $tuning.metrics.semanticNegativeRefusalRate
        deterministicEmptyRefusalRate = $tuning.metrics.deterministicEmptyRefusalRate
        failedResponseCount = $tuningFailedResponseCount
        knownRisk = "One or more fail-closed model contract responses may remain; the fixed holdout is not rerunnable for result-driven tuning."
    }
    acceptanceThresholds = [ordered]@{
        caseOutcomeAccuracy = $CaseOutcomeAccuracy
        positiveAnswerSuccessRate = $PositiveAnswerSuccessRate
        positiveEvidenceHitRate = $PositiveEvidenceHitRate
        positiveCitationPrecision = $PositiveCitationPrecision
        positiveRetrievalHitAt5 = $PositiveRetrievalHitAt5
        semanticNegativeRefusalRate = $SemanticNegativeRefusalRate
        deterministicEmptyRefusalRate = $DeterministicEmptyRefusalRate
    }
    mainCommit = [string]$mainCommit
    rationale = @(
        "TopK remains 5 to match the public default and bounded generation evidence contract.",
        "No scalar vector-score threshold is selected from this small reviewed dataset.",
        "The fixed holdout may be run once after this decision file is written."
    )
}
[IO.File]::WriteAllText($resolvedOutput, (($decision | ConvertTo-Json -Depth 8) + "`n"), $utf8NoBom)
Write-Output "[RAG_V3_FREEZE] status=PASS output=$resolvedOutput topK=$TopK tuningRunId=$($tuning.runId)"
