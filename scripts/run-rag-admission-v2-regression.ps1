[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DatasetPath,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [switch]$ConfirmRealModelCost
)

$ErrorActionPreference = "Stop"
$utf8NoBom = New-Object Text.UTF8Encoding($false)
$allowedCaseIds = @("rag-v3l-0003", "rag-v3l-0013", "rag-v3l-0018")

function Fail([string]$Message) { throw "RAG admission v2 regression failed closed: $Message" }

function Sha256-Text([string]$Text) {
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
        return (($sha256.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join "")
    } finally {
        $sha256.Dispose()
    }
}

function Read-Json([string]$Path, [string]$Name) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { Fail "$Name was not found." }
    try { return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json }
    catch { Fail "$Name is not valid JSON." }
}

function Require-Property($Object, [string]$Name, [string]$Context) {
    if ($null -eq $Object -or $Name -notin @($Object.PSObject.Properties.Name)) {
        Fail "$Context is missing property $Name."
    }
    return $Object.$Name
}

function Post-Json([string]$Uri, $Body, [string]$CaseId) {
    $json = $Body | ConvertTo-Json -Depth 6 -Compress
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $Uri `
            -ContentType "application/json; charset=utf-8" `
            -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 150
    } catch {
        Fail "request for $CaseId failed: $($_.Exception.GetType().Name)"
    }
    if ($response.StatusCode -ne 200) { Fail "request for $CaseId returned HTTP $($response.StatusCode)." }
    try { return $response.Content | ConvertFrom-Json }
    catch { Fail "request for $CaseId returned invalid JSON." }
}

if (-not $ConfirmRealModelCost) {
    Fail "-ConfirmRealModelCost is required for the three real-provider regression cases."
}

$dataset = [IO.Path]::GetFullPath($DatasetPath)
$output = [IO.Path]::GetFullPath($OutputPath)
if (-not (Test-Path -LiteralPath $dataset -PathType Container)) { Fail "DatasetPath was not found." }
if (Test-Path -LiteralPath $output) { Fail "OutputPath already exists and will not be overwritten." }
$outputDirectory = Split-Path -Parent $output
if (-not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
    Fail "OutputPath parent directory must already exist."
}

$manifest = Read-Json (Join-Path $dataset "manifest.json") "dataset manifest"
if ([string]$manifest.datasetId -ne "rag-retrieval-v3-lite") { Fail "unexpected datasetId." }
if ([string]$manifest.review.groundTruthStatus -ne "USER_AUDITED_CODEX_REVIEWED") {
    Fail "dataset review state is not eligible for this development regression."
}

$allCases = @()
foreach ($fileName in @("tuning-cases.json", "fixed-holdout-cases.json")) {
    $allCases += @((Read-Json (Join-Path $dataset $fileName) $fileName).GetEnumerator())
}
$cases = @($allCases | Where-Object { [string]$_.caseId -in $allowedCaseIds })
if ($cases.Count -ne 3) { Fail "the exact three revealed regression cases were not found." }
if (@($cases | Select-Object -ExpandProperty caseId -Unique).Count -ne 3) {
    Fail "regression case IDs are not unique."
}

$base = $BaseUrl.TrimEnd("/")
if ($base -notmatch "^https?://[^\s/]+(?::\d+)?$") { Fail "BaseUrl is not an HTTP origin." }
$observations = @()
foreach ($caseId in $allowedCaseIds) {
    $case = @($cases | Where-Object { [string]$_.caseId -eq $caseId })[0]
    $question = [string]$case.queryText
    $request = [ordered]@{ question = $question; topK = 5 }
    if ($null -ne $case.filter.fromYear) { $request.fromYear = [int]$case.filter.fromYear }
    if ($null -ne $case.filter.toYear) { $request.toYear = [int]$case.filter.toYear }

    $watch = [Diagnostics.Stopwatch]::StartNew()
    $response = Post-Json "$base/api/research/ask" $request $caseId
    $watch.Stop()
    $diagnostics = Require-Property $response "diagnostics" $caseId
    $answer = [string](Require-Property $response "answer" $caseId)
    $status = [string](Require-Property $response "status" $caseId)
    $citations = @((Require-Property $response "citations" $caseId) | ForEach-Object {
        [ordered]@{ normalizedDoi = ([string]$_.normalizedDoi).ToLowerInvariant() }
    })
    $detail = if ("failureDetailCode" -in @($diagnostics.PSObject.Properties.Name)) {
        [string]$diagnostics.failureDetailCode
    } else { $null }

    $casePass = $status -eq "SUCCESS" `
        -and [int]$diagnostics.relevanceJudgeCallCount -eq 1 `
        -and [int]$diagnostics.answerModelCallCount -ge 1 `
        -and $citations.Count -ge 1
    $observations += [ordered]@{
        caseId = $caseId
        originalSplit = [string]$case.split
        usage = "BURNED_DEVELOPMENT_REGRESSION_ONLY"
        questionUtf8Sha256 = Sha256-Text $question
        status = $status
        failureCode = [string]$diagnostics.failureCode
        failureDetailCode = $detail
        modelCallCount = [int]$diagnostics.modelCallCount
        relevanceJudgeCallCount = [int]$diagnostics.relevanceJudgeCallCount
        answerModelCallCount = [int]$diagnostics.answerModelCallCount
        repairCount = [int]$diagnostics.repairCount
        admittedEvidenceCount = [int]$diagnostics.admittedEvidenceCount
        generationEvidenceCount = [int]$diagnostics.generationEvidenceCount
        answerLength = $answer.Length
        answerUtf8Sha256 = if ($answer.Length -eq 0) { $null } else { Sha256-Text $answer }
        citations = $citations
        elapsedMs = [long]$watch.ElapsedMilliseconds
        pass = $casePass
    }
}

$document = [ordered]@{
    schemaVersion = "rag-admission-v2-regression-v0.1"
    capturedAt = [DateTime]::UtcNow.ToString("o")
    status = if (@($observations | Where-Object { -not $_.pass }).Count -eq 0) { "PASS" } else { "FAIL" }
    acceptanceAuthority = "NONE_BURNED_CASES"
    historicalHoldoutStatus = "FAIL_UNCHANGED"
    caseCount = 3
    cases = $observations
}
[IO.File]::WriteAllText($output, ($document | ConvertTo-Json -Depth 10), $utf8NoBom)
$document | ConvertTo-Json -Depth 10
