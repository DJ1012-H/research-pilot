[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$QdrantBaseUrl = "http://127.0.0.1:6333",
    [ValidatePattern("^[A-Za-z0-9_-]{1,255}$")]
    [string]$CollectionName = "research_pilot_paper_segments_v1",
    [string]$Question = "Which papers study selective state space models for dense prediction?",
    [string]$SemanticNegativeQuestion = "Which state-space models predict protein folding structures?",
    [ValidateRange(1900, 2100)]
    [int]$FromYear = 2023,
    [ValidateRange(1900, 2100)]
    [int]$ToYear = 2024,
    [ValidateRange(1, [long]::MaxValue)]
    [long]$EvidenceInsufficientPaperId = [long]::MaxValue,
    [ValidateRange(1, 20)]
    [int]$TopK = 5,
    [ValidateRange(1, 5)]
    [int]$ExpectedMaxEvidence = 5,
    [switch]$RequireMaxEvidenceBoundary,
    [switch]$ShowPublicAnswer
)

$ErrorActionPreference = "Stop"

function Require-Property {
    param(
        [Parameter(Mandatory = $true)]$Object,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Context
    )
    if ($null -eq $Object -or $Object.PSObject.Properties.Name -notcontains $Name) {
        throw "$Context is missing required field '$Name'."
    }
    return $Object.$Name
}

function Normalize-BaseUrl {
    param([Parameter(Mandatory = $true)][string]$Url, [Parameter(Mandatory = $true)][string]$Name)
    if ($Url -notmatch "^https?://[^\s/]+(?:[:/].*)?$") {
        throw "$Name must be an absolute http:// or https:// URL."
    }
    return $Url.TrimEnd('/')
}

function Invoke-JsonGet {
    param([Parameter(Mandatory = $true)][string]$Uri, [Parameter(Mandatory = $true)][string]$Context)
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Get -Uri $Uri
    } catch {
        throw "$Context failed: external HTTP request was not successful."
    }
    if ($response.StatusCode -ne 200) {
        throw "$Context returned unexpected HTTP status $($response.StatusCode)."
    }
    try {
        $json = $response.Content | ConvertFrom-Json
    } catch {
        throw "$Context returned invalid JSON."
    }
    if ($null -eq $json) {
        throw "$Context returned an empty JSON document."
    }
    return $json
}

