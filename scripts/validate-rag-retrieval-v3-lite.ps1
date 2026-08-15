[CmdletBinding()]
param(
    [string]$DatasetPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($DatasetPath)) {
    $DatasetPath = Join-Path $PSScriptRoot "..\eval\rag-retrieval-v3-lite"
}

function Fail([string]$Message) {
    throw "RAG v3-lite validation failed: $Message"
}

function Get-Sha256Text([string]$Value) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        return ([System.BitConverter]::ToString($sha.ComputeHash($bytes))).Replace("-", "").ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
    }
}

function Get-NullableYearText($Value) {
    if ($null -eq $Value) { return "null" }
    return [string][int]$Value
}

function Get-CaseCanonicalText($Case) {
    $dois = @($Case.relevantDois | ForEach-Object { ([string]$_).ToLowerInvariant() }) -join ","
    return @(
        [string]$Case.caseId,
        [string]$Case.split,
        [string]$Case.queryLanguage,
        [string]$Case.queryText,
        [string]$Case.caseIntent,
        (Get-NullableYearText $Case.filter.fromYear),
        (Get-NullableYearText $Case.filter.toYear),
        [string]$Case.expectedOutcome,
        $dois,
        [string]$Case.relevanceReason,
        [string]$Case.labelScope,
        [string]$Case.labelProvenance,
        [string]$Case.reviewer,
        [string]$Case.reviewStatus
    ) -join "|"
}

