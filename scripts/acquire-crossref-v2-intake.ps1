[CmdletBinding()]
param(
    [switch]$ConfirmPublicApiCalls
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if (-not $ConfirmPublicApiCalls) {
    throw "Public API acquisition is disabled. Re-run with -ConfirmPublicApiCalls after explicit authorization."
}

$targetPairCount = 20
$sampleSize = 100
$sampleSeed = 20260810
$maxCasesPerField = 3
$batchId = "intake-v0.1"
$userAgent = "ResearchPilot-Evaluation/0.1 (+https://github.com/DJ1012-H/research-pilot)"
$openAlexSelect = "id,doi,title,display_name,publication_year,publication_date,type,language,authorships,primary_location,primary_topic,is_retracted"

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$datasetRoot = Join-Path $repoRoot "eval\crossref-verification-v2"
$finalSnapshotRoot = Join-Path $datasetRoot "fixtures\$batchId"
$finalQueuePath = Join-Path $datasetRoot "draft\review-queue-v0.1.jsonl"
$finalManifestPath = Join-Path $datasetRoot "manifests\intake-batch-v0.1.json"

foreach ($immutableTarget in @($finalSnapshotRoot, $finalQueuePath, $finalManifestPath)) {
    if (Test-Path -LiteralPath $immutableTarget) {
        throw "Refusing to overwrite immutable intake output: $immutableTarget"
    }
}

$stagingRoot = Join-Path $datasetRoot (".intake-v0.1-staging-" + [guid]::NewGuid().ToString("N"))
$datasetPrefix = $datasetRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (-not $stagingRoot.StartsWith($datasetPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe staging path: $stagingRoot"
}

function Get-RelativeRepositoryPath {
    param([Parameter(Mandatory)][string]$AbsolutePath)

    $rootPrefix = $repoRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $AbsolutePath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside the repository: $AbsolutePath"
    }
    $relative = $AbsolutePath.Substring($rootPrefix.Length)
    return $relative.Replace([IO.Path]::DirectorySeparatorChar, "/")
}

function Get-Sha256 {
    param([Parameter(Mandatory)][string]$Path)

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-NormalizedDoi {
    param([Parameter(Mandatory)][string]$Doi)

    return ($Doi.Trim() -replace "^(?i:https?://(?:dx\.)?doi\.org/)", "").ToLowerInvariant()
}

function Get-DoiPath {
    param([Parameter(Mandatory)][string]$Doi)

    return (($Doi -split "/" | ForEach-Object { [uri]::EscapeDataString($_) }) -join "/")
}

function Invoke-SnapshotRequest {
    param(
        [Parameter(Mandatory)][string]$Uri,
        [Parameter(Mandatory)][string]$OutFile,
        [int]$DelayMilliseconds = 0,
        [switch]$AllowNotFound
    )

    $transientStatuses = @(0, 403, 429, 500, 502, 503, 504)
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        if ($DelayMilliseconds -gt 0) {
            Start-Sleep -Milliseconds $DelayMilliseconds
        }

        try {
            $response = Invoke-WebRequest -Uri $Uri -Method Get -Headers @{ "User-Agent" = $userAgent } `
                    -TimeoutSec 45 -MaximumRedirection 5 -UseBasicParsing -OutFile $OutFile -PassThru
            $statusCode = [int]$response.StatusCode
            if ($statusCode -ne 200) {
                throw "Unexpected HTTP status $statusCode for $Uri"
            }
            return [pscustomobject]@{
                Found = $true
                StatusCode = $statusCode
                RetrievedAt = (Get-Date).ToUniversalTime().ToString("o")
            }
        } catch {
            $statusCode = 0
            $retryAfterSeconds = $null
            $responseProperty = $_.Exception.PSObject.Properties["Response"]
            if ($null -ne $responseProperty -and $null -ne $responseProperty.Value) {
                $errorResponse = $responseProperty.Value
                $statusCode = [int]$errorResponse.StatusCode
                $headersProperty = $errorResponse.PSObject.Properties["Headers"]
                if ($null -ne $headersProperty -and $null -ne $headersProperty.Value) {
                    $retryAfterProperty = $headersProperty.Value.PSObject.Properties["RetryAfter"]
                    if ($null -ne $retryAfterProperty -and $null -ne $retryAfterProperty.Value -and
                            $null -ne $retryAfterProperty.Value.Delta) {
                        $retryAfterSeconds = [Math]::Min(30,
                                [Math]::Ceiling($retryAfterProperty.Value.Delta.TotalSeconds))
                    }
                }
            }
            if (Test-Path -LiteralPath $OutFile) {
                Remove-Item -LiteralPath $OutFile -Force
            }
            if ($AllowNotFound -and $statusCode -eq 404) {
                return [pscustomobject]@{
                    Found = $false
                    StatusCode = 404
                    RetrievedAt = $null
                }
            }
            if ($attempt -eq 3 -or $statusCode -notin $transientStatuses) {
                throw "HTTP acquisition failed for $Uri after $attempt attempt(s), status=$statusCode. $($_.Exception.Message)"
            }
            $backoffSeconds = if ($null -ne $retryAfterSeconds) { $retryAfterSeconds } else { [Math]::Pow(2, $attempt) }
            Start-Sleep -Seconds $backoffSeconds
        }
    }
}

function Test-CandidateShape {
    param([Parameter(Mandatory)]$Work)

    return $null -ne $Work.id -and
            $Work.id -match "^https://openalex\.org/W[0-9]+$" -and
            $null -ne $Work.doi -and
            $Work.doi -match "^https?://doi\.org/10\." -and
            -not [string]::IsNullOrWhiteSpace([string]$Work.title) -and
            [int]$Work.publication_year -gt 0 -and
            [string]$Work.type -eq "article" -and
            -not [bool]$Work.is_retracted -and
            $null -ne $Work.authorships -and
            @($Work.authorships).Count -gt 0 -and
            $null -ne $Work.primary_location -and
            $null -ne $Work.primary_location.source -and
            -not [string]::IsNullOrWhiteSpace([string]$Work.primary_location.source.id) -and
            -not [string]::IsNullOrWhiteSpace([string]$Work.primary_location.source.display_name) -and
            $null -ne $Work.primary_topic -and
            $null -ne $Work.primary_topic.field -and
            -not [string]::IsNullOrWhiteSpace([string]$Work.primary_topic.field.id) -and
            -not [string]::IsNullOrWhiteSpace([string]$Work.primary_topic.field.display_name)
}

$completed = $false
New-Item -ItemType Directory -Path (Join-Path $stagingRoot "selection") -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stagingRoot "candidate") -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stagingRoot "reference") -Force | Out-Null

try {
    $selectionUrl = "https://api.openalex.org/works?filter=type:article,has_doi:true,is_retracted:false&sample=$sampleSize&per_page=$sampleSize&seed=$sampleSeed&select=$openAlexSelect"
    $selectionPath = Join-Path $stagingRoot "selection\openalex-sample-seed-$sampleSeed.json"
    $selectionResponse = Invoke-SnapshotRequest -Uri $selectionUrl -OutFile $selectionPath
    $selection = Get-Content -LiteralPath $selectionPath -Encoding utf8 -Raw | ConvertFrom-Json
    $selectionResults = @($selection.results)
    if ($selectionResults.Count -lt $targetPairCount) {
        throw "OpenAlex returned only $($selectionResults.Count) candidates; at least $targetPairCount are required."
    }

    $selectedCases = [Collections.Generic.List[object]]::new()
    $usedDois = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $usedPrimarySources = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $fieldCounts = @{}

    foreach ($candidate in $selectionResults) {
        if ($selectedCases.Count -ge $targetPairCount) {
            break
        }
        if (-not (Test-CandidateShape -Work $candidate)) {
            continue
        }

        $doi = Get-NormalizedDoi -Doi ([string]$candidate.doi)
        $primarySourceId = [string]$candidate.primary_location.source.id
        $fieldId = [string]$candidate.primary_topic.field.id
        $currentFieldCount = if ($fieldCounts.ContainsKey($fieldId)) { [int]$fieldCounts[$fieldId] } else { 0 }
        if ($usedDois.Contains($doi) -or $usedPrimarySources.Contains($primarySourceId) -or
                $currentFieldCount -ge $maxCasesPerField) {
            continue
        }

        $caseNumber = $selectedCases.Count + 1
        $caseId = "crv2-case-{0:D4}" -f $caseNumber
        $openAlexId = ([string]$candidate.id).Substring(([string]$candidate.id).LastIndexOf("/") + 1)
        $candidateUrl = "https://api.openalex.org/works/$openAlexId`?select=$openAlexSelect"
        $candidatePath = Join-Path $stagingRoot "candidate\$caseId-openalex.json"
        $candidateResponse = Invoke-SnapshotRequest -Uri $candidateUrl -OutFile $candidatePath -DelayMilliseconds 200
        $candidateSnapshot = Get-Content -LiteralPath $candidatePath -Encoding utf8 -Raw | ConvertFrom-Json
        if (-not (Test-CandidateShape -Work $candidateSnapshot)) {
            throw "OpenAlex singleton failed the acquisition shape contract for $openAlexId."
        }
        if ((Get-NormalizedDoi -Doi ([string]$candidateSnapshot.doi)) -ne $doi -or
                [string]$candidateSnapshot.id -ne [string]$candidate.id) {
            throw "OpenAlex sample/singleton identity mismatch for $openAlexId."
        }

        $crossrefUrl = "https://api.crossref.org/works/$(Get-DoiPath -Doi $doi)"
        $referencePath = Join-Path $stagingRoot "reference\$caseId-crossref.json"
        $referenceResponse = Invoke-SnapshotRequest -Uri $crossrefUrl -OutFile $referencePath `
                -DelayMilliseconds 350 -AllowNotFound
        if (-not $referenceResponse.Found) {
            Remove-Item -LiteralPath $candidatePath -Force
            continue
        }

        $referenceSnapshot = Get-Content -LiteralPath $referencePath -Encoding utf8 -Raw | ConvertFrom-Json
        if ([string]$referenceSnapshot.status -ne "ok" -or $null -eq $referenceSnapshot.message -or
                [string]::IsNullOrWhiteSpace([string]$referenceSnapshot.message.DOI)) {
            throw "Crossref returned a non-work response for DOI $doi."
        }
        if ((Get-NormalizedDoi -Doi ([string]$referenceSnapshot.message.DOI)) -ne $doi) {
            throw "Crossref DOI mismatch for requested DOI $doi."
        }

        [void]$usedDois.Add($doi)
        [void]$usedPrimarySources.Add($primarySourceId)
        $fieldCounts[$fieldId] = $currentFieldCount + 1

        $candidateRelativePath = "eval/crossref-verification-v2/fixtures/$batchId/candidate/$caseId-openalex.json"
        $referenceRelativePath = "eval/crossref-verification-v2/fixtures/$batchId/reference/$caseId-crossref.json"
        $selectedCases.Add([pscustomobject][ordered]@{
            case_id = $caseId
            doi = $doi
            openalex_id = [string]$candidateSnapshot.id
            publication_year = [int]$candidateSnapshot.publication_year
            language = if ($null -eq $candidateSnapshot.language) { $null } else { [string]$candidateSnapshot.language }
            primary_source = [ordered]@{
                id = [string]$candidateSnapshot.primary_location.source.id
                name = [string]$candidateSnapshot.primary_location.source.display_name
            }
            primary_topic_field = [ordered]@{
                id = [string]$candidateSnapshot.primary_topic.field.id
                name = [string]$candidateSnapshot.primary_topic.field.display_name
            }
            candidate_source = [ordered]@{
                source_id = "openalex-$openAlexId"
                snapshot_path = $candidateRelativePath
                source_url = $candidateUrl
                retrieved_at = $candidateResponse.RetrievedAt
                sha256 = Get-Sha256 -Path $candidatePath
            }
            reference_source = [ordered]@{
                source_id = "crossref-doi:$doi"
                snapshot_path = $referenceRelativePath
                source_url = $crossrefUrl
                retrieved_at = $referenceResponse.RetrievedAt
                sha256 = Get-Sha256 -Path $referencePath
            }
        })
    }

    if ($selectedCases.Count -ne $targetPairCount) {
        throw "Fail closed: selected $($selectedCases.Count) valid independent pairs, target is $targetPairCount. No intake outputs were published."
    }

    $queuePath = Join-Path $stagingRoot "review-queue-v0.1.jsonl"
    $queueLines = foreach ($case in $selectedCases) {
        $queueCase = [ordered]@{
            schema_version = "crossref-verification-v2-review-queue"
            case_id = $case.case_id
            review_state = "NEEDS_REVIEW"
            input = [ordered]@{
                candidate_source_id = $case.candidate_source.source_id
                reference_source_ids = @($case.reference_source.source_id)
            }
            expected = [ordered]@{
                policy_status = $null
                formal_admission = $null
                field_oracles = [ordered]@{
                    doi = $null
                    title = $null
                    first_author = $null
                    authors = $null
                    year = $null
                    venue = $null
                    work_type = $null
                }
            }
            provenance = [ordered]@{
                sources = @(
                    [ordered]@{
                        source_id = $case.candidate_source.source_id
                        role = "CANDIDATE"
                        snapshot_path = $case.candidate_source.snapshot_path
                        source_url = $case.candidate_source.source_url
                        retrieved_at = $case.candidate_source.retrieved_at
                        sha256 = $case.candidate_source.sha256
                    },
                    [ordered]@{
                        source_id = $case.reference_source.source_id
                        role = "REFERENCE"
                        snapshot_path = $case.reference_source.snapshot_path
                        source_url = $case.reference_source.source_url
                        retrieved_at = $case.reference_source.retrieved_at
                        sha256 = $case.reference_source.sha256
                    }
                )
                review = $null
            }
            notes = "Independent OpenAlex/Crossref snapshots acquired; every bibliographic and policy expectation requires explicit human review."
        }
        $queueCase | ConvertTo-Json -Depth 20 -Compress
    }
    [IO.File]::WriteAllLines($queuePath, $queueLines, [Text.UTF8Encoding]::new($false))

    $manifestPath = Join-Path $stagingRoot "intake-batch-v0.1.json"
    $manifest = [ordered]@{
        '$schema' = "../schema/intake-batch.schema.json"
        schema_version = "crossref-verification-v2-intake-batch"
        batch_id = $batchId
        dataset_version = "crossref-verification-v2"
        state = "NEEDS_REVIEW"
        generated_at = (Get-Date).ToUniversalTime().ToString("o")
        request_mode = "ANONYMOUS_READ_ONLY"
        user_agent = $userAgent
        selection = [ordered]@{
            provider = "OpenAlex"
            seed = $sampleSeed
            query_url = $selectionUrl
            snapshot_path = "eval/crossref-verification-v2/fixtures/$batchId/selection/openalex-sample-seed-$sampleSeed.json"
            sha256 = Get-Sha256 -Path $selectionPath
            retrieved_at = $selectionResponse.RetrievedAt
            requested_sample_size = $sampleSize
            returned_count = $selectionResults.Count
        }
        selection_policy = [ordered]@{
            target_pair_count = $targetPairCount
            actual_pair_count = $selectedCases.Count
            article_only = $true
            doi_required = $true
            retracted_excluded = $true
            unique_primary_source_required = $true
            max_cases_per_primary_topic_field = $maxCasesPerField
            crossref_lookup_status_required = 200
            ground_truth_generated = $false
        }
        queue = [ordered]@{
            path = "eval/crossref-verification-v2/draft/review-queue-v0.1.jsonl"
            sha256 = Get-Sha256 -Path $queuePath
            case_count = $selectedCases.Count
            review_state = "NEEDS_REVIEW"
            expected_labels_present = $false
        }
        cases = @($selectedCases)
        notes = "Acquisition evidence only. No API response, production policy, or model output is treated as ground truth."
    }
    [IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 30) + [Environment]::NewLine,
            [Text.UTF8Encoding]::new($false))

    New-Item -ItemType Directory -Path (Split-Path -Parent $finalSnapshotRoot) -Force | Out-Null
    Move-Item -LiteralPath (Join-Path $stagingRoot "selection") -Destination (Join-Path $stagingRoot "published-selection")
    New-Item -ItemType Directory -Path $finalSnapshotRoot | Out-Null
    Move-Item -LiteralPath (Join-Path $stagingRoot "published-selection") -Destination (Join-Path $finalSnapshotRoot "selection")
    Move-Item -LiteralPath (Join-Path $stagingRoot "candidate") -Destination (Join-Path $finalSnapshotRoot "candidate")
    Move-Item -LiteralPath (Join-Path $stagingRoot "reference") -Destination (Join-Path $finalSnapshotRoot "reference")
    Move-Item -LiteralPath $queuePath -Destination $finalQueuePath
    Move-Item -LiteralPath $manifestPath -Destination $finalManifestPath
    $completed = $true

    Write-Output "ACQUISITION_COMPLETE"
    Write-Output "BATCH_ID=$batchId"
    Write-Output "PAIR_COUNT=$($selectedCases.Count)"
    Write-Output "QUEUE=$(Get-RelativeRepositoryPath -AbsolutePath $finalQueuePath)"
    Write-Output "MANIFEST=$(Get-RelativeRepositoryPath -AbsolutePath $finalManifestPath)"
} finally {
    if (Test-Path -LiteralPath $stagingRoot) {
        if (-not $stagingRoot.StartsWith($datasetPrefix, [StringComparison]::OrdinalIgnoreCase) -or
                -not (Split-Path -Leaf $stagingRoot).StartsWith(".intake-v0.1-staging-", [StringComparison]::Ordinal)) {
            throw "Refusing to remove unsafe staging path: $stagingRoot"
        }
        Remove-Item -LiteralPath $stagingRoot -Recurse -Force
    }
    if (-not $completed) {
        Write-Warning "Acquisition did not publish a complete intake batch."
    }
}
