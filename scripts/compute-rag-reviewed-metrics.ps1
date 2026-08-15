[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$CasesPath,
    [Parameter(Mandatory = $true)][string]$ObservationPath,
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [Parameter(Mandatory = $true)][string]$OutputPath
)

$ErrorActionPreference = "Stop"
$utf8NoBom = New-Object Text.UTF8Encoding($false)

function Fail([string]$Message) { throw "Reviewed RAG metric computation failed closed: $Message" }

function Sha256-Bytes([byte[]]$Bytes) {
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try { return (($sha256.ComputeHash($Bytes) | ForEach-Object { $_.ToString('x2') }) -join '') }
    finally { $sha256.Dispose() }
}

function Sha256-File([string]$Path) {
    return Sha256-Bytes ([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Path).Path))
}

function Read-Utf8LfBytes([string]$Path) {
    $bytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Path).Path)
    $text = [Text.Encoding]::UTF8.GetString($bytes).Replace("`r`n", "`n").Replace("`r", "`n")
    return [Text.Encoding]::UTF8.GetBytes($text)
}

function Case-Hash($Case) {
    $canonical = "{0}|{1}|{2}|{3}|{4}" -f $Case.caseId, $Case.queryLanguage, $Case.queryText,
        $Case.relevanceJudgmentProvenance, $Case.reviewStatus
    return Sha256-Bytes ([Text.Encoding]::UTF8.GetBytes($canonical))
}

function Require-Text($Value, [string]$Name) {
    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) { Fail "$Name is blank." }
    return [string]$Value
}

function Require-NonNegativeInt($Value, [string]$Name) {
    try { $number = [long]$Value } catch { Fail "$Name is not an integer." }
    if ($number -lt 0) { Fail "$Name is negative." }
    return $number
}

function Require-FiniteDouble($Value, [string]$Name) {
    try { $number = [double]$Value } catch { Fail "$Name is not numeric." }
    if ([double]::IsNaN($number) -or [double]::IsInfinity($number)) { Fail "$Name is not finite." }
    return $number
}

function Nullable-Value($Value) {
    if ($null -eq $Value) { return "<NULL>" }
    return [string]$Value
}

function Average-Field($Rows, [string]$Name) {
    $items = @($Rows)
    if ($items.Count -eq 0) { return "UNMEASURED" }
    $total = 0.0
    foreach ($row in $items) { $total += [double]$row.$Name }
    return [math]::Round(($total / [double]$items.Count), 10)
}

function Build-MetricSummary($Rows, [int]$TopK) {
    $items = @($Rows)
    $positive = @($items | Where-Object { $_.relevantCount -gt 0 })
    $empty = @($items | Where-Object { $_.relevantCount -eq 0 })
    $semanticNegative = @($empty | Where-Object { $_.caseIntent -eq "no_relevant_result" })
    $semanticRejected = @($semanticNegative | Where-Object { $_.observedStatus -eq "NO_TRUSTED_RESULTS" }).Count
    $emptyRejected = @($empty | Where-Object { $_.observedStatus -eq "NO_TRUSTED_RESULTS" }).Count
    $hitCount = @($positive | Where-Object { $_.hitAt5 }).Count
    return [ordered]@{
        caseCount = $items.Count
        positiveCaseCount = $positive.Count
        emptyRelevantSetCaseCount = $empty.Count
        semanticNegativeCaseCount = $semanticNegative.Count
        metrics = [ordered]@{
            "Recall@1" = Average-Field $positive "recallAt1"
            "Recall@3" = Average-Field $positive "recallAt3"
            "Recall@5" = Average-Field $positive "recallAt5"
            "Hit@5" = if ($positive.Count -eq 0) { "UNMEASURED" } else { [math]::Round(($hitCount / [double]$positive.Count), 10) }
            MRR = Average-Field $positive "mrr"
            emptySetRejectionRate = if ($empty.Count -eq 0) { "UNMEASURED" } else { [math]::Round(($emptyRejected / [double]$empty.Count), 10) }
            semanticNegativeRejectionRate = if ($semanticNegative.Count -eq 0) { "UNMEASURED" } else { [math]::Round(($semanticRejected / [double]$semanticNegative.Count), 10) }
        }
    }
}