function Read-Json([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { Fail "missing file $Path" }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
}

$dataset = (Resolve-Path -LiteralPath $DatasetPath).Path
$catalogPath = Join-Path $dataset "candidate-catalog.json"
$tuningPath = Join-Path $dataset "tuning-cases.json"
$holdoutPath = Join-Path $dataset "fixed-holdout-cases.json"
$schemaPath = Join-Path $dataset "schema\retrieval-case.schema.json"
$reviewNotesPath = Join-Path $dataset "review-notes.md"
$readmePath = Join-Path $dataset "README.md"
$manifestPath = Join-Path $dataset "manifest.json"

$catalog = Read-Json $catalogPath
$tuning = @((Read-Json $tuningPath).GetEnumerator())
$holdout = @((Read-Json $holdoutPath).GetEnumerator())
$manifest = Read-Json $manifestPath
Read-Json $schemaPath | Out-Null

if (@($catalog.papers).Count -ne 18) { Fail "candidate catalog must contain exactly 18 papers" }
if ([int]$catalog.qdrantSnapshot.pointCount -ne 27) { Fail "candidate point count drifted from the frozen value 27" }
if ([int]$catalog.qdrantSnapshot.abstractSegmentCount -ne 9) { Fail "abstract count drifted from the frozen value 9" }

$doiPattern = '^10\.[0-9]{4,9}/[-._;()/:a-z0-9]+$'
$catalogByDoi = @{}
$paperIds = @{}
foreach ($paper in @($catalog.papers)) {
    $doi = ([string]$paper.normalizedDoi).ToLowerInvariant()
    if ($doi -cne [string]$paper.normalizedDoi -or $doi -notmatch $doiPattern) { Fail "invalid normalized catalog DOI $doi" }
    if ($catalogByDoi.ContainsKey($doi)) { Fail "duplicate catalog DOI $doi" }
    $id = [long]$paper.paperIdSnapshot
    if ($id -lt 1 -or $paperIds.ContainsKey($id)) { Fail "invalid or duplicate paperIdSnapshot $id" }
    if ([string]$paper.verificationStatus -ne "VERIFIED") { Fail "non-VERIFIED catalog paper $doi" }
    if ([bool]$paper.eligibleForAbstractRetrieval -ne ($null -ne $paper.abstractContentHash)) { Fail "abstract eligibility/hash mismatch for $doi" }
    $catalogByDoi[$doi] = $paper
    $paperIds[$id] = $true
}

if ($tuning.Count -ne 12 -or $holdout.Count -ne 12) { Fail "expected 12 tuning and 12 fixed-holdout cases" }
$allCases = @($tuning) + @($holdout)
$caseIds = @{}
$queryKeys = @{}
$expectedCaseProperties = @(
    "caseId", "split", "queryLanguage", "queryText", "caseIntent", "filter",
    "expectedOutcome", "relevantDois", "relevanceReason", "labelScope",
    "labelProvenance", "reviewer", "reviewStatus", "frozenHash"
)
$allowedCaseIntents = @(
    "cross_language_positive", "synonym_positive", "long_context_positive",
    "precise_topic_positive", "year_filter_positive", "survey_positive",
    "semantic_negative_ood", "semantic_negative_in_domain",
    "semantic_negative_underspecified", "deterministic_empty_year"
)
foreach ($case in $allCases) {
    $caseId = [string]$case.caseId
    if ($caseId -notmatch '^rag-v3l-[0-9]{4}$' -or $caseIds.ContainsKey($caseId)) { Fail "invalid or duplicate caseId $caseId" }
    $caseIds[$caseId] = $true

    $actualCaseProperties = @($case.PSObject.Properties.Name)
    foreach ($propertyName in $expectedCaseProperties) {
        if ($propertyName -notin $actualCaseProperties) { Fail "missing property $propertyName in $caseId" }
    }
    foreach ($propertyName in $actualCaseProperties) {
        if ($propertyName -notin $expectedCaseProperties) { Fail "unexpected property $propertyName in $caseId" }
    }
    $filterProperties = @($case.filter.PSObject.Properties.Name)
    if ($filterProperties.Count -ne 2 -or "fromYear" -notin $filterProperties -or "toYear" -notin $filterProperties) { Fail "invalid filter shape for $caseId" }

    $queryText = [string]$case.queryText
    if ([string]::IsNullOrWhiteSpace($queryText) -or $queryText.Length -gt 500) { Fail "invalid queryText for $caseId" }
    $queryKey = $queryText.Trim().ToLowerInvariant()
    if ($queryKeys.ContainsKey($queryKey)) { Fail "duplicate query text for $caseId" }
    $queryKeys[$queryKey] = $true

    if ([string]$case.queryLanguage -notin @("zh", "en")) { Fail "invalid queryLanguage for $caseId" }
    if ([string]$case.caseIntent -notin $allowedCaseIntents) { Fail "invalid caseIntent for $caseId" }
    if ([string]$case.labelScope -ne "FROZEN_CATALOG_ABSTRACT_SEGMENTS") { Fail "invalid labelScope for $caseId" }
    if ([string]$case.reviewer -ne "codex") { Fail "unexpected reviewer for $caseId" }
    if ([string]$case.reviewStatus -ne "USER_AUDITED_CODEX_REVIEWED") { Fail "unexpected reviewStatus for $caseId" }
    if ([string]::IsNullOrWhiteSpace([string]$case.relevanceReason) -or ([string]$case.relevanceReason).Length -lt 20) { Fail "missing relevance reason for $caseId" }

    $fromYear = $case.filter.fromYear
    $toYear = $case.filter.toYear
    if ($null -ne $fromYear -and ([int]$fromYear -lt 1900 -or [int]$fromYear -gt 2100)) { Fail "invalid fromYear for $caseId" }
    if ($null -ne $toYear -and ([int]$toYear -lt 1900 -or [int]$toYear -gt 2100)) { Fail "invalid toYear for $caseId" }
    if ($null -ne $fromYear -and $null -ne $toYear -and [int]$fromYear -gt [int]$toYear) { Fail "reversed year filter for $caseId" }

    $relevantDois = @($case.relevantDois)
    if ([string]$case.expectedOutcome -eq "ANSWER" -and $relevantDois.Count -eq 0) { Fail "ANSWER case has no relevant DOI: $caseId" }
    if ([string]$case.expectedOutcome -eq "INSUFFICIENT_EVIDENCE" -and $relevantDois.Count -ne 0) { Fail "negative case has relevant DOI: $caseId" }
    if ([string]$case.expectedOutcome -notin @("ANSWER", "INSUFFICIENT_EVIDENCE")) { Fail "invalid expectedOutcome for $caseId" }

    $seenDois = @{}
    foreach ($rawDoi in $relevantDois) {
        $doi = ([string]$rawDoi).ToLowerInvariant()
        if ($doi -cne [string]$rawDoi -or $doi -notmatch $doiPattern) { Fail "invalid normalized DOI $rawDoi in $caseId" }
        if ($seenDois.ContainsKey($doi)) { Fail "duplicate DOI $doi in $caseId" }
        if (-not $catalogByDoi.ContainsKey($doi)) { Fail "DOI $doi in $caseId is absent from the frozen catalog" }
        if (-not [bool]$catalogByDoi[$doi].eligibleForAbstractRetrieval) { Fail "metadata-only DOI $doi cannot label ABSTRACT evidence in $caseId" }
        if ($null -ne $fromYear -and [int]$catalogByDoi[$doi].publicationYear -lt [int]$fromYear) { Fail "DOI $doi violates fromYear in $caseId" }
        if ($null -ne $toYear -and [int]$catalogByDoi[$doi].publicationYear -gt [int]$toYear) { Fail "DOI $doi violates toYear in $caseId" }
        $seenDois[$doi] = $true
    }

    $expectedHash = Get-Sha256Text (Get-CaseCanonicalText $case)
    if ([string]$case.frozenHash -cne $expectedHash) { Fail "case hash mismatch for $caseId; expected $expectedHash" }
}

foreach ($split in @(
    @{ Name = "TUNING"; Cases = $tuning },
    @{ Name = "FIXED_HOLDOUT"; Cases = $holdout }
)) {
    foreach ($case in @($split.Cases)) {
        if ([string]$case.split -ne [string]$split.Name) { Fail "case $($case.caseId) is in the wrong split file" }
    }
    $positive = @($split.Cases | Where-Object { $_.expectedOutcome -eq "ANSWER" }).Count
    $negative = @($split.Cases | Where-Object { $_.expectedOutcome -eq "INSUFFICIENT_EVIDENCE" }).Count
    $zh = @($split.Cases | Where-Object { $_.queryLanguage -eq "zh" }).Count
    $en = @($split.Cases | Where-Object { $_.queryLanguage -eq "en" }).Count
    if ($positive -ne 6 -or $negative -ne 6 -or $zh -ne 6 -or $en -ne 6) { Fail "$($split.Name) is not balanced 6/6 by outcome and language" }
}

$expectedManifestFiles = @(
    ".gitattributes",
    "candidate-catalog.json",
    "tuning-cases.json",
    "fixed-holdout-cases.json",
    "review-notes.md",
    "README.md",
    "schema/retrieval-case.schema.json",
    "user-audit-v0.1.json"
)
foreach ($relativePath in $expectedManifestFiles) {
    $record = @($manifest.files | Where-Object { $_.path -eq $relativePath })
    if ($record.Count -ne 1) { Fail "manifest must contain exactly one hash for $relativePath" }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $dataset $relativePath)).Hash.ToLowerInvariant()
    if ([string]$record[0].sha256 -cne $actualHash) { Fail "manifest hash mismatch for $relativePath" }
}