function Invoke-JsonPost {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)]$Body,
        [Parameter(Mandatory = $true)][string]$Context
    )
    try {
        $jsonBody = $Body | ConvertTo-Json -Depth 10 -Compress
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $Uri `
            -ContentType "application/json; charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($jsonBody))
    } catch {
        throw "$Context failed: external HTTP request was not successful."
    }
    if ($response.StatusCode -ne 200) {
        throw "$Context returned unexpected HTTP status $($response.StatusCode)."
    }
    try {
        $json = $response.Content | ConvertFrom-Json
    } catch {
        throw "$Context returned invalid JSON."
    }
    if ($null -eq $json) {
        throw "$Context returned an empty JSON document."
    }
    return [pscustomobject]@{
        Json = $json
        Headers = $response.Headers
        StatusCode = [int]$response.StatusCode
    }
}

function Require-NonBlank {
    param([AllowNull()]$Value, [Parameter(Mandatory = $true)][string]$Name)
    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        throw "$Name must be non-blank."
    }
    return [string]$Value
}

function Require-NonNegativeInt {
    param($Value, [Parameter(Mandatory = $true)][string]$Name)
    if ($null -eq $Value -or $Value -isnot [ValueType]) {
        throw "$Name is missing or not numeric."
    }
    try { $number = [long]$Value } catch { throw "$Name is not numeric." }
    if ($number -lt 0) { throw "$Name must not be negative." }
    return $number
}

function Get-QdrantPointSummary {
    param(
        [Parameter(Mandatory = $true)][string]$QdrantUrl,
        [Parameter(Mandatory = $true)][string]$Collection
    )
    $encodedCollection = [uri]::EscapeDataString($Collection)
    $collectionResponse = Invoke-JsonGet "$QdrantUrl/collections/$encodedCollection" "Qdrant collection inspection"
    $collectionStatus = [string]$collectionResponse.status
    if ([string]::IsNullOrWhiteSpace($collectionStatus)) {
        $collectionStatus = [string]$collectionResponse.result.status
    }
    if ([string]::IsNullOrWhiteSpace($collectionStatus)) {
        throw "Qdrant collection inspection is missing required field 'status'."
    }
    if ($collectionStatus -notin @("ok", "green")) {
        throw "Qdrant collection inspection returned an unknown status."
    }
    $collectionResult = Require-Property $collectionResponse "result" "Qdrant collection inspection"
    $config = Require-Property $collectionResult "config" "Qdrant collection inspection"
    $params = Require-Property $config "params" "Qdrant collection inspection"
    $vectors = Require-Property $params "vectors" "Qdrant collection inspection"
    $dimension = Require-NonNegativeInt (Require-Property $vectors "size" "Qdrant vector configuration") "Qdrant vector dimension"
    if ($dimension -lt 1) { throw "Qdrant vector dimension must be positive." }
    $reportedPointCount = Require-NonNegativeInt (Require-Property $collectionResult "points_count" "Qdrant collection inspection") "Qdrant point count"

    $points = @()
    $offset = $null
    do {
        $body = [ordered]@{
            limit = 256
            with_payload = $true
            with_vector = $false
        }
        if ($null -ne $offset) { $body.offset = $offset }
        $page = Invoke-JsonPost "$QdrantUrl/collections/$encodedCollection/points/scroll" $body "Qdrant point summary"
        $result = Require-Property $page.Json "result" "Qdrant point summary"
        $pagePoints = @(Require-Property $result "points" "Qdrant point summary")
        $points += $pagePoints
        $offset = if ($result.PSObject.Properties.Name -contains "next_page_offset") {
            $result.next_page_offset
        } else { $null }
    } while ($null -ne $offset)

    if ($points.Count -ne $reportedPointCount) {
        throw "Qdrant point count changed or scroll was incomplete: collection=$reportedPointCount scroll=$($points.Count)."
    }

    $verified = @($points | Where-Object { $_.payload.verificationStatus -eq "VERIFIED" })
    $abstract = @($points | Where-Object { $_.payload.segmentType -eq "ABSTRACT" })
    $metadata = @($points | Where-Object { $_.payload.segmentType -eq "METADATA" })
    $abstractPaperIds = @($abstract | ForEach-Object { [long]$_.payload.paperId } | Sort-Object -Unique)
    return [pscustomobject]@{
        collectionName = $Collection
        vectorDimensions = $dimension
        pointCount = [long]$reportedPointCount
        verifiedPointCount = [long]$verified.Count
        abstractPointCount = [long]$abstract.Count
        abstractPaperCount = [long]$abstractPaperIds.Count
        metadataPointCount = [long]$metadata.Count
    }
}

function Get-RetrievalSignature {
    param([Parameter(Mandatory = $true)]$Json)
    $status = Require-Property $Json "status" "retrieval response"
    if ($status -notin @("SUCCESS", "NO_TRUSTED_RESULTS", "FAILED")) {
        throw "retrieval response returned an unknown status '$status'."
    }
    foreach ($field in @("qdrantCandidateCount", "admittedPaperCount", "filteredCount", "requestedTopK")) {
        $value = Require-NonNegativeInt (Require-Property $Json $field "retrieval response") "retrieval $field"
        if ($field -eq "requestedTopK" -and $value -lt 1) { throw "retrieval requestedTopK must be positive." }
    }
    $results = @(Require-Property $Json "results" "retrieval response")
    $paperIds = @($results | ForEach-Object { Require-NonNegativeInt (Require-Property $_ "paperId" "retrieval result") "retrieval paperId" })
    return "$status|$(Require-Property $Json 'qdrantCandidateCount' 'retrieval response')|$(Require-Property $Json 'admittedPaperCount' 'retrieval response')|$($paperIds -join ',')"
}

$baseUrl = Normalize-BaseUrl $BaseUrl "BaseUrl"
$qdrantUrl = Normalize-BaseUrl $QdrantBaseUrl "QdrantBaseUrl"
if ($FromYear -gt $ToYear) { throw "FromYear must not exceed ToYear." }
if ($RequireMaxEvidenceBoundary -and $TopK -le $ExpectedMaxEvidence) {
    throw "TopK must exceed ExpectedMaxEvidence for a real truncation test."
}

$status = Invoke-JsonGet "$baseUrl/api/system/status" "system status"
foreach ($dependency in @("application", "mysql", "ollamaEmbedding", "qdrant")) {
    $value = Require-Property $status $dependency "system status"
    if ($dependency -eq "application") {
        $state = [string]$value
        if ($state -ne "UP") { throw "application is not UP." }
        continue
    }
    $state = Require-Property $value "status" "system status.$dependency"
    if ($state -ne "UP") {
        throw "$dependency is not UP; RAG acceptance cannot continue."
    }
}

$pointSummary = Get-QdrantPointSummary $qdrantUrl $CollectionName
$probeBody = [ordered]@{ query = $Question; topK = $TopK }
$probe = Invoke-JsonPost "$baseUrl/api/research/retrieve" $probeBody "baseline retrieval"
$probeJson = $probe.Json
$activeVersion = Require-NonBlank (Require-Property $probeJson "activeEmbeddingVersion" "baseline retrieval") "active embeddingVersion"
$requestedTopK = Require-NonNegativeInt (Require-Property $probeJson "requestedTopK" "baseline retrieval") "requestedTopK"
if ($requestedTopK -lt 1) { throw "requestedTopK must be positive." }
$ollamaDetail = Require-Property (Require-Property $status "ollamaEmbedding" "system status") "detail" "system status.ollamaEmbedding"
if ($ollamaDetail -notmatch "(?i)(\d+) dimensions") {
    throw "Could not confirm Ollama embedding dimensions from the status detail."
}
$ollamaDimensions = [int]$Matches[1]
if ($ollamaDimensions -ne $pointSummary.vectorDimensions) {
    throw "Embedding dimension mismatch: Ollama=$ollamaDimensions Qdrant=$($pointSummary.vectorDimensions)."
}

$yearBody = [ordered]@{ query = $Question; topK = $TopK; fromYear = $FromYear; toYear = $ToYear }
$yearFiltered = (Invoke-JsonPost "$baseUrl/api/research/retrieve" $yearBody "year-filtered retrieval").Json
$baseSignature = Get-RetrievalSignature $probeJson
$yearSignature = Get-RetrievalSignature $yearFiltered
if ($baseSignature -eq $yearSignature) {
    throw "Year-filter demonstration is not evidenced by the current data; no candidate/admission change was observed."
}

$answerBody = [ordered]@{ question = $Question; topK = $TopK }
$answerResult = Invoke-JsonPost "$baseUrl/api/research/ask" $answerBody "RAG answer"
$answer = $answerResult.Json
if ((Require-Property $answer "status" "RAG answer") -ne "SUCCESS") { throw "RAG answer did not return SUCCESS." }
if ([string]::IsNullOrWhiteSpace([string](Require-Property $answer "answer" "RAG answer"))) { throw "RAG answer was empty." }
$citations = @(Require-Property $answer "citations" "RAG answer")
if ($citations.Count -lt 1) { throw "RAG answer returned no citations." }
if ((Require-Property $answer "insufficientEvidence" "RAG answer") -ne $false) { throw "Successful RAG answer must not be marked insufficient." }
$diagnostics = Require-Property $answer "diagnostics" "RAG answer"
if ($null -ne $diagnostics.failureCode) { throw "Successful RAG answer exposed failureCode=$($diagnostics.failureCode)." }
$modelCallCount = Require-NonNegativeInt (Require-Property $diagnostics "modelCallCount" "RAG answer.diagnostics") "modelCallCount"
if ($modelCallCount -lt 2) { throw "Successful RAG answer must report one relevance-judge call and at least one answer-model call." }
$relevanceJudgeCallCount = Require-NonNegativeInt `
    (Require-Property $diagnostics "relevanceJudgeCallCount" "RAG answer.diagnostics") `
    "relevanceJudgeCallCount"
$answerModelCallCount = Require-NonNegativeInt `
    (Require-Property $diagnostics "answerModelCallCount" "RAG answer.diagnostics") `
    "answerModelCallCount"
$admittedEvidenceCount = Require-NonNegativeInt `
    (Require-Property $diagnostics "admittedEvidenceCount" "RAG answer.diagnostics") `
    "admittedEvidenceCount"
if ($relevanceJudgeCallCount -ne 1) { throw "Successful RAG answer must report exactly one relevance-judge call." }
if ($answerModelCallCount -lt 1 -or $answerModelCallCount -gt 2) { throw "Successful RAG answer must report one answer call and at most one repair." }
if ($modelCallCount -ne ($relevanceJudgeCallCount + $answerModelCallCount)) { throw "Total model calls do not equal judge plus answer calls." }
$generationEvidenceCount = Require-NonNegativeInt `
    (Require-Property $diagnostics "generationEvidenceCount" "RAG answer.diagnostics") `
    "generationEvidenceCount"
$summary = Require-Property $answer "retrievalSummary" "RAG answer"
$evidenceCount = Require-NonNegativeInt (Require-Property $summary "evidenceCount" "RAG answer.retrievalSummary") "evidenceCount"
if ($generationEvidenceCount -gt $evidenceCount) {
    throw "generationEvidenceCount exceeds the MySQL re-admitted evidence count."
}
if ($admittedEvidenceCount -lt 1 -or $generationEvidenceCount -gt $admittedEvidenceCount) {
    throw "Generation evidence is inconsistent with relevance-admitted evidence."
}
if ($generationEvidenceCount -gt $ExpectedMaxEvidence) {
    throw "generationEvidenceCount exceeds ExpectedMaxEvidence=$ExpectedMaxEvidence."
}
if ($RequireMaxEvidenceBoundary) {
    if ($pointSummary.abstractPaperCount -le $ExpectedMaxEvidence) {
        throw "Qdrant has only $($pointSummary.abstractPaperCount) distinct ABSTRACT papers; the boundary is not reachable."
    }
    if ($evidenceCount -le $ExpectedMaxEvidence) {
        throw "The live answer admitted only $evidenceCount evidence items; no real truncation occurred."
    }
    if ($generationEvidenceCount -ne $ExpectedMaxEvidence) {
        throw "Expected generationEvidenceCount=$ExpectedMaxEvidence after truncation, observed $generationEvidenceCount."
    }
}
foreach ($citation in $citations) {
    $paperId = Require-NonNegativeInt (Require-Property $citation "paperId" "RAG citation") "citation paperId"
    if ($paperId -lt 1) { throw "RAG citation paperId must be positive." }
    Require-NonBlank (Require-Property $citation "normalizedDoi" "RAG citation") "citation DOI" | Out-Null
    if ((Require-Property $citation "normalizedDoi" "RAG citation") -notmatch "^10\.\d{4,9}/\S+$") { throw "RAG citation DOI is not normalized." }
    Require-NonBlank (Require-Property $citation "title" "RAG citation") "citation title" | Out-Null
    $hash = Require-NonBlank (Require-Property $citation "contentHash" "RAG citation") "citation contentHash"
    if ($hash -notmatch "^[0-9a-f]{64}$") { throw "RAG citation contentHash is not lowercase SHA-256." }
    $position = Require-NonNegativeInt (Require-Property $citation "evidencePosition" "RAG citation") "citation evidencePosition"
    if ($position -lt 1 -or $position -gt $generationEvidenceCount) {
        throw "RAG citation evidencePosition is outside the generation evidence range."
    }
}

$insufficientBody = [ordered]@{
    question = $Question
    topK = $TopK
    paperIds = @($EvidenceInsufficientPaperId)
}
$insufficient = (Invoke-JsonPost "$baseUrl/api/research/ask" $insufficientBody "insufficient-evidence answer").Json
if ((Require-Property $insufficient "status" "insufficient-evidence answer") -ne "INSUFFICIENT_EVIDENCE") {
    throw "Evidence-insufficient request did not return INSUFFICIENT_EVIDENCE."
}
if ((Require-Property $insufficient "answer" "insufficient-evidence answer") -ne "") { throw "Insufficient answer must be empty." }
if (@(Require-Property $insufficient "citations" "insufficient-evidence answer").Count -ne 0) { throw "Insufficient answer must have no citations." }
$insufficientDiagnostics = Require-Property $insufficient "diagnostics" "insufficient-evidence answer"
if ((Require-Property $insufficientDiagnostics "modelCallCount" "insufficient diagnostics") -ne 0) {
    throw "Could not confirm zero model calls for insufficient evidence."
}
if ((Require-Property $insufficientDiagnostics "relevanceJudgeCallCount" "insufficient diagnostics") -ne 0 `
        -or (Require-Property $insufficientDiagnostics "answerModelCallCount" "insufficient diagnostics") -ne 0) {
    throw "No-candidate insufficient evidence must not call either model operation."
}
if ((Require-Property $insufficientDiagnostics "repairCount" "insufficient diagnostics") -ne 0) {
    throw "Could not confirm zero repair calls for insufficient evidence."
}

