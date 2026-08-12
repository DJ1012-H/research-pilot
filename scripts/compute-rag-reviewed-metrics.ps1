[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$CasesPath,
    [Parameter(Mandatory = $true)][string]$ObservationPath,
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [Parameter(Mandatory = $true)][string]$OutputPath
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) { throw "Reviewed RAG metric computation failed closed: $Message" }

function Sha256-Bytes([byte[]]$Bytes) {
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try { return (($sha256.ComputeHash($Bytes) | ForEach-Object { $_.ToString('x2') }) -join '') }
    finally { $sha256.Dispose() }
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

foreach ($path in @($CasesPath, $ObservationPath, $ManifestPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Fail "missing input: $path" }
}

$manifest = Get-Content -Raw -LiteralPath $ManifestPath | ConvertFrom-Json
$actualManifestHash = Sha256-Bytes (Read-Utf8LfBytes $CasesPath)
if ($actualManifestHash -ne [string]$manifest.casesLfSha256) {
    Fail "cases LF-SHA256 does not match manifest."
}

$cases = @()
foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $CasesPath).Path, [Text.Encoding]::UTF8)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $case = $line | ConvertFrom-Json
    if ([string]$case.reviewStatus -ne "REVIEWED" -or [string]$case.reviewer -ne "codex") {
        Fail "case $($case.caseId) is not Codex-reviewed."
    }
    if ((Case-Hash $case) -ne [string]$case.frozenHash) {
        Fail "frozenHash mismatch for $($case.caseId)."
    }
    $cases += $case
}
if ($cases.Count -eq 0) { Fail "no cases found." }

$observations = @{}
foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $ObservationPath).Path, [Text.Encoding]::UTF8)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $observation = $line | ConvertFrom-Json
    $observations[[string]$observation.caseId] = $observation
}

$scored = @()
$emptySetResults = @()
foreach ($case in $cases) {
    if (-not $observations.ContainsKey([string]$case.caseId)) { Fail "missing observation for $($case.caseId)." }
    $observation = $observations[[string]$case.caseId]
    $ranked = @($observation.rankedPaperIds | ForEach-Object { [long]$_ })
    $relevant = @($case.relevantPaperIds | ForEach-Object { [long]$_ })

    if ($relevant.Count -eq 0) {
        $emptySetResults += [ordered]@{
            caseId = [string]$case.caseId
            observedStatus = [string]$observation.status
            rankedPaperIds = $ranked
        }
        continue
    }

    $rankByPaper = @{}
    for ($index = 0; $index -lt $ranked.Count; $index++) {
        if (-not $rankByPaper.ContainsKey($ranked[$index])) { $rankByPaper[$ranked[$index]] = $index + 1 }
    }
    $hitsAt = @{}
    foreach ($k in @(1, 3, 5)) {
        $hitsAt[$k] = @($relevant | Where-Object { $rankByPaper.ContainsKey($_) -and $rankByPaper[$_] -le $k }).Count
    }
    $firstRank = $relevant | Where-Object { $rankByPaper.ContainsKey($_) } | ForEach-Object { $rankByPaper[$_] } | Sort-Object | Select-Object -First 1
    $mrr = if ($null -eq $firstRank) { 0.0 } else { 1.0 / [double]$firstRank }
    $scored += [ordered]@{
        caseId = [string]$case.caseId
        relevantCount = $relevant.Count
        rankedPaperIds = $ranked
        recallAt1 = [math]::Round($hitsAt[1] / [double]$relevant.Count, 10)
        recallAt3 = [math]::Round($hitsAt[3] / [double]$relevant.Count, 10)
        recallAt5 = [math]::Round($hitsAt[5] / [double]$relevant.Count, 10)
        hitAt5 = ($hitsAt[5] -gt 0)
        mrr = [math]::Round($mrr, 10)
    }
}

function Average-Field($Rows, [string]$Name) {
    $rowsArray = @($Rows)
    $total = 0.0
    foreach ($row in $rowsArray) { $total += [double]$row[$Name] }
    return [math]::Round(($total / [double]$rowsArray.Count), 10)
}

$hitAt5Count = 0
foreach ($row in @($scored)) { if ([bool]$row['hitAt5']) { $hitAt5Count++ } }

$report = [ordered]@{
    datasetId = [string]$manifest.datasetId
    metricKind = "CODEX_REVIEWED_RETRIEVAL_OBSERVATION"
    reviewer = "codex"
    caseCount = $cases.Count
    scoredCaseCount = $scored.Count
    emptyRelevantSetCaseCount = $emptySetResults.Count
    topK = 5
    formalMetrics = [ordered]@{
        "Recall@1" = Average-Field $scored "recallAt1"
        "Recall@3" = Average-Field $scored "recallAt3"
        "Recall@5" = Average-Field $scored "recallAt5"
        "Hit@5" = [math]::Round(($hitAt5Count / [double]$scored.Count), 10)
        MRR = Average-Field $scored "mrr"
    }
    scoredCases = $scored
    emptySetCases = $emptySetResults
}

$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
$report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $resolvedOutput -Encoding UTF8
Write-Output "[RAG_REVIEWED_METRICS] outputPath=$resolvedOutput scoredCaseCount=$($scored.Count) emptyRelevantSetCaseCount=$($emptySetResults.Count) RecallAt1=$($report.formalMetrics.'Recall@1') RecallAt3=$($report.formalMetrics.'Recall@3') RecallAt5=$($report.formalMetrics.'Recall@5') HitAt5=$($report.formalMetrics.'Hit@5') MRR=$($report.formalMetrics.MRR)"