foreach ($path in @($CasesPath, $ObservationPath, $ManifestPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Fail "missing input: $path" }
}
$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
foreach ($path in @($CasesPath, $ObservationPath, $ManifestPath)) {
    if ($resolvedOutput.Equals([IO.Path]::GetFullPath($path), [StringComparison]::OrdinalIgnoreCase)) {
        Fail "OutputPath must not overwrite an input file."
    }
}
if (Test-Path -LiteralPath $resolvedOutput) { Fail "OutputPath already exists; use a new versioned path." }
$parent = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $parent -PathType Container)) { Fail "OutputPath parent directory does not exist." }

try { $manifest = Get-Content -Encoding UTF8 -Raw -LiteralPath $ManifestPath | ConvertFrom-Json }
catch { Fail "manifest is invalid JSON." }
$actualManifestHash = Sha256-Bytes (Read-Utf8LfBytes $CasesPath)
if ($actualManifestHash -ne [string]$manifest.casesLfSha256) { Fail "cases LF-SHA256 does not match manifest." }

$cases = @()
$caseById = @{}
foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $CasesPath).Path, [Text.Encoding]::UTF8)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try { $case = $line | ConvertFrom-Json } catch { Fail "cases.jsonl contains invalid JSON." }
    $caseId = Require-Text $case.caseId "caseId"
    if ($caseById.ContainsKey($caseId)) { Fail "duplicate caseId in cases: $caseId." }
    if ([string]$case.reviewStatus -ne "REVIEWED") { Fail "case $caseId is not reviewed." }
    Require-Text $case.reviewer "reviewer for $caseId" | Out-Null
    Require-Text $case.relevanceJudgmentProvenance "provenance for $caseId" | Out-Null
    if ((Case-Hash $case) -ne [string]$case.frozenHash) { Fail "frozenHash mismatch for $caseId." }
    $caseById[$caseId] = $case
    $cases += $case
}
if ($cases.Count -eq 0) { Fail "no cases found." }
if ($null -ne $manifest.caseCount -and [int]$manifest.caseCount -ne $cases.Count) { Fail "manifest.caseCount mismatch." }
if ($null -ne $manifest.tuningCaseCount -and [int]$manifest.tuningCaseCount -ne @($cases | Where-Object { $_.split -eq "TUNING" }).Count) { Fail "manifest.tuningCaseCount mismatch." }
if ($null -ne $manifest.fixedAcceptanceCaseCount -and [int]$manifest.fixedAcceptanceCaseCount -ne @($cases | Where-Object { $_.split -eq "FIXED_ACCEPTANCE" }).Count) { Fail "manifest.fixedAcceptanceCaseCount mismatch." }

