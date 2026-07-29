[CmdletBinding()]
param(
    [string]$LogPath = "",

    [string]$TaskId = "",

    [switch]$AsJson
)

$ErrorActionPreference = "Stop"

function Get-RequiredLogValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Line,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $pattern = "(?:^|\s)" + [regex]::Escape($Name) + "=([^\s]+)"
    $match = [regex]::Match($Line, $pattern)
    if (-not $match.Success) {
        throw "Completion log is missing required field: $Name"
    }

    return $match.Groups[1].Value
}

if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    throw "PSScriptRoot is empty. Run this file as a .ps1 script."
}

if ([string]::IsNullOrWhiteSpace($LogPath)) {
    $repoRoot = Split-Path -Parent $PSScriptRoot
    $LogPath = Join-Path $repoRoot "logs\research-pilot.log"
}

if (-not (Test-Path -LiteralPath $LogPath -PathType Leaf)) {
    throw "ResearchPilot log file was not found: $LogPath"
}

if (-not [string]::IsNullOrWhiteSpace($TaskId)) {
    $parsedTaskId = [guid]::Empty
    if (-not [guid]::TryParse($TaskId, [ref]$parsedTaskId)) {
        throw "TaskId must be a valid UUID."
    }
    $TaskId = $parsedTaskId.ToString()
}

$completionLines = @(
    Select-String `
        -LiteralPath $LogPath `
        -SimpleMatch `
        -Pattern "event=literature_search_completed" |
        ForEach-Object { $_.Line }
)

if (-not [string]::IsNullOrWhiteSpace($TaskId)) {
    $taskPattern = "(?:^|\s)taskId=" + [regex]::Escape($TaskId) + "(?:\s|$)"
    $completionLines = @(
        $completionLines |
            Where-Object { $_ -match $taskPattern }
    )
}

if ($completionLines.Count -eq 0) {
    $scope = if ([string]::IsNullOrWhiteSpace($TaskId)) {
        "any task"
    }
    else {
        "taskId=$TaskId"
    }
    throw "No literature search completion log was found for $scope."
}

$line = $completionLines[-1]
$loggedTaskId = Get-RequiredLogValue -Line $line -Name "taskId"
$reviewStatus = Get-RequiredLogValue -Line $line -Name "reviewStatus"
$modelCallCount = [int](Get-RequiredLogValue -Line $line -Name "reviewModelCallCount")
$repairCount = [int](Get-RequiredLogValue -Line $line -Name "reviewRepairCount")
$evidenceCount = [int](Get-RequiredLogValue -Line $line -Name "reviewEvidenceCount")
$citationCount = [int](Get-RequiredLogValue -Line $line -Name "reviewCitationCount")
$elapsedMs = [long](Get-RequiredLogValue -Line $line -Name "elapsedMs")

if ($modelCallCount -lt 0 -or $modelCallCount -gt 2) {
    throw "Logged reviewModelCallCount is outside the Java-owned range 0..2."
}
if ($repairCount -lt 0 -or $repairCount -gt 1 -or $repairCount -gt $modelCallCount) {
    throw "Logged reviewRepairCount is inconsistent with reviewModelCallCount."
}

$result = [pscustomobject][ordered]@{
    taskId = $loggedTaskId
    reviewStatus = $reviewStatus
    reviewModelCallCount = $modelCallCount
    reviewRepairCount = $repairCount
    reviewEvidenceCount = $evidenceCount
    reviewCitationCount = $citationCount
    elapsedMs = $elapsedMs
}

if ($AsJson) {
    $result | ConvertTo-Json -Compress
}
else {
    $result
}