$semanticNegativeBody = [ordered]@{ question = $SemanticNegativeQuestion; topK = $TopK }
$semanticNegative = (Invoke-JsonPost "$baseUrl/api/research/ask" $semanticNegativeBody "semantic-negative answer").Json
if ((Require-Property $semanticNegative "status" "semantic-negative answer") -ne "INSUFFICIENT_EVIDENCE") {
    throw "Semantic-negative request did not return INSUFFICIENT_EVIDENCE."
}
if ((Require-Property $semanticNegative "answer" "semantic-negative answer") -ne "") { throw "Semantic-negative answer must be empty." }
if (@(Require-Property $semanticNegative "citations" "semantic-negative answer").Count -ne 0) { throw "Semantic-negative answer must have no citations." }
$semanticDiagnostics = Require-Property $semanticNegative "diagnostics" "semantic-negative answer"
if ((Require-Property $semanticDiagnostics "relevanceJudgeCallCount" "semantic-negative diagnostics") -ne 1) {
    throw "Semantic-negative request must report exactly one relevance-judge call."
}
if ((Require-Property $semanticDiagnostics "answerModelCallCount" "semantic-negative diagnostics") -ne 0) {
    throw "Semantic-negative request called the answer model."
}
if ((Require-Property $semanticDiagnostics "modelCallCount" "semantic-negative diagnostics") -ne 1) {
    throw "Semantic-negative total model-call count must be exactly one."
}
if ((Require-Property $semanticDiagnostics "admittedEvidenceCount" "semantic-negative diagnostics") -ne 0 `
        -or (Require-Property $semanticDiagnostics "generationEvidenceCount" "semantic-negative diagnostics") -ne 0) {
    throw "Semantic-negative request admitted or generated from evidence."
}

Write-Output "[RAG_DEMO] status=PASS collection=$CollectionName pointCount=$($pointSummary.pointCount) verifiedPointCount=$($pointSummary.verifiedPointCount) abstractPointCount=$($pointSummary.abstractPointCount) abstractPaperCount=$($pointSummary.abstractPaperCount) metadataPointCount=$($pointSummary.metadataPointCount) activeEmbeddingVersion=$activeVersion embeddingDimensions=$($pointSummary.vectorDimensions)"
Write-Output "[RAG_DEMO] retrieveStatus=$($probeJson.status) yearFilteredStatus=$($yearFiltered.status) answerStatus=$($answer.status) insufficientStatus=$($insufficient.status) semanticNegativeStatus=$($semanticNegative.status) evidenceCount=$evidenceCount admittedEvidenceCount=$admittedEvidenceCount generationEvidenceCount=$generationEvidenceCount relevanceJudgeCallCount=$relevanceJudgeCallCount answerModelCallCount=$answerModelCallCount modelCallCount=$modelCallCount requestId=$($answer.requestId) elapsedMs=$($answer.elapsedMs)"

if ($ShowPublicAnswer) {
    Write-Output "[RAG_DEMO_PUBLIC_ANSWER]"
    Write-Output (Require-Property $answer "answer" "RAG answer")
    Write-Output (($citations | ConvertTo-Json -Depth 5))
}
