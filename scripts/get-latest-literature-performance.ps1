[CmdletBinding()]
param(
    [string]$LogPath = "",

    [Parameter(Mandatory = $true)]
    [string]$TaskId,

    [switch]$AsJson
)

$ErrorActionPreference = "Stop"

function Parse-LogFields {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Line
    )

    $fields = [ordered]@{}
    foreach ($match in [regex]::Matches($Line, "(?:^|\s)([A-Za-z][A-Za-z0-9]*)=([^\s]+)")) {
        $fields[$match.Groups[1].Value] = $match.Groups[2].Value
    }
    return $fields
}

function Require-NonNegativeDuration {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$Fields,

        [Parameter(Mandatory = $true)]
        [string]$EventName
    )

    if (-not $Fields.Contains("durationMs")) {
        throw "$EventName is missing durationMs."
    }
    $duration = 0L
    if (-not [long]::TryParse($Fields["durationMs"], [ref]$duration) -or $duration -lt 0) {
        throw "$EventName has an invalid durationMs."
    }
    return $duration
}

function Group-PerformanceEvents {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [object[]]$Events,

        [Parameter(Mandatory = $true)]
        [string[]]$Dimensions
    )

    if ($Events.Count -eq 0) {
        return @()
    }

    return @(
        $Events |
            Group-Object {
                $event = $_
                ($Dimensions | ForEach-Object { [string]$event.$_ }) -join "|"
            } |
            ForEach-Object {
                $first = $_.Group[0]
                $durations = @($_.Group | ForEach-Object { [long]$_.durationMs })
                $result = [ordered]@{}
                foreach ($dimension in $Dimensions) {
                    $result[$dimension] = $first.$dimension
                }
                $result["count"] = $_.Count
                $result["totalMs"] = [long](($durations | Measure-Object -Sum).Sum)
                $result["maxMs"] = [long](($durations | Measure-Object -Maximum).Maximum)
                $result["averageMs"] = [math]::Round(
                    [double](($durations | Measure-Object -Average).Average),
                    3
                )
                [pscustomobject]$result
            }
    )
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

$parsedTaskId = [guid]::Empty
if (-not [guid]::TryParse($TaskId, [ref]$parsedTaskId)) {
    throw "TaskId must be a valid UUID."
}
$TaskId = $parsedTaskId.ToString()

$lines = @(Get-Content -LiteralPath $LogPath)
$taskPattern = "(?:^|\s)taskId=" + [regex]::Escape($TaskId) + "(?:\s|$)"
$targetIndex = -1
for ($index = 0; $index -lt $lines.Count; $index++) {
    if ($lines[$index] -match "event=literature_search_completed" -and
            $lines[$index] -match $taskPattern) {
        $targetIndex = $index
    }
}
if ($targetIndex -lt 0) {
    throw "No literature search completion log was found for taskId=$TaskId."
}

$previousCompletionIndex = -1
for ($index = $targetIndex - 1; $index -ge 0; $index--) {
    if ($lines[$index] -match "event=literature_search_completed") {
        $previousCompletionIndex = $index
        break
    }
}
$processStartIndex = -1
for ($index = $targetIndex - 1; $index -ge 0; $index--) {
    if ($lines[$index] -match "Started ResearchPilotApplication in") {
        $processStartIndex = $index
        break
    }
}
$windowStartIndex = [math]::Max($previousCompletionIndex, $processStartIndex) + 1
$window = @($lines[$windowStartIndex..$targetIndex])

$cacheEvents = @()
$persistenceEvents = @()
$modelEvents = @()
$agentEvents = @()

foreach ($line in $window) {
    if ($line -match "event=literature_cache_access") {
        $fields = Parse-LogFields -Line $line
        $cacheEvents += [pscustomobject]@{
            provider = $fields["provider"]
            operation = $fields["operation"]
            phase = $fields["phase"]
            outcome = $fields["outcome"]
            durationMs = Require-NonNegativeDuration -Fields $fields -EventName "cache event"
        }
        continue
    }
    if ($line -match "event=literature_persistence") {
        $fields = Parse-LogFields -Line $line
        $persistenceEvents += [pscustomobject]@{
            operation = $fields["operation"]
            outcome = $fields["outcome"]
            durationMs = Require-NonNegativeDuration -Fields $fields -EventName "persistence event"
        }
        continue
    }
    if ($line -match "event=model_call_(succeeded|failed)") {
        $fields = Parse-LogFields -Line $line
        $modelEvents += [pscustomobject]@{
            operation = $fields["operation"]
            outcome = if ($line -match "event=model_call_succeeded") { "SUCCEEDED" } else { "FAILED" }
            durationMs = Require-NonNegativeDuration -Fields $fields -EventName "model event"
        }
        continue
    }
    if ($line -match "event=literature_agent_step") {
        $fields = Parse-LogFields -Line $line
        $agentEvents += [pscustomobject]@{
            action = $fields["action"]
            status = $fields["status"]
            durationMs = Require-NonNegativeDuration -Fields $fields -EventName "Agent step event"
        }
    }
}

$completion = Parse-LogFields -Line $lines[$targetIndex]
$slowest = $agentEvents | Sort-Object -Property durationMs -Descending | Select-Object -First 1
$unmeasured = @()
if ($cacheEvents.Count -eq 0) { $unmeasured += "REDIS_CACHE_EVENTS" }
if ($persistenceEvents.Count -eq 0) { $unmeasured += "MYSQL_PERSISTENCE_EVENTS" }
if ($modelEvents.Count -eq 0) { $unmeasured += "LLM_CALL_EVENTS" }
if ($agentEvents.Count -eq 0) { $unmeasured += "AGENT_STEP_EVENTS" }

$cacheSummary = @(
    Group-PerformanceEvents `
        -Events $cacheEvents `
        -Dimensions @("provider", "operation", "phase", "outcome")
)
$persistenceSummary = @(
    Group-PerformanceEvents `
        -Events $persistenceEvents `
        -Dimensions @("operation", "outcome")
)
$modelCallSummary = @(
    Group-PerformanceEvents `
        -Events $modelEvents `
        -Dimensions @("operation", "outcome")
)
$agentStepSummary = @(
    Group-PerformanceEvents `
        -Events $agentEvents `
        -Dimensions @("action", "status")
)

$result = [pscustomobject][ordered]@{
    taskId = $TaskId
    status = $completion["agentStage"]
    terminationReason = $completion["terminationReason"]
    elapsedMs = [long]$completion["elapsedMs"]
    measurementScope = if ($processStartIndex -gt $previousCompletionIndex) {
        "isolated serial log window since current application start"
    }
    else {
        "isolated serial log window since previous completed literature task"
    }
    cache = $cacheSummary
    persistence = $persistenceSummary
    modelCalls = $modelCallSummary
    agentSteps = $agentStepSummary
    slowestAgentAction = if ($null -eq $slowest) {
        $null
    }
    else {
        [pscustomobject]@{
            action = $slowest.action
            status = $slowest.status
            durationMs = [long]$slowest.durationMs
        }
    }
    unmeasured = $unmeasured
}

if ($AsJson) {
    $result | ConvertTo-Json -Depth 8 -Compress
}
else {
    $result
}
