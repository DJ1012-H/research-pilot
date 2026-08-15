[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DatasetPath,
    [Parameter(Mandatory = $true)][string]$ValidatorPath,
    [Parameter(Mandatory = $true)][string]$OutputRoot,
    [Parameter(Mandatory = $true)]
    [ValidateSet("TUNING", "FIXED_HOLDOUT")]
    [string]$Split,
    [ValidatePattern("^[a-z0-9][a-z0-9._-]{0,63}$")]
    [string]$RunLabel = "v0.1",
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [ValidateRange(5, 20)][int]$TopK = 5,
    [string]$ParameterDecisionPath,
    [switch]$ConfirmRealModelCost,
    [switch]$ConfirmFrozenParameters
)

$ErrorActionPreference = "Stop"
$utf8NoBom = New-Object Text.UTF8Encoding($false)

function Fail([string]$Message) { throw "RAG v3-lite evaluation failed closed: $Message" }

function Sha256-Bytes([byte[]]$Bytes) {
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try { return (($sha256.ComputeHash($Bytes) | ForEach-Object { $_.ToString("x2") }) -join "") }
    finally { $sha256.Dispose() }
}

function Sha256-File([string]$Path) {
    return Sha256-Bytes ([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Path).Path))
}

function Sha256-Text([string]$Text) {
    return Sha256-Bytes ([Text.Encoding]::UTF8.GetBytes($Text))
}

function Read-Json([string]$Path, [string]$Name) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { Fail "$Name was not found: $Path" }
    try { return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json }
    catch { Fail "$Name is not valid JSON." }
}

function Require-Property($Object, [string]$Name, [string]$Context) {
    if ($null -eq $Object -or $Name -notin @($Object.PSObject.Properties.Name)) {
        Fail "$Context is missing property $Name."
    }
    return $Object.$Name
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

function Normalize-BaseUrl([string]$Url) {
    if ($Url -notmatch "^https?://[^\s/]+(?:[:/].*)?$") { Fail "BaseUrl is not an absolute HTTP URL." }
    return $Url.TrimEnd("/")
}

function Post-Json([string]$Uri, $Body, [string]$Name) {
    $json = $Body | ConvertTo-Json -Depth 8 -Compress
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $Uri `
            -ContentType "application/json; charset=utf-8" `
            -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 150
    } catch { Fail "$Name request failed: $($_.Exception.Message)" }
    if ($response.StatusCode -ne 200) { Fail "$Name returned HTTP $($response.StatusCode)." }
    try { return $response.Content | ConvertFrom-Json }
    catch { Fail "$Name returned invalid JSON." }
}

function Average($Rows, [string]$Property) {
    $values = @($Rows | ForEach-Object { $_.$Property } | Where-Object { $null -ne $_ })
    if ($values.Count -eq 0) { return "UNMEASURED" }
    return [math]::Round((($values | Measure-Object -Average).Average), 10)
}

function Rate([int]$Numerator, [int]$Denominator) {
    if ($Denominator -eq 0) { return "UNMEASURED" }
    return [math]::Round(($Numerator / [double]$Denominator), 10)
}

if (-not $ConfirmRealModelCost) {
    Fail "-ConfirmRealModelCost is required because every semantic case may call the configured real model."
}

$dataset = [IO.Path]::GetFullPath($DatasetPath)
$validator = [IO.Path]::GetFullPath($ValidatorPath)
$output = [IO.Path]::GetFullPath($OutputRoot)
if (-not (Test-Path -LiteralPath $dataset -PathType Container)) { Fail "DatasetPath was not found." }
if (-not (Test-Path -LiteralPath $validator -PathType Leaf)) { Fail "ValidatorPath was not found." }
if (-not (Test-Path -LiteralPath $output -PathType Container)) { Fail "OutputRoot must already exist." }

$manifestPath = Join-Path $dataset "manifest.json"
$manifest = Read-Json $manifestPath "dataset manifest"
if ([string]$manifest.datasetId -ne "rag-retrieval-v3-lite") { Fail "unexpected datasetId." }
if ([string]$manifest.review.groundTruthStatus -ne "USER_AUDITED_CODEX_REVIEWED") {
    Fail "dataset does not have the required user-audited review state."
}
$manifestHash = Sha256-File $manifestPath

