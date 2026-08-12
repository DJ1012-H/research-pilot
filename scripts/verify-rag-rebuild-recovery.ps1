[CmdletBinding()]
param(
    [ValidateSet("CaptureBaseline", "DeleteCollection", "VerifyRestored")]
    [string]$Stage = "CaptureBaseline",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$QdrantBaseUrl = "http://127.0.0.1:6333",
    [ValidatePattern("^[A-Za-z0-9_-]{1,255}$")]
    [string]$CollectionName = "research_pilot_paper_segments_v1",
    [string]$Question = "Which papers study selective state space models for dense prediction?",
    [ValidateRange(1, 20)][int]$TopK = 5,
    [Parameter(Mandatory = $true)][string]$StatePath,
    [switch]$ExecuteDestructiveDelete,
    [string]$ExpectedCollectionName,
    [string]$ConfirmationPhrase
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) { throw "RAG recovery rehearsal failed closed: $Message" }

function Normalize-BaseUrl([string]$Url, [string]$Name) {
    if ($Url -notmatch "^https?://[^\s/]+(?:[:/].*)?$") { Fail "$Name is not an absolute HTTP URL." }
    return $Url.TrimEnd('/')
}

function Require-Property($Object, [string]$Name, [string]$Context) {
    if ($null -eq $Object -or $Object.PSObject.Properties.Name -notcontains $Name) {
        Fail "$Context is missing '$Name'."
    }
    return $Object.$Name
}

function Require-NonNegativeInt($Value, [string]$Name) {
    try { $number = [long]$Value } catch { Fail "$Name is not numeric." }
    if ($number -lt 0) { Fail "$Name is negative." }
    return $number
}

function Get-Json([string]$Uri, [string]$Context) {
    try { $response = Invoke-WebRequest -UseBasicParsing -Method Get -Uri $Uri }
    catch { Fail "$Context request failed." }
    if ($response.StatusCode -ne 200) { Fail "$Context returned HTTP $($response.StatusCode)." }
    try { return ($response.Content | ConvertFrom-Json) }
    catch { Fail "$Context returned invalid JSON." }
}

