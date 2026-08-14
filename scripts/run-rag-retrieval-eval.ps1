[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$CasesPath,
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$QdrantBaseUrl = "http://127.0.0.1:6333",
    [ValidatePattern("^[A-Za-z0-9_-]{1,255}$")]
    [string]$CollectionName = "research_pilot_paper_segments_v1",
    [ValidateRange(1, 20)][int]$TopK = 5,
    [string]$OutputPath,
    [string]$RunMetadataPath
)

$ErrorActionPreference = "Stop"
$utf8NoBom = New-Object Text.UTF8Encoding($false)

function Fail([string]$Message) { throw "RAG retrieval eval failed closed: $Message" }

function Read-JsonFile([string]$Path, [string]$Name) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { Fail "$Name was not found." }
    try { return (Get-Content -Encoding UTF8 -Raw -LiteralPath $Path | ConvertFrom-Json) }
    catch { Fail "$Name is not valid JSON." }
}

function Sha256-Bytes([byte[]]$Bytes) {
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try { return (($sha256.ComputeHash($Bytes) | ForEach-Object { $_.ToString('x2') }) -join '') }
    finally { $sha256.Dispose() }
}

function Sha256-File([string]$Path) {
    return Sha256-Bytes ([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Path).Path))
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

function Require-FiniteDouble($Value, [string]$Name) {
    try { $number = [double]$Value } catch { Fail "$Name is not numeric." }
    if ([double]::IsNaN($number) -or [double]::IsInfinity($number)) { Fail "$Name is not finite." }
    return $number
}

function Normalize-BaseUrl([string]$Url, [string]$Name) {
    if ($Url -notmatch "^https?://[^\s/]+(?:[:/].*)?$") { Fail "$Name is not an absolute HTTP URL." }
    return $Url.TrimEnd('/')
}

function Post-Json([string]$Uri, $Body, [string]$Name) {
    $json = $Body | ConvertTo-Json -Depth 8 -Compress
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $Uri `
            -ContentType "application/json; charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($json)) `
            -TimeoutSec 60
    } catch { Fail "$Name request failed: $($_.Exception.Message)" }
    if ($response.StatusCode -ne 200) { Fail "$Name returned HTTP $($response.StatusCode)." }
    try { return ($response.Content | ConvertFrom-Json) }
    catch { Fail "$Name returned invalid JSON." }
}

function Get-Json([string]$Uri, [string]$Name) {
    try { $response = Invoke-WebRequest -UseBasicParsing -Method Get -Uri $Uri -TimeoutSec 10 }
    catch { Fail "$Name request failed: $($_.Exception.Message)" }
    if ($response.StatusCode -ne 200) { Fail "$Name returned HTTP $($response.StatusCode)." }
    try { return ($response.Content | ConvertFrom-Json) }
    catch { Fail "$Name returned invalid JSON." }
}

function Case-Hash($Case) {
    $canonical = "{0}|{1}|{2}|{3}|{4}" -f $Case.caseId, $Case.queryLanguage, $Case.queryText,
        $Case.relevanceJudgmentProvenance, $Case.reviewStatus
    return Sha256-Bytes ([Text.Encoding]::UTF8.GetBytes($canonical))
}

function Resolve-NewOutput([string]$Path, [string]$Name, [string[]]$Inputs) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return $null }
    $full = [IO.Path]::GetFullPath($Path)
    foreach ($input in $Inputs) {
        if ($full.Equals([IO.Path]::GetFullPath($input), [StringComparison]::OrdinalIgnoreCase)) {
            Fail "$Name must not overwrite an input file."
        }
    }
    if (Test-Path -LiteralPath $full) { Fail "$Name already exists; use a new versioned run path." }
    $parent = Split-Path -Parent $full
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) { Fail "$Name parent directory does not exist." }
    return $full
}

function Git-Value([string[]]$Arguments) {
    try {
        $value = & git -C $repoRoot @Arguments 2>$null
        if ($LASTEXITCODE -ne 0) { return "UNAVAILABLE" }
        return (@($value) -join "`n").Trim()
    } catch { return "UNAVAILABLE" }
}