if ([int]$manifest.caseCount -ne 24 -or [int]$manifest.tuningCaseCount -ne 12 -or [int]$manifest.fixedHoldoutCaseCount -ne 12) { Fail "manifest case counts are invalid" }
if ([string]$manifest.review.groundTruthStatus -ne "USER_AUDITED_CODEX_REVIEWED") { Fail "manifest review status is invalid" }
if ([string]$manifest.metricsStatus -ne "UNMEASURED_DAY1_DATA_PREPARATION_ONLY") { Fail "Day 1 manifest must not claim measured metrics" }

[ordered]@{
    status = "PASS"
    datasetId = [string]$manifest.datasetId
    paperCount = @($catalog.papers).Count
    abstractEligiblePaperCount = @($catalog.papers | Where-Object { $_.eligibleForAbstractRetrieval }).Count
    caseCount = $allCases.Count
    tuningCaseCount = $tuning.Count
    fixedHoldoutCaseCount = $holdout.Count
    positiveCaseCount = @($allCases | Where-Object { $_.expectedOutcome -eq "ANSWER" }).Count
    negativeCaseCount = @($allCases | Where-Object { $_.expectedOutcome -eq "INSUFFICIENT_EVIDENCE" }).Count
    reviewStatus = [string]$manifest.review.groundTruthStatus
    metricsStatus = [string]$manifest.metricsStatus
} | ConvertTo-Json -Depth 5