$observationById = @{}
$runIds = @{}
$topKs = @{}
$embeddingVersions = @{}
foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $ObservationPath).Path, [Text.Encoding]::UTF8)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try { $observation = $line | ConvertFrom-Json } catch { Fail "observation JSONL contains invalid JSON." }
    $caseId = Require-Text $observation.caseId "observation.caseId"
    if ($observationById.ContainsKey($caseId)) { Fail "duplicate observation caseId: $caseId." }
    if (-not $caseById.ContainsKey($caseId)) { Fail "extra observation caseId: $caseId." }
    $status = Require-Text $observation.status "status for $caseId"
    if ($status -notin @("SUCCESS", "NO_TRUSTED_RESULTS", "FAILED")) { Fail "unknown observation status for $caseId." }
    $requestedTopK = Require-NonNegativeInt $observation.requestedTopK "requestedTopK for $caseId"
    if ($requestedTopK -lt 5) { Fail "topK must be at least 5 for Recall@5 metrics." }
    $topKs[[string]$requestedTopK] = $true
    $version = Require-Text $observation.activeEmbeddingVersion "activeEmbeddingVersion for $caseId"
    $embeddingVersions[$version] = $true
    if ($null -ne $observation.runId) { $runIds[[string]$observation.runId] = $true }

    $ranked = @($observation.rankedPaperIds | ForEach-Object { [long]$_ })
    if ($ranked.Count -gt $requestedTopK) { Fail "too many ranked results for $caseId." }
    if (@($ranked | Sort-Object -Unique).Count -ne $ranked.Count) { Fail "duplicate ranked paper IDs for $caseId." }
    if ($status -eq "SUCCESS" -and $ranked.Count -eq 0) { Fail "SUCCESS has no ranked results for $caseId." }
    if ($status -eq "NO_TRUSTED_RESULTS" -and $ranked.Count -ne 0) { Fail "NO_TRUSTED_RESULTS has ranked results for $caseId." }
    if ((Require-NonNegativeInt $observation.admittedPaperCount "admittedPaperCount for $caseId") -ne $ranked.Count) { Fail "admitted count mismatch for $caseId." }

    $scores = @()
    if ($null -ne $observation.scores) {
        $scores = @($observation.scores | ForEach-Object { Require-FiniteDouble $_ "score for $caseId" })
        if ($scores.Count -ne $ranked.Count) { Fail "score count mismatch for $caseId." }
        for ($index = 1; $index -lt $scores.Count; $index++) {
            if ($scores[$index] -gt $scores[$index - 1]) { Fail "scores are not descending for $caseId." }
        }
    }

    if ([string]$observation.schemaVersion -eq "rag-retrieval-observation-v0.2") {
        if ($scores.Count -ne $ranked.Count) { Fail "v0.2 observation is missing scores for $caseId." }
        $case = $caseById[$caseId]
        $expectedFrom = if ($null -eq $case.filter) { $null } else { $case.filter.fromYear }
        $expectedTo = if ($null -eq $case.filter) { $null } else { $case.filter.toYear }
        if ((Nullable-Value $expectedFrom) -ne (Nullable-Value $observation.request.fromYear) -or
                (Nullable-Value $expectedTo) -ne (Nullable-Value $observation.request.toYear)) {
            Fail "request filter mismatch for $caseId."
        }
        if ([int]$observation.request.topK -ne $requestedTopK) { Fail "request topK mismatch for $caseId." }
    }
    $observationById[$caseId] = $observation
}
if ($observationById.Count -ne $cases.Count) { Fail "observation count does not match cases count." }
foreach ($case in $cases) { if (-not $observationById.ContainsKey([string]$case.caseId)) { Fail "missing observation for $($case.caseId)." } }
if ($topKs.Count -ne 1) { Fail "observations do not share one topK." }
if ($embeddingVersions.Count -ne 1) { Fail "observations do not share one active embedding version." }
if ($runIds.Count -gt 1) { Fail "observations contain multiple run IDs." }
$topK = [int]@($topKs.Keys)[0]
$runId = if ($runIds.Count -eq 1) { [string]@($runIds.Keys)[0] } else { "LEGACY_UNVERSIONED" }

$caseResults = @()
foreach ($case in $cases) {
    $observation = $observationById[[string]$case.caseId]
    $ranked = @($observation.rankedPaperIds | ForEach-Object { [long]$_ })
    $scores = @($observation.scores | ForEach-Object { [double]$_ })
    $relevant = @($case.relevantPaperIds | ForEach-Object { [long]$_ })
    $rankByPaper = @{}
    for ($index = 0; $index -lt $ranked.Count; $index++) { $rankByPaper[$ranked[$index]] = $index + 1 }
    $hitsAt = @{}
    foreach ($k in @(1, 3, 5)) { $hitsAt[$k] = @($relevant | Where-Object { $rankByPaper.ContainsKey($_) -and $rankByPaper[$_] -le $k }).Count }
    $firstRank = $relevant | Where-Object { $rankByPaper.ContainsKey($_) } | ForEach-Object { $rankByPaper[$_] } | Sort-Object | Select-Object -First 1
    $mrr = if ($null -eq $firstRank) { 0.0 } else { 1.0 / [double]$firstRank }
    $caseResults += [pscustomobject][ordered]@{
        caseId = [string]$case.caseId
        split = [string]$case.split
        caseIntent = [string]$case.caseIntent
        relevantCount = $relevant.Count
        observedStatus = [string]$observation.status
        rankedPaperIds = $ranked
        scores = $scores
        top1Score = if ($scores.Count -eq 0) { $null } else { $scores[0] }
        lowestReturnedScore = if ($scores.Count -eq 0) { $null } else { $scores[-1] }
        recallAt1 = if ($relevant.Count -eq 0) { $null } else { [math]::Round($hitsAt[1] / [double]$relevant.Count, 10) }
        recallAt3 = if ($relevant.Count -eq 0) { $null } else { [math]::Round($hitsAt[3] / [double]$relevant.Count, 10) }
        recallAt5 = if ($relevant.Count -eq 0) { $null } else { [math]::Round($hitsAt[5] / [double]$relevant.Count, 10) }
        hitAt5 = if ($relevant.Count -eq 0) { $null } else { $hitsAt[5] -gt 0 }
        mrr = if ($relevant.Count -eq 0) { $null } else { [math]::Round($mrr, 10) }
    }
}

