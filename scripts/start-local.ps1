[CmdletBinding()]
param(
    [ValidateSet("OfflineBuild", "TrustedSearch", "FullDemo")]
    [string]$Mode = "OfflineBuild",

    [switch]$RunTests,
    [switch]$EnableCache,

    [string]$LlmBaseUrl,
    [string]$LlmModelName,
    [string]$CrossrefMailto,
    [string]$CrossrefUserAgent,

    [string]$MysqlHost = "localhost",
    [ValidateRange(1, 65535)]
    [int]$MysqlPort = 3306,
    [string]$MysqlDatabase = "research_pilot",
    [string]$MysqlUsername = "research_pilot",

    [string]$RedisHost = "localhost",
    [ValidateRange(1, 65535)]
    [int]$RedisPort = 6379,
    [string]$RedisUsername = ""
)

$ErrorActionPreference = "Stop"

function Read-PlainTextSecret {
    param([Parameter(Mandatory = $true)][string]$Prompt)

    $secureValue = Read-Host $Prompt -AsSecureString
    return ([System.Net.NetworkCredential]::new("", $secureValue)).Password
}

function Require-Text {
    param([string]$Value, [string]$Name)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Name is required for $Mode."
    }
    return $Value.Trim()
}

function Test-RequiredTcpService {
    param([string]$HostName, [int]$Port, [string]$DisplayName)

    $reachable = Test-NetConnection -ComputerName $HostName -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue
    if (-not $reachable) {
        throw "$DisplayName is required by the selected mode but ${HostName}:${Port} is unreachable."
    }
    Write-Host "$DisplayName endpoint is reachable." -ForegroundColor Green
}

if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    throw "PSScriptRoot is empty. Run this file as a .ps1 script."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"
if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw "mvnw.cmd was not found in project directory: $repoRoot"
}

Set-Location -LiteralPath $repoRoot

# Always override inherited feature switches so each mode is reproducible.
$env:LLM_ENABLED = "false"
$env:OPENALEX_ENABLED = "false"
$env:CROSSREF_ENABLED = "false"
$env:FLYWAY_ENABLED = "false"
$env:LITERATURE_PERSISTENCE_ENABLED = "false"
$env:LITERATURE_CACHE_ENABLED = "false"
# Redis clients remain configured with a harmless local endpoint even when the
# cache is disabled; this does not probe or require Redis.
$env:REDIS_HOST = $RedisHost
$env:REDIS_PORT = $RedisPort.ToString()
$env:REDIS_USERNAME = $RedisUsername
Remove-Item -Path "Env:REDIS_PASSWORD" -ErrorAction SilentlyContinue

Write-Host "Project directory: $repoRoot" -ForegroundColor Cyan
Write-Host "Mode: $Mode" -ForegroundColor Cyan

if ($Mode -eq "OfflineBuild") {
    Write-Host "OfflineBuild contacts no LLM, OpenAlex, Crossref, MySQL, or Redis service." -ForegroundColor Yellow
    & $mavenWrapper clean verify
    if ($LASTEXITCODE -ne 0) {
        throw "Maven clean verify failed. Exit code: $LASTEXITCODE"
    }
    exit 0
}

$LlmBaseUrl = Require-Text $LlmBaseUrl "LlmBaseUrl"
$LlmModelName = Require-Text $LlmModelName "LlmModelName"
$CrossrefMailto = Require-Text $CrossrefMailto "CrossrefMailto"
$CrossrefUserAgent = Require-Text $CrossrefUserAgent "CrossrefUserAgent"

if ($LlmBaseUrl -notmatch "^https?://") {
    throw "LlmBaseUrl must start with http:// or https://."
}
if ($CrossrefMailto -notmatch "^[^@\s]+@[^@\s]+\.[^@\s]+$") {
    throw "CrossrefMailto must be a valid contact email address."
}
if ($CrossrefMailto -match "your-email@example\.com") {
    throw "CrossrefMailto still contains the documentation placeholder."
}
if ($EnableCache -and [string]::IsNullOrWhiteSpace($RedisHost)) {
    throw "RedisHost is required when -EnableCache is used."
}

$env:LLM_ENABLED = "true"
$env:LLM_BASE_URL = $LlmBaseUrl.TrimEnd('/')
$env:LLM_MODEL_NAME = $LlmModelName
$env:OPENALEX_ENABLED = "true"
$env:CROSSREF_ENABLED = "true"
$env:CROSSREF_MAILTO = $CrossrefMailto
$env:CROSSREF_USER_AGENT = $CrossrefUserAgent
$env:LITERATURE_CACHE_ENABLED = $EnableCache.IsPresent.ToString().ToLowerInvariant()

$secretNames = @("LLM_API_KEY", "OPENALEX_API_KEY", "CROSSREF_PLUS_TOKEN", "MYSQL_PASSWORD", "REDIS_PASSWORD")
try {
    $env:LLM_API_KEY = Read-PlainTextSecret "Enter LLM API key"
    if ([string]::IsNullOrWhiteSpace($env:LLM_API_KEY)) {
        throw "LLM API key is required for $Mode."
    }

    $env:OPENALEX_API_KEY = Read-PlainTextSecret "Enter optional OpenAlex API key, or press Enter to omit"
    $env:CROSSREF_PLUS_TOKEN = Read-PlainTextSecret "Enter optional Crossref Plus token, or press Enter to omit"

    if ($EnableCache) {
        Test-RequiredTcpService $RedisHost $RedisPort "Redis"
        $env:REDIS_HOST = $RedisHost
        $env:REDIS_PORT = $RedisPort.ToString()
        $env:REDIS_USERNAME = $RedisUsername
        $env:REDIS_PASSWORD = Read-PlainTextSecret "Enter Redis password, or press Enter if empty"
    }

    if ($Mode -eq "FullDemo") {
        Test-RequiredTcpService $MysqlHost $MysqlPort "MySQL"
        $env:MYSQL_URL = "jdbc:mysql://${MysqlHost}:${MysqlPort}/${MysqlDatabase}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
        $env:MYSQL_USERNAME = $MysqlUsername
        $env:MYSQL_PASSWORD = Read-PlainTextSecret "Enter MySQL password"
        $env:FLYWAY_ENABLED = "true"
        $env:LITERATURE_PERSISTENCE_ENABLED = "true"
    }

    Write-Host "Non-sensitive configuration:" -ForegroundColor Cyan
    Write-Host "  LLM model       : $env:LLM_MODEL_NAME"
    Write-Host "  OpenAlex        : enabled"
    Write-Host "  Crossref        : enabled"
    Write-Host "  Persistence     : $env:LITERATURE_PERSISTENCE_ENABLED"
    Write-Host "  Flyway          : $env:FLYWAY_ENABLED"
    Write-Host "  Redis cache     : $env:LITERATURE_CACHE_ENABLED"
    Write-Host "Secrets are not printed. No standalone HTTP probe is sent; provider calls occur only through a search request."

    if ($RunTests) {
        & $mavenWrapper test
        if ($LASTEXITCODE -ne 0) {
            throw "Maven test failed. Exit code: $LASTEXITCODE"
        }
    }

    Write-Host "Starting ResearchPilot. Stop with Ctrl+C." -ForegroundColor Green
    & $mavenWrapper spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "ResearchPilot stopped with exit code: $LASTEXITCODE"
    }
}
finally {
    foreach ($secretName in $secretNames) {
        Remove-Item -Path "Env:$secretName" -ErrorAction SilentlyContinue
    }
}