$baseUrl = Normalize-BaseUrl $BaseUrl "BaseUrl"
$qdrantBaseUrl = Normalize-BaseUrl $QdrantBaseUrl "QdrantBaseUrl"
$manifest = Read-JsonFile $ManifestPath "manifest"
$manifestCasesHash = Require-Text $manifest.casesLfSha256 "manifest.casesLfSha256"
$actualCasesHash = Sha256-Bytes (Read-Utf8BytesWithLf $CasesPath)
if ($actualCasesHash -ne $manifestCasesHash) { Fail "cases LF-SHA256 does not match manifest." }
$candidateCatalogPath = $null
$candidateCatalogHash = $null
if ($null -ne $manifest.candidateCatalogPath -and
        -not [string]::IsNullOrWhiteSpace([string]$manifest.candidateCatalogPath)) {
    $catalogValue = [string]$manifest.candidateCatalogPath
    $candidateCatalogPath = if ([IO.Path]::IsPathRooted($catalogValue)) {
        [IO.Path]::GetFullPath($catalogValue)
    } else {
        [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent ([IO.Path]::GetFullPath($ManifestPath))) $catalogValue))
    }
    if (-not (Test-Path -LiteralPath $candidateCatalogPath -PathType Leaf)) {
        Fail "manifest candidate catalog was not found."
    }
    $candidateCatalogHash = Sha256-File $candidateCatalogPath
}

$cases = @()
$caseIds = @{}
foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $CasesPath).Path, [Text.Encoding]::UTF8)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try { $case = $line | ConvertFrom-Json } catch { Fail "cases.jsonl contains invalid JSON." }
    $caseId = Require-Text $case.caseId "caseId"
    if ($caseIds.ContainsKey($caseId)) { Fail "duplicate caseId: $caseId." }
    $caseIds[$caseId] = $true
    if ($null -eq $case.frozenHash -or $null -eq $case.queryText) { Fail "case $caseId is missing frozenHash or queryText." }
    if ((Case-Hash $case) -ne [string]$case.frozenHash) { Fail "frozenHash mismatch for case $caseId." }
    if ([string]$case.reviewStatus -notin @("NEEDS_REVIEW", "REVIEWED")) { Fail "unknown reviewStatus for case $caseId." }
    foreach ($id in @($case.relevantPaperIds)) { if ([long]$id -lt 1) { Fail "invalid relevantPaperIds in $caseId." } }
    $cases += $case
}
if ($cases.Count -eq 0) { Fail "no cases were found." }
if ($null -ne $manifest.caseCount -and [int]$manifest.caseCount -ne $cases.Count) { Fail "manifest.caseCount does not match cases.jsonl." }

