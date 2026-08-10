[CmdletBinding()]
param(
    [switch]$ConfirmReviewAuthorization,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$ApproverId
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $ConfirmReviewAuthorization) {
    throw "Review preparation is disabled. Re-run with -ConfirmReviewAuthorization after explicit user approval."
}

$batchId = "intake-v0.1"
$reviewVersion = "human-review-v0.1"
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$datasetRoot = Join-Path $repoRoot "eval\crossref-verification-v2"
$intakeManifestPath = Join-Path $datasetRoot "manifests\intake-batch-v0.1.json"
$queuePath = Join-Path $datasetRoot "draft\review-queue-v0.1.jsonl"
$reviewRoot = Join-Path $datasetRoot "review\$batchId"
$stagingRoot = Join-Path $datasetRoot (".review-v0.1-staging-" + [guid]::NewGuid().ToString("N"))
$datasetPrefix = $datasetRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar

if (Test-Path -LiteralPath $reviewRoot) {
    throw "Refusing to overwrite review evidence: $reviewRoot"
}
if (-not $stagingRoot.StartsWith($datasetPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe review staging path: $stagingRoot"
}

function Get-Sha256 {
    param([Parameter(Mandatory)][string]$Path)

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Read-JsonFile {
    param([Parameter(Mandatory)][string]$Path)

    return Get-Content -LiteralPath $Path -Encoding utf8 -Raw | ConvertFrom-Json
}

function Resolve-RepositoryPath {
    param([Parameter(Mandatory)][string]$RepositoryPath)

    $absolute = [IO.Path]::GetFullPath((Join-Path $repoRoot $RepositoryPath))
    if (-not $absolute.StartsWith($datasetPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Dataset provenance path escaped the dataset root: $RepositoryPath"
    }
    return $absolute
}

function Get-OptionalString {
    param($Object, [Parameter(Mandatory)][string]$PropertyName)

    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$PropertyName]
    if ($null -eq $property -or $null -eq $property.Value -or
            [string]::IsNullOrWhiteSpace([string]$property.Value)) {
        return $null
    }
    return [string]$property.Value
}

function Get-FirstArrayString {
    param($Object, [Parameter(Mandatory)][string]$PropertyName)

    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$PropertyName]
    if ($null -eq $property -or $null -eq $property.Value) {
        return $null
    }
    $values = @($property.Value)
    if ($values.Count -eq 0 -or [string]::IsNullOrWhiteSpace([string]$values[0])) {
        return $null
    }
    return [string]$values[0]
}

function Get-DateParts {
    param($Object, [Parameter(Mandatory)][string]$PropertyName)

    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$PropertyName]
    if ($null -eq $property -or $null -eq $property.Value) {
        return $null
    }
    $datePartsProperty = $property.Value.PSObject.Properties["date-parts"]
    if ($null -eq $datePartsProperty -or $null -eq $datePartsProperty.Value) {
        return $null
    }
    $outer = @($datePartsProperty.Value)
    if ($outer.Count -eq 0) {
        return $null
    }
    return @($outer[0] | ForEach-Object { [int]$_ })
}

function Get-CrossrefAuthorName {
    param([Parameter(Mandatory)]$Author)

    $literalName = Get-OptionalString -Object $Author -PropertyName "name"
    if ($null -ne $literalName) {
        return $literalName
    }
    $parts = @(
        Get-OptionalString -Object $Author -PropertyName "given"
        Get-OptionalString -Object $Author -PropertyName "family"
    ) | Where-Object { $null -ne $_ }
    if ($parts.Count -eq 0) {
        throw "Crossref author has no reviewable name."
    }
    return ($parts -join " ").Trim()
}

function ConvertTo-MarkdownText {
    param($Value)

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return "_missing_"
    }
    return [Net.WebUtility]::HtmlEncode([string]$Value)
}

function ConvertTo-DateText {
    param($Parts)

    if ($null -eq $Parts) {
        return "_missing_"
    }
    return (@($Parts) -join "-")
}