function Post-Json([string]$Uri, $Body, [string]$Context) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $Uri `
            -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 8 -Compress)
    } catch { Fail "$Context request failed." }
    if ($response.StatusCode -ne 200) { Fail "$Context returned HTTP $($response.StatusCode)." }
    try { return ($response.Content | ConvertFrom-Json) }
    catch { Fail "$Context returned invalid JSON." }
}

function Get-Snapshot {
    $baseUrl = Normalize-BaseUrl $BaseUrl "BaseUrl"
    $qdrantUrl = Normalize-BaseUrl $QdrantBaseUrl "QdrantBaseUrl"
    $encodedCollection = [uri]::EscapeDataString($CollectionName)
    $collection = Get-Json "$qdrantUrl/collections/$encodedCollection" "collection inspection"
    $collectionResult = Require-Property $collection "result" "collection inspection"
    $config = Require-Property $collectionResult "config" "collection inspection"
    $params = Require-Property $config "params" "collection inspection"
    $vectors = Require-Property $params "vectors" "collection inspection"
    $dimensions = Require-NonNegativeInt (Require-Property $vectors "size" "collection vectors") "vector dimensions"
    $pointCount = Require-NonNegativeInt (Require-Property $collectionResult "points_count" "collection inspection") "point count"

    $points = @()
    $offset = $null
    do {
        $body = [ordered]@{ limit = 256; with_payload = $true; with_vector = $false }
        if ($null -ne $offset) { $body.offset = $offset }
        $page = Post-Json "$qdrantUrl/collections/$encodedCollection/points/scroll" $body "point summary"
        $result = Require-Property $page "result" "point summary"
        $points += @(Require-Property $result "points" "point summary")
        $offset = if ($result.PSObject.Properties.Name -contains "next_page_offset") { $result.next_page_offset } else { $null }
    } while ($null -ne $offset)
    if ($points.Count -ne $pointCount) { Fail "point count changed during baseline capture." }

    $retrieval = Post-Json "$baseUrl/api/research/retrieve" ([ordered]@{ query = $Question; topK = $TopK }) "fixed retrieval"
    $status = Get-Json "$baseUrl/api/system/status" "system status"
    $mysqlStatus = [string](Require-Property (Require-Property $status "mysql" "system status") "status" "system status.mysql")
    if ($mysqlStatus -ne "UP") { Fail "MySQL is not UP; active index evidence cannot be confirmed." }
    $activeVersion = Require-Property $retrieval "activeEmbeddingVersion" "fixed retrieval"
    if ([string]::IsNullOrWhiteSpace([string]$activeVersion)) { Fail "active embedding version is unavailable." }
    $retrievalStatus = [string](Require-Property $retrieval "status" "fixed retrieval")
    if ($retrievalStatus -notin @("SUCCESS", "NO_TRUSTED_RESULTS")) {
        Fail "fixed retrieval returned an unknown or failed status: $retrievalStatus."
    }
    foreach ($field in @("requestedTopK", "qdrantCandidateCount", "admittedPaperCount", "filteredCount")) {
        Require-NonNegativeInt (Require-Property $retrieval $field "fixed retrieval") "fixed retrieval $field" | Out-Null
    }
    $results = @(Require-Property $retrieval "results" "fixed retrieval")
    $rankedIds = @($results | ForEach-Object { Require-NonNegativeInt $_.paperId "fixed retrieval paperId" })
    $activeEvidence = @($results | Where-Object { $_.matchedSegmentType -eq "ABSTRACT" }).Count
    if ($activeEvidence -lt 0) { Fail "invalid MySQL active evidence count." }

    return [ordered]@{
        capturedAtUtc = [DateTime]::UtcNow.ToString("o")
        collectionName = $CollectionName
        activeEmbeddingVersion = [string]$activeVersion
        vectorDimensions = [int]$dimensions
        pointCount = [long]$pointCount
        abstractPointCount = @($points | Where-Object { $_.payload.segmentType -eq "ABSTRACT" }).Count
        metadataPointCount = @($points | Where-Object { $_.payload.segmentType -eq "METADATA" }).Count
        mysqlActiveEvidenceCount = [int]$activeEvidence
        fixedRetrievalStatus = $retrievalStatus
        fixedRetrievalPaperIds = $rankedIds
        systemStatus = [ordered]@{
            application = [string](Require-Property $status "application" "system status")
            mysql = $mysqlStatus
            ollamaEmbedding = [string](Require-Property (Require-Property $status "ollamaEmbedding" "system status") "status" "system status.ollamaEmbedding")
            qdrant = [string](Require-Property (Require-Property $status "qdrant" "system status") "status" "system status.qdrant")
        }
    }
}

if ([string]::IsNullOrWhiteSpace($StatePath)) { Fail "StatePath is required so the rehearsal is resumable and explicit." }
$stateFullPath = [IO.Path]::GetFullPath($StatePath)

switch ($Stage) {
    "CaptureBaseline" {
        $snapshot = Get-Snapshot
        Set-Content -LiteralPath $stateFullPath -Value ($snapshot | ConvertTo-Json -Depth 8) -Encoding UTF8
        Write-Output "[RAG_RECOVERY] stage=CaptureBaseline status=CAPTURED collection=$CollectionName pointCount=$($snapshot.pointCount) abstractPointCount=$($snapshot.abstractPointCount) metadataPointCount=$($snapshot.metadataPointCount) activeEmbeddingVersion=$($snapshot.activeEmbeddingVersion) statePath=$stateFullPath"
    }
    "DeleteCollection" {
        if (-not $ExecuteDestructiveDelete) { Fail "DeleteCollection is disabled by default; supply -ExecuteDestructiveDelete only after separate operator authorization." }
        if ($ExpectedCollectionName -ne $CollectionName) { Fail "ExpectedCollectionName must exactly match CollectionName." }
        if ($ConfirmationPhrase -cne "DELETE RAG COLLECTION $CollectionName") { Fail "ConfirmationPhrase must exactly match the displayed destructive confirmation phrase." }
        if (-not (Test-Path -LiteralPath $stateFullPath -PathType Leaf)) { Fail "baseline StatePath was not found." }
        $baseline = Get-Content -Raw -LiteralPath $stateFullPath | ConvertFrom-Json
        if ($baseline.collectionName -ne $CollectionName) { Fail "baseline collection does not match target collection." }
        Write-Host "DESTRUCTIVE TARGET: Qdrant collection '$CollectionName' only." -ForegroundColor Yellow
        Write-Host "BASELINE: pointCount=$($baseline.pointCount), abstractPointCount=$($baseline.abstractPointCount), metadataPointCount=$($baseline.metadataPointCount), activeEmbeddingVersion=$($baseline.activeEmbeddingVersion)" -ForegroundColor Yellow
        Write-Host "CONFIRMATION: DELETE RAG COLLECTION $CollectionName" -ForegroundColor Yellow
        $qdrantUrl = Normalize-BaseUrl $QdrantBaseUrl "QdrantBaseUrl"
        $encodedCollection = [uri]::EscapeDataString($CollectionName)
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Method Delete -Uri "$qdrantUrl/collections/$encodedCollection"
        } catch { Fail "collection deletion request failed." }
        if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) { Fail "collection deletion returned HTTP $($response.StatusCode)." }
        Write-Output "[RAG_RECOVERY] stage=DeleteCollection status=DELETED collection=$CollectionName nextStage=VerifyRestored"
    }
    "VerifyRestored" {
        if (-not (Test-Path -LiteralPath $stateFullPath -PathType Leaf)) { Fail "baseline StatePath was not found." }
        try { $baseline = Get-Content -Raw -LiteralPath $stateFullPath | ConvertFrom-Json } catch { Fail "baseline StatePath is invalid JSON." }
        $current = Get-Snapshot
        foreach ($field in @("collectionName", "activeEmbeddingVersion", "vectorDimensions", "pointCount", "abstractPointCount", "metadataPointCount", "mysqlActiveEvidenceCount", "fixedRetrievalStatus")) {
            if ([string]$baseline.$field -ne [string]$current.$field) {
                Fail "restored snapshot mismatch in ${field}: baseline=$($baseline.$field), current=$($current.$field)."
            }
        }
        $beforeIds = (@($baseline.fixedRetrievalPaperIds) -join ",")
        $afterIds = (@($current.fixedRetrievalPaperIds) -join ",")
        if ($beforeIds -ne $afterIds) { Fail "fixed retrieval paper IDs changed after rebuild." }
        Write-Output "[RAG_RECOVERY] stage=VerifyRestored status=PASS collection=$CollectionName pointCount=$($current.pointCount) abstractPointCount=$($current.abstractPointCount) metadataPointCount=$($current.metadataPointCount) activeEmbeddingVersion=$($current.activeEmbeddingVersion)"
    }
}