$inputs = @((Resolve-Path -LiteralPath $CasesPath).Path, (Resolve-Path -LiteralPath $ManifestPath).Path)
if ($null -ne $candidateCatalogPath) { $inputs += $candidateCatalogPath }
$resolvedOutput = Resolve-NewOutput $OutputPath "OutputPath" $inputs
$resolvedMetadata = Resolve-NewOutput $RunMetadataPath "RunMetadataPath" $inputs
if (($null -eq $resolvedOutput) -ne ($null -eq $resolvedMetadata)) {
    Fail "OutputPath and RunMetadataPath must be supplied together."
}
if ([string]$manifest.datasetId -eq "rag-retrieval-v1" -and $null -ne $resolvedOutput) {
    $frozenRoot = [IO.Path]::GetFullPath((Split-Path -Parent (Resolve-Path -LiteralPath $CasesPath).Path))
    if ($resolvedOutput.StartsWith($frozenRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
            $resolvedMetadata.StartsWith($frozenRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        Fail "v1 output files must not be inside the frozen dataset directory."
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$runId = "rag-eval-{0}-{1}" -f [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssfffZ"), ([guid]::NewGuid().ToString("N").Substring(0, 8))
$startedAt = [DateTime]::UtcNow.ToString("o")
$results = @()
foreach ($case in $cases) {
    $requestBody = [ordered]@{ query = [string]$case.queryText; topK = $TopK }
    $fromYear = $null
    $toYear = $null
    if ($null -ne $case.filter) {
        if ($null -ne $case.filter.fromYear) { $fromYear = [int]$case.filter.fromYear; $requestBody.fromYear = $fromYear }
        if ($null -ne $case.filter.toYear) { $toYear = [int]$case.filter.toYear; $requestBody.toYear = $toYear }
    }
    $started = [Diagnostics.Stopwatch]::StartNew()
    $response = Post-Json "$baseUrl/api/research/retrieve" $requestBody "case $($case.caseId)"
    $started.Stop()
    $status = Require-Text $response.status "status for $($case.caseId)"
    if ($status -notin @("SUCCESS", "NO_TRUSTED_RESULTS", "FAILED")) { Fail "unknown status for $($case.caseId): $status." }
    $version = if ($null -eq $response.activeEmbeddingVersion) { $null } else { [string]$response.activeEmbeddingVersion }
    $candidateCount = Require-NonNegativeInt $response.qdrantCandidateCount "candidateCount for $($case.caseId)"
    $uniqueCount = Require-NonNegativeInt $response.uniquePaperCandidateCount "uniqueCandidateCount for $($case.caseId)"
    $admittedCount = Require-NonNegativeInt $response.admittedPaperCount "admittedCount for $($case.caseId)"
    $filteredCount = Require-NonNegativeInt $response.filteredCount "filteredCount for $($case.caseId)"
    $requestedTopK = Require-NonNegativeInt $response.requestedTopK "requestedTopK for $($case.caseId)"
    if ($requestedTopK -ne $TopK) { Fail "response requestedTopK mismatch for $($case.caseId)." }
    $ranked = @()
    $seenPaperIds = @{}
    $previousScore = [double]::PositiveInfinity
    $rank = 0
    foreach ($hit in @($response.results)) {
        $rank++
        $id = Require-NonNegativeInt $hit.paperId "paperId for $($case.caseId)"
        if ($id -lt 1) { Fail "paperId must be positive for $($case.caseId)." }
        if ($seenPaperIds.ContainsKey($id)) { Fail "duplicate ranked paperId for $($case.caseId)." }
        $seenPaperIds[$id] = $true
        $score = Require-FiniteDouble $hit.score "score for $($case.caseId)"
        if ($score -gt $previousScore) { Fail "scores are not descending for $($case.caseId)." }
        $previousScore = $score
        $ranked += [pscustomobject][ordered]@{
            rank = $rank
            paperId = $id
            score = $score
            segmentType = Require-Text $hit.matchedSegmentType "segmentType for $($case.caseId)"
        }
    }
    if ($ranked.Count -gt $TopK -or $ranked.Count -ne $admittedCount) { Fail "ranked result count mismatch for $($case.caseId)." }
    if ($status -eq "SUCCESS" -and $ranked.Count -eq 0) { Fail "SUCCESS returned no ranked result for $($case.caseId)." }
    if ($status -eq "NO_TRUSTED_RESULTS" -and $ranked.Count -ne 0) { Fail "NO_TRUSTED_RESULTS returned ranked results for $($case.caseId)." }
    $failureCode = if ($null -eq $response.diagnostics) { $null } else { $response.diagnostics.failureCode }
    $results += [pscustomobject][ordered]@{
        schemaVersion = "rag-retrieval-observation-v0.2"
        runId = $runId
        caseId = [string]$case.caseId
        split = [string]$case.split
        caseIntent = [string]$case.caseIntent
        observedAtUtc = [DateTime]::UtcNow.ToString("o")
        request = [ordered]@{ topK = $TopK; fromYear = $fromYear; toYear = $toYear }
        status = $status
        activeEmbeddingVersion = $version
        requestedTopK = $requestedTopK
        qdrantCandidateCount = $candidateCount
        uniquePaperCandidateCount = $uniqueCount
        admittedPaperCount = $admittedCount
        filteredCount = $filteredCount
        serviceElapsedMs = Require-NonNegativeInt $response.elapsedMs "elapsedMs for $($case.caseId)"
        clientElapsedMs = [long]$started.ElapsedMilliseconds
        rankedPaperIds = @($ranked | ForEach-Object { $_.paperId })
        scores = @($ranked | ForEach-Object { $_.score })
        rankedResults = $ranked
        failureCode = $failureCode
    }
}

$collection = Get-Json "$qdrantBaseUrl/collections/$([uri]::EscapeDataString($CollectionName))" "Qdrant collection"
if ([string]$collection.status -ne "ok" -or $null -eq $collection.result) { Fail "Qdrant collection response was not successful." }
$pointCount = Require-NonNegativeInt $collection.result.points_count "Qdrant point count"
$vectorDimensions = Require-NonNegativeInt $collection.result.config.params.vectors.size "Qdrant vector dimensions"
$versions = @($results | ForEach-Object { $_.activeEmbeddingVersion } | Where-Object { $_ } | Sort-Object -Unique)
if ($versions.Count -ne 1) { Fail "the run did not observe exactly one active embedding version." }

$observationLines = @($results | ForEach-Object { $_ | ConvertTo-Json -Depth 10 -Compress })
$observationText = ($observationLines -join "`n") + "`n"
$observationHash = Sha256-Bytes ([Text.Encoding]::UTF8.GetBytes($observationText))
$completedAt = [DateTime]::UtcNow.ToString("o")
$trackedDirty = (Git-Value @("status", "--porcelain", "--untracked-files=no"))
$metadata = [ordered]@{
    schemaVersion = "rag-retrieval-eval-run-v0.2"
    runId = $runId
    startedAtUtc = $startedAt
    completedAtUtc = $completedAt
    datasetId = [string]$manifest.datasetId
    caseCount = $cases.Count
    topK = $TopK
    baseUrl = $baseUrl
    qdrantBaseUrl = $qdrantBaseUrl
    collectionName = $CollectionName
    collectionStatus = [string]$collection.result.status
    collectionPointCount = $pointCount
    vectorDimensions = $vectorDimensions
    activeEmbeddingVersion = $versions[0]
    mainCommit = Git-Value @("rev-parse", "HEAD")
    trackedWorktreeState = if ([string]::IsNullOrWhiteSpace($trackedDirty)) { "CLEAN" } else { "DIRTY" }
    casesLfSha256 = $actualCasesHash
    manifestSha256 = Sha256-File $ManifestPath
    candidateCatalogPath = if ($null -eq $candidateCatalogPath) { $null } else { [string]$manifest.candidateCatalogPath }
    candidateCatalogSha256 = $candidateCatalogHash
    runnerSha256 = Sha256-File $PSCommandPath
    observationLfSha256 = $observationHash
    statusCounts = [ordered]@{
        success = @($results | Where-Object { $_.status -eq "SUCCESS" }).Count
        noTrustedResults = @($results | Where-Object { $_.status -eq "NO_TRUSTED_RESULTS" }).Count
        failed = @($results | Where-Object { $_.status -eq "FAILED" }).Count
    }
    averageClientElapsedMs = [math]::Round((($results | Measure-Object -Property clientElapsedMs -Average).Average), 2)
    outputPath = if ($null -eq $resolvedOutput) { $null } else { [IO.Path]::GetFileName($resolvedOutput) }
}

if ($null -eq $resolvedOutput) {
    $observationLines | Write-Output
    Write-Host "[RAG_RETRIEVAL_EVAL] runId=$runId caseCount=$($cases.Count) observationLfSha256=$observationHash metadata=$($metadata | ConvertTo-Json -Depth 8 -Compress)"
} else {
    [IO.File]::WriteAllText($resolvedOutput, $observationText, $utf8NoBom)
    [IO.File]::WriteAllText($resolvedMetadata, (($metadata | ConvertTo-Json -Depth 8) + "`n"), $utf8NoBom)
    Write-Output "[RAG_RETRIEVAL_EVAL] runId=$runId outputPath=$resolvedOutput metadataPath=$resolvedMetadata caseCount=$($cases.Count) observationLfSha256=$observationHash"
}