$completed = $false
New-Item -ItemType Directory -Path $stagingRoot | Out-Null

try {
    $manifest = Read-JsonFile -Path $intakeManifestPath
    if ([string]$manifest.batch_id -ne $batchId -or [string]$manifest.state -ne "NEEDS_REVIEW" -or
            [int]$manifest.queue.case_count -ne 20 -or [bool]$manifest.queue.expected_labels_present) {
        throw "The intake manifest is not an eligible unreviewed 20-case batch."
    }
    if ((Get-Sha256 -Path $queuePath) -ne [string]$manifest.queue.sha256) {
        throw "Review queue hash no longer matches the immutable intake manifest."
    }

    $queueCases = @(
        Get-Content -LiteralPath $queuePath -Encoding utf8 |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
                ForEach-Object { $_ | ConvertFrom-Json }
    )
    if ($queueCases.Count -ne 20) {
        throw "Expected 20 queued cases, found $($queueCases.Count)."
    }

    $manifestCases = @{}
    foreach ($case in $manifest.cases) {
        $manifestCases[[string]$case.case_id] = $case
    }

    $packetCases = [Collections.Generic.List[object]]::new()
    foreach ($queuedCase in $queueCases) {
        if ([string]$queuedCase.review_state -ne "NEEDS_REVIEW" -or
                $null -ne $queuedCase.expected.policy_status -or
                $null -ne $queuedCase.expected.formal_admission -or
                $null -ne $queuedCase.provenance.review) {
            throw "Queue case $($queuedCase.case_id) is no longer fail closed."
        }

        $caseId = [string]$queuedCase.case_id
        $manifestCase = $manifestCases[$caseId]
        if ($null -eq $manifestCase) {
            throw "Intake manifest is missing $caseId."
        }
        $candidateSource = $manifestCase.candidate_source
        $referenceSource = $manifestCase.reference_source
        $candidatePath = Resolve-RepositoryPath -RepositoryPath ([string]$candidateSource.snapshot_path)
        $referencePath = Resolve-RepositoryPath -RepositoryPath ([string]$referenceSource.snapshot_path)
        if ((Get-Sha256 -Path $candidatePath) -ne [string]$candidateSource.sha256 -or
                (Get-Sha256 -Path $referencePath) -ne [string]$referenceSource.sha256) {
            throw "Snapshot provenance hash mismatch for $caseId."
        }

        $candidate = Read-JsonFile -Path $candidatePath
        $referenceEnvelope = Read-JsonFile -Path $referencePath
        $reference = $referenceEnvelope.message
        $candidateAuthors = @(
            foreach ($authorship in @($candidate.authorships)) {
                [ordered]@{
                    display_name = [string]$authorship.author.display_name
                    orcid = Get-OptionalString -Object $authorship.author -PropertyName "orcid"
                }
            }
        )
        $referenceAuthors = @(
            foreach ($author in @($reference.author)) {
                Get-CrossrefAuthorName -Author $author
            }
        )

        $packetCases.Add([pscustomobject][ordered]@{
            schema_version = "crossref-verification-v2-review-packet-case"
            case_id = $caseId
            review_state = "AWAITING_HUMAN_DECISION"
            observations = [ordered]@{
                candidate = [ordered]@{
                    source_id = [string]$candidateSource.source_id
                    doi = Get-OptionalString -Object $candidate -PropertyName "doi"
                    title = Get-OptionalString -Object $candidate -PropertyName "title"
                    authors = $candidateAuthors
                    publication_year = [int]$candidate.publication_year
                    publication_date = Get-OptionalString -Object $candidate -PropertyName "publication_date"
                    venue = Get-OptionalString -Object $candidate.primary_location.source -PropertyName "display_name"
                    work_type = Get-OptionalString -Object $candidate -PropertyName "type"
                    language = Get-OptionalString -Object $candidate -PropertyName "language"
                }
                reference = [ordered]@{
                    source_id = [string]$referenceSource.source_id
                    doi = Get-OptionalString -Object $reference -PropertyName "DOI"
                    title = Get-FirstArrayString -Object $reference -PropertyName "title"
                    authors = $referenceAuthors
                    published_online = Get-DateParts -Object $reference -PropertyName "published-online"
                    published_print = Get-DateParts -Object $reference -PropertyName "published-print"
                    issued = Get-DateParts -Object $reference -PropertyName "issued"
                    venue = Get-FirstArrayString -Object $reference -PropertyName "container-title"
                    work_type = Get-OptionalString -Object $reference -PropertyName "type"
                    publisher = Get-OptionalString -Object $reference -PropertyName "publisher"
                    language = Get-OptionalString -Object $reference -PropertyName "language"
                }
            }
            decision = [ordered]@{
                reviewer = $null
                reviewed_at = $null
                review_version = $null
                field_oracles = [ordered]@{
                    doi = $null
                    title = $null
                    first_author = $null
                    authors = $null
                    year = $null
                    venue = $null
                    work_type = $null
                }
                policy_status = $null
                formal_admission = $null
                rationale = $null
            }
            provenance = [ordered]@{
                candidate_snapshot_path = [string]$candidateSource.snapshot_path
                candidate_sha256 = [string]$candidateSource.sha256
                reference_snapshot_path = [string]$referenceSource.snapshot_path
                reference_sha256 = [string]$referenceSource.sha256
            }
        })
    }

    $packetPath = Join-Path $stagingRoot "review-pack-v0.1.jsonl"
    $packetLines = $packetCases | ForEach-Object { $_ | ConvertTo-Json -Depth 30 -Compress }
    [IO.File]::WriteAllLines($packetPath, $packetLines, [Text.UTF8Encoding]::new($false))

    $guidePath = Join-Path $stagingRoot "review-guide-v0.1.md"
    $guide = [Text.StringBuilder]::new()
    [void]$guide.AppendLine("# Crossref Verification v2 — intake-v0.1 review guide")
    [void]$guide.AppendLine()
    [void]$guide.AppendLine("## Authorization boundary")
    [void]$guide.AppendLine()
    [void]$guide.AppendLine("- Authorization received: ``APPROVE review intake-v0.1``.")
    [void]$guide.AppendLine("- Approver record: ``$ApproverId``.")
    [void]$guide.AppendLine("- Review version target: ``$reviewVersion``.")
    [void]$guide.AppendLine("- Current decisions: 0/20. This guide contains observations only; it does not assign Ground Truth.")
    [void]$guide.AppendLine()
    [void]$guide.AppendLine("For each case, a human reviewer must decide DOI, title, first author, complete author set, year, venue, and work type. Crossref online, print, and issued dates are deliberately shown separately. ``UNKNOWN`` remains fail closed. Overall policy status and formal admission must be confirmed independently after the field judgments.")
    [void]$guide.AppendLine()

    foreach ($packetCase in $packetCases) {
        $candidate = $packetCase.observations.candidate
        $reference = $packetCase.observations.reference
        $candidateAuthorText = (@($candidate.authors) | ForEach-Object { $_.display_name }) -join "; "
        $referenceAuthorText = @($reference.authors) -join "; "
        [void]$guide.AppendLine("## $($packetCase.case_id)")
        [void]$guide.AppendLine()
        [void]$guide.AppendLine("- Candidate DOI: ``$(ConvertTo-MarkdownText $candidate.doi)``")
        [void]$guide.AppendLine("- Crossref DOI: ``$(ConvertTo-MarkdownText $reference.doi)``")
        [void]$guide.AppendLine("- Candidate title: $(ConvertTo-MarkdownText $candidate.title)")
        [void]$guide.AppendLine("- Crossref title: $(ConvertTo-MarkdownText $reference.title)")
        [void]$guide.AppendLine("- Candidate authors ($(@($candidate.authors).Count)): $(ConvertTo-MarkdownText $candidateAuthorText)")
        [void]$guide.AppendLine("- Crossref authors ($(@($reference.authors).Count)): $(ConvertTo-MarkdownText $referenceAuthorText)")
        [void]$guide.AppendLine("- Candidate year/date: $($candidate.publication_year) / $(ConvertTo-MarkdownText $candidate.publication_date)")
        [void]$guide.AppendLine("- Crossref online / print / issued: $(ConvertTo-DateText $reference.published_online) / $(ConvertTo-DateText $reference.published_print) / $(ConvertTo-DateText $reference.issued)")
        [void]$guide.AppendLine("- Candidate venue/type: $(ConvertTo-MarkdownText $candidate.venue) / $(ConvertTo-MarkdownText $candidate.work_type)")
        [void]$guide.AppendLine("- Crossref venue/type: $(ConvertTo-MarkdownText $reference.venue) / $(ConvertTo-MarkdownText $reference.work_type)")
        [void]$guide.AppendLine("- Decision: DOI [ ] · title [ ] · first author [ ] · authors [ ] · year [ ] · venue [ ] · work type [ ] · overall [ ]")
        [void]$guide.AppendLine()
    }
    [IO.File]::WriteAllText($guidePath, $guide.ToString(), [Text.UTF8Encoding]::new($false))

    $sessionPath = Join-Path $stagingRoot "review-session-v0.1.json"
    $session = [ordered]@{
        '$schema' = "../../schema/review-session.schema.json"
        schema_version = "crossref-verification-v2-review-session"
        batch_id = $batchId
        review_version = $reviewVersion
        state = "AWAITING_CASE_DECISIONS"
        authorization = [ordered]@{
            decision = "APPROVE_REVIEW"
            approval_phrase = "APPROVE review intake-v0.1"
            approver_id = $ApproverId
            recorded_at = (Get-Date).ToUniversalTime().ToString("o")
            source = "Codex task user message"
        }
        reviewer_assignment = $null
        input = [ordered]@{
            manifest_path = "eval/crossref-verification-v2/manifests/intake-batch-v0.1.json"
            manifest_sha256 = Get-Sha256 -Path $intakeManifestPath
            queue_path = "eval/crossref-verification-v2/draft/review-queue-v0.1.jsonl"
            queue_sha256 = Get-Sha256 -Path $queuePath
        }
        outputs = [ordered]@{
            review_packet_path = "eval/crossref-verification-v2/review/intake-v0.1/review-pack-v0.1.jsonl"
            review_packet_sha256 = Get-Sha256 -Path $packetPath
            review_guide_path = "eval/crossref-verification-v2/review/intake-v0.1/review-guide-v0.1.md"
            review_guide_sha256 = Get-Sha256 -Path $guidePath
            case_count = $packetCases.Count
            decision_count = 0
        }
        constraints = [ordered]@{
            ground_truth_generated = $false
            promotion_allowed = $false
            human_case_decisions_required = $true
        }
        notes = "Review authorization is recorded, but no case has a human field judgment or promotion approval yet."
    }
    [IO.File]::WriteAllText($sessionPath, ($session | ConvertTo-Json -Depth 20) + [Environment]::NewLine,
            [Text.UTF8Encoding]::new($false))

    New-Item -ItemType Directory -Path (Split-Path -Parent $reviewRoot) -Force | Out-Null
    Move-Item -LiteralPath $stagingRoot -Destination $reviewRoot
    $completed = $true
    Write-Output "REVIEW_PREPARATION_COMPLETE"
    Write-Output "CASE_COUNT=$($packetCases.Count)"
    Write-Output "DECISION_COUNT=0"
    Write-Output "STATE=AWAITING_CASE_DECISIONS"
} finally {
    if (-not $completed -and (Test-Path -LiteralPath $stagingRoot)) {
        if (-not $stagingRoot.StartsWith($datasetPrefix, [StringComparison]::OrdinalIgnoreCase) -or
                -not (Split-Path -Leaf $stagingRoot).StartsWith(".review-v0.1-staging-", [StringComparison]::Ordinal)) {
            throw "Refusing to remove unsafe review staging path: $stagingRoot"
        }
        Remove-Item -LiteralPath $stagingRoot -Recurse -Force
    }
}
