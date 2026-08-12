[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$CasesPath,
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [string]$BaseUrl = "http://localhost:8080",
    [ValidateRange(1, 20)][int]$TopK = 5,
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) { throw "RAG retrieval eval failed closed: $Message" }

function Read-JsonFile([string]$Path, [string]$Name) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { Fail "$Name was not found." }
    try { return (Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json) }
    catch { Fail "$Name is not valid JSON." }
}

function Sha256-Bytes([byte[]]$Bytes) {
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        return (($sha256.ComputeHash($Bytes) | ForEach-Object { $_.ToString('x2') }) -join '')
    } finally {
        $sha256.Dispose()
    }
}

function Read-Utf8BytesWithLf([string]$Path) {
    $bytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Path).Path)
    $text = [Text.Encoding]::UTF8.GetString($bytes)
    $text = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    return [Text.Encoding]::UTF8.GetBytes($text)
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

function Post-Json([string]$Uri, $Body, [string]$Name) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $Uri `
            -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 8 -Compress)
    } catch { Fail "$Name request failed." }
    if ($response.StatusCode -ne 200) { Fail "$Name returned HTTP $($response.StatusCode)." }
    try { return ($response.Content | ConvertFrom-Json) }
    catch { Fail "$Name returned invalid JSON." }
}

function Normalize-BaseUrl([string]$Url) {
    if ($Url -notmatch "^https?://[^\s/]+(?:[:/].*)?$") { Fail "BaseUrl is not an absolute HTTP URL." }
    return $Url.TrimEnd('/')
}

function Case-Hash($Case) {
    $canonical = "{0}|{1}|{2}|{3}|{4}" -f $Case.caseId, $Case.queryLanguage, $Case.queryText,
        $Case.relevanceJudgmentProvenance, $Case.reviewStatus
    return Sha256-Bytes ([Text.Encoding]::UTF8.GetBytes($canonical))
}

if (-not (Test-Path -LiteralPath $CasesPath -PathType Leaf)) { Fail "CasesPath was not found." }
if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) { Fail "ManifestPath was not found." }
$baseUrl = Normalize-BaseUrl $BaseUrl
$manifest = Read-JsonFile $ManifestPath "manifest"
$manifestCasesHash = Require-Text $manifest.casesLfSha256 "manifest.casesLfSha256"
$actualCasesHash = Sha256-Bytes (Read-Utf8BytesWithLf $CasesPath)
if ($actualCasesHash -ne $manifestCasesHash) {
    Fail "cases LF-SHA256 does not match manifest."
}

$cases = @()
foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $CasesPath).Path, [Text.Encoding]::UTF8)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try { $case = $line | ConvertFrom-Json }
    catch { Fail "cases.jsonl contains invalid JSON." }
    if ($null -eq $case.caseId -or $null -eq $case.frozenHash -or $null -eq $case.queryText) {
        Fail "a case is missing caseId, frozenHash, or queryText."
    }
    if ((Case-Hash $case) -ne [string]$case.frozenHash) {
        Fail "frozenHash mismatch for case $($case.caseId)."
    }
    if ([string]$case.reviewStatus -notin @("NEEDS_REVIEW", "REVIEWED")) {
        Fail "unknown reviewStatus for case $($case.caseId)."
    }
    if (@($case.relevantPaperIds).Count -gt 0) {
        foreach ($id in @($case.relevantPaperIds)) { if ([long]$id -lt 1) { Fail "invalid relevantPaperIds in $($case.caseId)." } }
    }
    $cases += $case
}
if ($cases.Count -eq 0) { Fail "no cases were found." }

$results = @()
foreach ($case in $cases) {
    $started = [Diagnostics.Stopwatch]::StartNew()
    $response = Post-Json "$baseUrl/api/research/retrieve" ([ordered]@{ query = $case.queryText; topK = $TopK }) "case $($case.caseId)"
    $started.Stop()
    $status = Require-Text $response.status "status for $($case.caseId)"
    $version = if ($null -eq $response.activeEmbeddingVersion) { $null } else { [string]$response.activeEmbeddingVersion }
    $candidateCount = Require-NonNegativeInt $response.qdrantCandidateCount "candidateCount for $($case.caseId)"
    $admittedCount = Require-NonNegativeInt $response.admittedPaperCount "admittedCount for $($case.caseId)"
    $filteredCount = Require-NonNegativeInt $response.filteredCount "filteredCount for $($case.caseId)"
    $requestedTopK = Require-NonNegativeInt $response.requestedTopK "requestedTopK for $($case.caseId)"
    $rankedPaperIds = @($response.results | ForEach-Object {
        $id = Require-NonNegativeInt $_.paperId "paperId for $($case.caseId)"
        if ($id -lt 1) { Fail "paperId must be positive for $($case.caseId)." }
        $id
    })
    $results += [ordered]@{
        caseId = [string]$case.caseId
        status = $status
        activeEmbeddingVersion = $version
        requestedTopK = $requestedTopK
        candidateCount = $candidateCount
        admittedCount = $admittedCount
        filteredCount = $filteredCount
        elapsedMs = [long]$started.ElapsedMilliseconds
        rankedPaperIds = $rankedPaperIds
    }
}

$reviewedCaseCount = @($cases | Where-Object { $_.reviewStatus -eq "REVIEWED" }).Count
$report = [ordered]@{
    datasetId = if ($null -ne $manifest.datasetId) { [string]$manifest.datasetId } else { "UNSPECIFIED" }
    caseCount = $cases.Count
    reviewedCaseCount = $reviewedCaseCount
    formalMetrics = [ordered]@{
        "Recall@1" = if ($reviewedCaseCount -eq 0) { "UNMEASURED" } else { "UNMEASURED_REQUIRES_SEPARATE_REVIEWED_METRIC_RUN" }
        "Recall@3" = if ($reviewedCaseCount -eq 0) { "UNMEASURED" } else { "UNMEASURED_REQUIRES_SEPARATE_REVIEWED_METRIC_RUN" }
        "Recall@5" = if ($reviewedCaseCount -eq 0) { "UNMEASURED" } else { "UNMEASURED_REQUIRES_SEPARATE_REVIEWED_METRIC_RUN" }
        MRR = if ($reviewedCaseCount -eq 0) { "UNMEASURED" } else { "UNMEASURED_REQUIRES_SEPARATE_REVIEWED_METRIC_RUN" }
    }
    observation = [ordered]@{
        successfulCalls = @($results | Where-Object { $_.status -ne "FAILED" }).Count
        totalCalls = $results.Count
        averageElapsedMs = [math]::Round((($results | Measure-Object -Property elapsedMs -Average).Average), 2)
    }
    results = $results
}

$json = $report | ConvertTo-Json -Depth 10
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    Write-Output $json
} else {
    $resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
    $resolvedCases = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $CasesPath).Path)
    $frozenRoot = [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $resolvedCases) ""))
    if ($resolvedOutput.StartsWith($frozenRoot, [StringComparison]::OrdinalIgnoreCase)) {
        Fail "OutputPath must not be inside the frozen cases directory."
    }
    Set-Content -LiteralPath $resolvedOutput -Value $json -Encoding UTF8
    Write-Output "[RAG_RETRIEVAL_EVAL] outputPath=$resolvedOutput reviewedCaseCount=$reviewedCaseCount RecallAt1=$($report.formalMetrics.'Recall@1') RecallAt3=$($report.formalMetrics.'Recall@3') RecallAt5=$($report.formalMetrics.'Recall@5') MRR=$($report.formalMetrics.MRR)"
}