$validationText = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $validator -DatasetPath $dataset
if ($LASTEXITCODE -ne 0) { Fail "offline dataset validation failed." }
try { $validation = (@($validationText) -join "`n") | ConvertFrom-Json }
catch { Fail "offline dataset validator did not return JSON." }
if ([string]$validation.status -ne "PASS" -or [int]$validation.caseCount -ne 24) {
    Fail "offline dataset validator did not pass the frozen 24-case contract."
}

$caseFileName = if ($Split -eq "TUNING") { "tuning-cases.json" } else { "fixed-holdout-cases.json" }
$casesPath = Join-Path $dataset $caseFileName
$cases = @((Read-Json $casesPath "$Split cases").GetEnumerator())
if ($cases.Count -ne 12) { Fail "$Split must contain exactly 12 cases." }
foreach ($case in $cases) {
    if ([string]$case.split -ne $Split) { Fail "case $($case.caseId) is in the wrong split file." }
}

$decision = $null
if ($Split -eq "FIXED_HOLDOUT") {
    if (-not $ConfirmFrozenParameters) { Fail "fixed holdout requires -ConfirmFrozenParameters." }
    if ([string]::IsNullOrWhiteSpace($ParameterDecisionPath)) { Fail "fixed holdout requires ParameterDecisionPath." }
    $decision = Read-Json ([IO.Path]::GetFullPath($ParameterDecisionPath)) "parameter decision"
    if ([string]$decision.status -ne "FROZEN_AFTER_TUNING") { Fail "parameter decision is not frozen." }
    if ([string]$decision.datasetManifestSha256 -cne $manifestHash) { Fail "parameter decision dataset hash mismatch." }
    if ([int]$decision.topK -ne $TopK) { Fail "TopK differs from the frozen parameter decision." }
    if (-not [bool]$decision.fixedHoldoutRunAllowed) { Fail "parameter decision does not allow the fixed holdout." }
    $runDirectory = Join-Path $output "fixed-holdout-v0.1"
} else {
    $runDirectory = Join-Path $output ("tuning-" + $RunLabel)
}
if (Test-Path -LiteralPath $runDirectory) { Fail "run directory already exists and will not be overwritten: $runDirectory" }
New-Item -ItemType Directory -Path $runDirectory | Out-Null