$allSummary = Build-MetricSummary $caseResults $topK
$tuningSummary = Build-MetricSummary @($caseResults | Where-Object { $_.split -eq "TUNING" }) $topK
$fixedSummary = Build-MetricSummary @($caseResults | Where-Object { $_.split -eq "FIXED_ACCEPTANCE" }) $topK
$semanticNegativeCount = @($caseResults | Where-Object { $_.caseIntent -eq "no_relevant_result" }).Count
$thresholdStatus = if ($semanticNegativeCount -lt 12) { "UNMEASURED_INSUFFICIENT_REVIEWED_SEMANTIC_NEGATIVES" } else { "READY_FOR_TUNING_ONLY" }
$reviewBasis = if ($null -ne $manifest.review) { [string]$manifest.review.groundTruthStatus } else { [string]$manifest.groundTruthStatus }

$report = [ordered]@{
    schemaVersion = "rag-reviewed-metrics-v0.3"
    datasetId = [string]$manifest.datasetId
    metricKind = "REVIEWED_RETRIEVAL_OBSERVATION"
    reviewBasis = $reviewBasis
    computedAtUtc = [DateTime]::UtcNow.ToString("o")
    runId = $runId
    activeEmbeddingVersion = [string]@($embeddingVersions.Keys)[0]
    topK = $topK
    reviewedCaseCount = $cases.Count
    formalMetrics = $allSummary.metrics
    splitMetrics = [ordered]@{
        tuning = $tuningSummary
        fixedAcceptance = $fixedSummary
    }
    relatednessThresholdCalibration = [ordered]@{
        status = $thresholdStatus
        reviewedSemanticNegativeCaseCount = $semanticNegativeCount
        selectedThreshold = "UNMEASURED"
        reason = "A threshold must be tuned on a larger reviewed semantic-negative tuning set and validated once on a fixed holdout."
    }
    caseResults = $caseResults
    inputEvidence = [ordered]@{
        casesLfSha256 = $actualManifestHash
        manifestSha256 = Sha256-File $ManifestPath
        observationSha256 = Sha256-File $ObservationPath
        metricScriptSha256 = Sha256-File $PSCommandPath
    }
    limitations = @(
        "Metrics are based on one captured retrieval observation per case.",
        "Empty relevant sets are excluded from Recall and MRR and are measured through explicit rejection rates.",
        "The semantic-negative sample is too small to select or validate a relatedness threshold."
    )
}

[IO.File]::WriteAllText($resolvedOutput, (($report | ConvertTo-Json -Depth 12) + "`n"), $utf8NoBom)
Write-Output "[RAG_REVIEWED_METRICS] outputPath=$resolvedOutput runId=$runId reviewedCaseCount=$($cases.Count) RecallAt1=$($report.formalMetrics.'Recall@1') RecallAt3=$($report.formalMetrics.'Recall@3') RecallAt5=$($report.formalMetrics.'Recall@5') HitAt5=$($report.formalMetrics.'Hit@5') MRR=$($report.formalMetrics.MRR) semanticNegativeRejectionRate=$($report.formalMetrics.semanticNegativeRejectionRate) thresholdStatus=$thresholdStatus"