$baseUrl = Normalize-BaseUrl $BaseUrl
$runId = "rag-v3l-{0}-{1}-{2}" -f $Split.ToLowerInvariant(),
    [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssfffZ"), ([guid]::NewGuid().ToString("N").Substring(0, 8))
$startedAt = [DateTime]::UtcNow.ToString("o")
$observations = @()
$observationPath = Join-Path $runDirectory "case-observations.jsonl"
[IO.File]::WriteAllText($observationPath, "", $utf8NoBom)

foreach ($case in $cases) {
    $caseId = [string]$case.caseId
    $question = [string]$case.queryText
    $request = [ordered]@{ question = $question; topK = $TopK }
    $retrievalRequest = [ordered]@{ query = $question; topK = $TopK; segmentTypes = @("ABSTRACT") }
    if ($null -ne $case.filter.fromYear) {
        $request.fromYear = [int]$case.filter.fromYear
        $retrievalRequest.fromYear = [int]$case.filter.fromYear
    }
    if ($null -ne $case.filter.toYear) {
        $request.toYear = [int]$case.filter.toYear
        $retrievalRequest.toYear = [int]$case.filter.toYear
    }

    $clientWatch = [Diagnostics.Stopwatch]::StartNew()
    $retrieval = Post-Json "$baseUrl/api/research/retrieve" $retrievalRequest "retrieval $caseId"
    $answer = Post-Json "$baseUrl/api/research/ask" $request "answer $caseId"
    $clientWatch.Stop()

    $retrievalStatus = [string](Require-Property $retrieval "status" "retrieval $caseId")
    if ($retrievalStatus -notin @("SUCCESS", "NO_TRUSTED_RESULTS", "FAILED")) {
        Fail "retrieval $caseId returned unknown status $retrievalStatus."
    }
    $answerStatus = [string](Require-Property $answer "status" "answer $caseId")
    if ($answerStatus -notin @("SUCCESS", "INSUFFICIENT_EVIDENCE", "FAILED")) {
        Fail "answer $caseId returned unknown status $answerStatus."
    }

    $rankedResults = @()
    $previousScore = [double]::PositiveInfinity
    $rank = 0
    foreach ($hit in @(Require-Property $retrieval "results" "retrieval $caseId")) {
        $rank++
        $score = Require-FiniteDouble $hit.score "retrieval score for $caseId"
        if ($score -gt $previousScore) { Fail "retrieval scores are not descending for $caseId." }
        $previousScore = $score
        $doi = ([string]$hit.normalizedDoi).ToLowerInvariant()
        if ([string]::IsNullOrWhiteSpace($doi)) { Fail "retrieval DOI is blank for $caseId." }
        $rankedResults += [pscustomobject][ordered]@{
            rank = $rank
            paperId = [long]$hit.paperId
            normalizedDoi = $doi
            score = $score
            segmentType = [string]$hit.matchedSegmentType
        }
    }
    if ($rankedResults.Count -gt $TopK) { Fail "retrieval returned more than TopK for $caseId." }

    $diagnostics = Require-Property $answer "diagnostics" "answer $caseId"
    $failureDetailCode = if ("failureDetailCode" -in @($diagnostics.PSObject.Properties.Name)) {
        [string]$diagnostics.failureDetailCode
    } else {
        $null
    }
    $judgeCalls = Require-NonNegativeInt $diagnostics.relevanceJudgeCallCount "judge calls for $caseId"
    $answerCalls = Require-NonNegativeInt $diagnostics.answerModelCallCount "answer calls for $caseId"
    $totalCalls = Require-NonNegativeInt $diagnostics.modelCallCount "total calls for $caseId"
    $repairCount = Require-NonNegativeInt $diagnostics.repairCount "repair count for $caseId"
    $admittedCount = Require-NonNegativeInt $diagnostics.admittedEvidenceCount "admitted count for $caseId"
    $generationCount = Require-NonNegativeInt $diagnostics.generationEvidenceCount "generation count for $caseId"
    if ($totalCalls -ne $judgeCalls + $answerCalls -or $judgeCalls -gt 1 -or $answerCalls -gt 2 -or $repairCount -gt 1) {
        Fail "model-call diagnostics violate the bounded contract for $caseId."
    }
    if ($generationCount -gt $admittedCount -or ($answerCalls -eq 0 -and $generationCount -ne 0)) {
        Fail "evidence diagnostics are inconsistent for $caseId."
    }

    $citations = @()
    foreach ($citation in @(Require-Property $answer "citations" "answer $caseId")) {
        $doi = ([string]$citation.normalizedDoi).ToLowerInvariant()
        if ([string]::IsNullOrWhiteSpace($doi)) { Fail "citation DOI is blank for $caseId." }
        $citations += [pscustomobject][ordered]@{
            citationId = [string]$citation.citationId
            paperId = [long]$citation.paperId
            normalizedDoi = $doi
            score = if ($null -eq $citation.score) { $null } else { Require-FiniteDouble $citation.score "citation score for $caseId" }
        }
    }
    $answerText = [string](Require-Property $answer "answer" "answer $caseId")
    if ($answerStatus -ne "SUCCESS" -and ($answerText.Length -ne 0 -or $citations.Count -ne 0)) {
        Fail "non-success answer published text or citations for $caseId."
    }

    $relevantDois = @($case.relevantDois | ForEach-Object { ([string]$_).ToLowerInvariant() })
    $relevantLookup = @{}
    foreach ($doi in $relevantDois) { $relevantLookup[$doi] = $true }
    $rankByDoi = @{}
    foreach ($hit in $rankedResults) { if (-not $rankByDoi.ContainsKey($hit.normalizedDoi)) { $rankByDoi[$hit.normalizedDoi] = $hit.rank } }
    $hitsAt1 = @($relevantDois | Where-Object { $rankByDoi.ContainsKey($_) -and $rankByDoi[$_] -le 1 }).Count
    $hitsAt3 = @($relevantDois | Where-Object { $rankByDoi.ContainsKey($_) -and $rankByDoi[$_] -le 3 }).Count
    $hitsAt5 = @($relevantDois | Where-Object { $rankByDoi.ContainsKey($_) -and $rankByDoi[$_] -le 5 }).Count
    $firstRank = $relevantDois | Where-Object { $rankByDoi.ContainsKey($_) } | ForEach-Object { $rankByDoi[$_] } | Sort-Object | Select-Object -First 1
    $relevantCitationCount = @($citations | Where-Object { $relevantLookup.ContainsKey($_.normalizedDoi) }).Count
    $expectedAnswer = [string]$case.expectedOutcome -eq "ANSWER"
    $isSemanticNegative = ([string]$case.caseIntent).StartsWith("semantic_negative_", [StringComparison]::Ordinal)
    $isDeterministicEmpty = [string]$case.caseIntent -eq "deterministic_empty_year"
    $positivePass = $expectedAnswer -and $answerStatus -eq "SUCCESS" -and $citations.Count -gt 0 -and $relevantCitationCount -gt 0
    $negativePass = (-not $expectedAnswer) -and $answerStatus -eq "INSUFFICIENT_EVIDENCE" -and
        $answerText.Length -eq 0 -and $citations.Count -eq 0 -and $answerCalls -eq 0

    $observations += [pscustomobject][ordered]@{
        schemaVersion = "rag-v3-lite-case-observation-v0.1"
        runId = $runId
        caseId = $caseId
        split = $Split
        queryLanguage = [string]$case.queryLanguage
        caseIntent = [string]$case.caseIntent
        expectedOutcome = [string]$case.expectedOutcome
        relevantDois = $relevantDois
        request = [ordered]@{
            topK = $TopK
            fromYear = $case.filter.fromYear
            toYear = $case.filter.toYear
            questionUtf8Sha256 = Sha256-Text $question
            questionLength = $question.Length
        }
        retrieval = [ordered]@{
            status = $retrievalStatus
            elapsedMs = Require-NonNegativeInt $retrieval.elapsedMs "retrieval elapsedMs for $caseId"
            rankedResults = $rankedResults
            recallAt1 = if ($relevantDois.Count -eq 0) { $null } else { [math]::Round($hitsAt1 / [double]$relevantDois.Count, 10) }
            recallAt3 = if ($relevantDois.Count -eq 0) { $null } else { [math]::Round($hitsAt3 / [double]$relevantDois.Count, 10) }
            recallAt5 = if ($relevantDois.Count -eq 0) { $null } else { [math]::Round($hitsAt5 / [double]$relevantDois.Count, 10) }
            hitAt5 = if ($relevantDois.Count -eq 0) { $null } else { $hitsAt5 -gt 0 }
            mrr = if ($relevantDois.Count -eq 0) { $null } elseif ($null -eq $firstRank) { 0.0 } else { [math]::Round(1.0 / [double]$firstRank, 10) }
        }
        answer = [ordered]@{
            status = $answerStatus
            answerLength = $answerText.Length
            answerUtf8Sha256 = if ($answerText.Length -eq 0) { $null } else { Sha256-Text $answerText }
            citations = $citations
            relevantCitationCount = $relevantCitationCount
            allCitationsRelevant = $citations.Count -gt 0 -and $relevantCitationCount -eq $citations.Count
            relevanceJudgeCallCount = $judgeCalls
            answerModelCallCount = $answerCalls
            modelCallCount = $totalCalls
            repairCount = $repairCount
            admittedEvidenceCount = $admittedCount
            generationEvidenceCount = $generationCount
            elapsedMs = Require-NonNegativeInt $answer.elapsedMs "answer elapsedMs for $caseId"
            failureCode = [string]$diagnostics.failureCode
            failureDetailCode = $failureDetailCode
        }
        expectedAnswer = $expectedAnswer
        semanticNegative = $isSemanticNegative
        deterministicEmpty = $isDeterministicEmpty
        outcomePass = if ($expectedAnswer) { $positivePass } else { $negativePass }
        clientElapsedMs = $clientWatch.ElapsedMilliseconds
    }
    [IO.File]::AppendAllText(
        $observationPath,
        (($observations[-1] | ConvertTo-Json -Depth 12 -Compress) + "`n"),
        $utf8NoBom)
    Write-Output "[RAG_V3_CASE] caseId=$caseId split=$Split answerStatus=$answerStatus outcomePass=$($observations[-1].outcomePass) modelCalls=$totalCalls"
}

$positive = @($observations | Where-Object { $_.expectedAnswer })
$negative = @($observations | Where-Object { -not $_.expectedAnswer })
$semanticNegative = @($observations | Where-Object { $_.semanticNegative })
$deterministicEmpty = @($observations | Where-Object { $_.deterministicEmpty })
$passed = @($observations | Where-Object { $_.outcomePass }).Count
$positiveAnswerSuccess = @($positive | Where-Object { $_.answer.status -eq "SUCCESS" }).Count
$positiveEvidenceHit = @($positive | Where-Object { $_.answer.relevantCitationCount -gt 0 }).Count
$allCitationCount = (@($positive | ForEach-Object { $_.answer.citations }) | Measure-Object).Count
$relevantCitationTotal = (@($positive | ForEach-Object { $_.answer.relevantCitationCount }) | Measure-Object -Sum).Sum
if ($null -eq $relevantCitationTotal) { $relevantCitationTotal = 0 }
$negativeRefused = @($negative | Where-Object { $_.answer.status -eq "INSUFFICIENT_EVIDENCE" -and $_.answer.answerModelCallCount -eq 0 }).Count
$semanticRefused = @($semanticNegative | Where-Object { $_.answer.status -eq "INSUFFICIENT_EVIDENCE" -and $_.answer.answerModelCallCount -eq 0 }).Count
$deterministicRefused = @($deterministicEmpty | Where-Object { $_.answer.status -eq "INSUFFICIENT_EVIDENCE" -and $_.answer.modelCallCount -eq 0 }).Count
$failedResponses = @($observations | Where-Object { $_.retrieval.status -eq "FAILED" -or $_.answer.status -eq "FAILED" }).Count
$infrastructureFailureCodes = @(
    "RAG_RETRIEVAL_FAILED",
    "RAG_RELEVANCE_JUDGE_UNAVAILABLE",
    "RAG_GENERATION_UNAVAILABLE",
    "RAG_ANSWER_DEADLINE_EXCEEDED",
    "RAG_ANSWER_FAILED"
)
$infrastructureFailures = @($observations | Where-Object {
    $_.retrieval.status -eq "FAILED" -or $_.answer.failureCode -in $infrastructureFailureCodes
}).Count
$modelContractFailures = @($observations | Where-Object {
    $_.answer.failureCode -in @(
        "RAG_EVIDENCE_ADMISSION_INVALID",
        "RAG_ANSWER_OUTPUT_INVALID",
        "RAG_ANSWER_VALIDATION_FAILED")
}).Count
$positiveRetrieval = @($positive | ForEach-Object { $_.retrieval })

$metrics = [ordered]@{
    caseOutcomeAccuracy = Rate $passed $observations.Count
    positiveAnswerSuccessRate = Rate $positiveAnswerSuccess $positive.Count
    positiveEvidenceHitRate = Rate $positiveEvidenceHit $positive.Count
    positiveCitationPrecision = Rate ([int]$relevantCitationTotal) $allCitationCount
    positiveRetrievalRecallAt1 = Average $positiveRetrieval "recallAt1"
    positiveRetrievalRecallAt3 = Average $positiveRetrieval "recallAt3"
    positiveRetrievalRecallAt5 = Average $positiveRetrieval "recallAt5"
    positiveRetrievalHitAt5 = Rate (@($positive | Where-Object { $_.retrieval.hitAt5 }).Count) $positive.Count
    positiveRetrievalMrr = Average $positiveRetrieval "mrr"
    negativeRefusalRate = Rate $negativeRefused $negative.Count
    semanticNegativeRefusalRate = Rate $semanticRefused $semanticNegative.Count
    deterministicEmptyRefusalRate = Rate $deterministicRefused $deterministicEmpty.Count
    failedResponseCount = $failedResponses
    infrastructureFailureCount = $infrastructureFailures
    modelContractFailureCount = $modelContractFailures
    totalModelCallCount = (@($observations | ForEach-Object { $_.answer.modelCallCount }) | Measure-Object -Sum).Sum
    totalRepairCount = (@($observations | ForEach-Object { $_.answer.repairCount }) | Measure-Object -Sum).Sum
}

$acceptance = $null
$status = "MEASURED_TUNING_NOT_ACCEPTANCE"
if ($Split -eq "FIXED_HOLDOUT") {
    $thresholds = $decision.acceptanceThresholds
    $checks = [ordered]@{
        caseOutcomeAccuracy = [double]$metrics.caseOutcomeAccuracy -ge [double]$thresholds.caseOutcomeAccuracy
        positiveAnswerSuccessRate = [double]$metrics.positiveAnswerSuccessRate -ge [double]$thresholds.positiveAnswerSuccessRate
        positiveEvidenceHitRate = [double]$metrics.positiveEvidenceHitRate -ge [double]$thresholds.positiveEvidenceHitRate
        positiveCitationPrecision = [double]$metrics.positiveCitationPrecision -ge [double]$thresholds.positiveCitationPrecision
        positiveRetrievalHitAt5 = [double]$metrics.positiveRetrievalHitAt5 -ge [double]$thresholds.positiveRetrievalHitAt5
        semanticNegativeRefusalRate = [double]$metrics.semanticNegativeRefusalRate -ge [double]$thresholds.semanticNegativeRefusalRate
        deterministicEmptyRefusalRate = [double]$metrics.deterministicEmptyRefusalRate -ge [double]$thresholds.deterministicEmptyRefusalRate
        failedResponseCount = [int]$metrics.failedResponseCount -eq 0
        infrastructureFailureCount = [int]$metrics.infrastructureFailureCount -eq 0
    }
    $failedChecks = @($checks.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object { $_.Key })
    $status = if ($failedChecks.Count -eq 0) { "PASS" } else { "FAIL" }
    $acceptance = [ordered]@{ status = $status; checks = $checks; failedChecks = $failedChecks }
}

$completedAt = [DateTime]::UtcNow.ToString("o")
$reportPath = Join-Path $runDirectory "evaluation-report.json"
$metadataPath = Join-Path $runDirectory "run-metadata.json"

$report = [ordered]@{
    schemaVersion = "rag-v3-lite-evaluation-report-v0.1"
    status = $status
    datasetId = [string]$manifest.datasetId
    split = $Split
    runId = $runId
    caseCount = $observations.Count
    positiveCaseCount = $positive.Count
    negativeCaseCount = $negative.Count
    semanticNegativeCaseCount = $semanticNegative.Count
    deterministicEmptyCaseCount = $deterministicEmpty.Count
    topK = $TopK
    metrics = $metrics
    acceptance = $acceptance
    limitations = @(
        "Labels are USER_AUDITED_CODEX_REVIEWED, not independent Ground Truth.",
        "This 12-case split is interview-readiness evidence, not a production benchmark or SLA.",
        "Answer text is not retained; only its UTF-8 SHA-256 and length are recorded."
    )
}
[IO.File]::WriteAllText($reportPath, (($report | ConvertTo-Json -Depth 12) + "`n"), $utf8NoBom)

$mainRoot = Split-Path -Parent $PSScriptRoot
$mainCommit = (& git -C $mainRoot rev-parse HEAD 2>$null | Select-Object -First 1)
$mainDirty = (& git -C $mainRoot status --porcelain --untracked-files=no 2>$null | Out-String).Trim()
$metadata = [ordered]@{
    schemaVersion = "rag-v3-lite-run-metadata-v0.1"
    runId = $runId
    startedAtUtc = $startedAt
    completedAtUtc = $completedAt
    baseUrl = $baseUrl
    split = $Split
    topK = $TopK
    datasetManifestSha256 = $manifestHash
    caseFileSha256 = Sha256-File $casesPath
    validatorSha256 = Sha256-File $validator
    runnerSha256 = Sha256-File $PSCommandPath
    parameterDecisionSha256 = if ($null -eq $decision) { $null } else { Sha256-File ([IO.Path]::GetFullPath($ParameterDecisionPath)) }
    caseObservationsSha256 = Sha256-File $observationPath
    evaluationReportSha256 = Sha256-File $reportPath
    mainCommit = [string]$mainCommit
    mainTrackedWorktreeState = if ([string]::IsNullOrWhiteSpace($mainDirty)) { "CLEAN" } else { "DIRTY" }
}
[IO.File]::WriteAllText($metadataPath, (($metadata | ConvertTo-Json -Depth 8) + "`n"), $utf8NoBom)

Write-Output "[RAG_V3_EVAL] status=$status split=$Split runId=$runId caseCount=$($observations.Count) output=$runDirectory"
if ($Split -eq "FIXED_HOLDOUT" -and $status -ne "PASS") { exit 1 }
