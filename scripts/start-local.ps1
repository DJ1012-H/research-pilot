[CmdletBinding()]
param(
    [string]$MysqlHost = "localhost",

    [ValidateRange(1, 65535)]
    [int]$MysqlPort = 3306,

    [string]$MysqlDatabase = "research_pilot",

    [string]$MysqlUsername = "research_pilot",

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$RedisHost,

    [ValidateRange(1, 65535)]
    [int]$RedisPort = 6379,

    [string]$RedisUsername = "",

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$LlmBaseUrl,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$LlmModelName
)

$ErrorActionPreference = "Stop"

function Read-PlainTextSecret {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Prompt
    )

    $secureValue = Read-Host $Prompt -AsSecureString
    $credential = New-Object System.Net.NetworkCredential("", $secureValue)
    return $credential.Password
}

if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    throw "PSScriptRoot is empty. Run this file as a .ps1 script."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$pomPath = Join-Path $repoRoot "pom.xml"

if (-not (Test-Path -LiteralPath $pomPath)) {
    throw "pom.xml was not found in project directory: $repoRoot"
}

Set-Location -LiteralPath $repoRoot

Write-Host ""
Write-Host "Project directory: $repoRoot" -ForegroundColor Cyan

if ($RedisHost -match "^https?://") {
    throw "RedisHost must be an IP address or hostname without http:// or https://."
}

if ($RedisHost -match "placeholder|your-host|your-ip") {
    throw "RedisHost still contains a placeholder value."
}

if ($LlmBaseUrl -notmatch "^https?://") {
    throw "LlmBaseUrl must start with http:// or https://."
}

$LlmBaseUrl = $LlmBaseUrl.TrimEnd('/')

Write-Host ""
Write-Host "Checking MySQL network connection..." -ForegroundColor Cyan

$mysqlReachable = Test-NetConnection `
    -ComputerName $MysqlHost `
    -Port $MysqlPort `
    -InformationLevel Quiet `
    -WarningAction SilentlyContinue

if (-not $mysqlReachable) {
    throw "Cannot connect to MySQL at ${MysqlHost}:${MysqlPort}."
}

Write-Host "MySQL port is reachable." -ForegroundColor Green

Write-Host ""
Write-Host "Checking Redis network connection..." -ForegroundColor Cyan

$redisReachable = Test-NetConnection `
    -ComputerName $RedisHost `
    -Port $RedisPort `
    -InformationLevel Quiet `
    -WarningAction SilentlyContinue

if (-not $redisReachable) {
    throw "Cannot connect to Redis at ${RedisHost}:${RedisPort}."
}

Write-Host "Redis port is reachable." -ForegroundColor Green

$mysqlPassword = Read-PlainTextSecret "Enter MySQL password"
$redisPassword = Read-PlainTextSecret "Enter Redis password, or press Enter if empty"
$llmApiKey = Read-PlainTextSecret "Enter LLM API key"

$env:MYSQL_URL = "jdbc:mysql://${MysqlHost}:${MysqlPort}/${MysqlDatabase}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:MYSQL_USERNAME = $MysqlUsername
$env:MYSQL_PASSWORD = $mysqlPassword

$env:REDIS_HOST = $RedisHost
$env:REDIS_PORT = $RedisPort.ToString()
$env:REDIS_USERNAME = $RedisUsername
$env:REDIS_PASSWORD = $redisPassword
$env:REDIS_DATABASE = "0"

$env:LLM_ENABLED = "true"
$env:LLM_BASE_URL = $LlmBaseUrl
$env:LLM_API_KEY = $llmApiKey
$env:LLM_MODEL_NAME = $LlmModelName
$env:LLM_TIMEOUT = "60s"
$env:LLM_MAX_RETRIES = "2"
$env:LLM_TEMPERATURE = "0.2"

Write-Host ""
Write-Host "Non-sensitive configuration:" -ForegroundColor Cyan
Write-Host "Project     : $repoRoot"
Write-Host "MySQL URL   : $env:MYSQL_URL"
Write-Host "MySQL user  : $env:MYSQL_USERNAME"
Write-Host "Redis       : $env:REDIS_HOST`:$env:REDIS_PORT"
Write-Host "LLM URL     : $env:LLM_BASE_URL"
Write-Host "LLM model   : $env:LLM_MODEL_NAME"
Write-Host "Passwords and API key are configured but will not be printed."

$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"

if (Test-Path -LiteralPath $mavenWrapper) {
    $mavenCommand = $mavenWrapper
}
elseif (Get-Command "mvn" -ErrorAction SilentlyContinue) {
    $mavenCommand = "mvn"
}
else {
    throw "Neither mvnw.cmd nor a global mvn command was found."
}

Write-Host ""
Write-Host "Running automated tests..." -ForegroundColor Cyan

& $mavenCommand clean test

if ($LASTEXITCODE -ne 0) {
    throw "Automated tests failed. Exit code: $LASTEXITCODE"
}

Write-Host ""
Write-Host "Tests passed." -ForegroundColor Green
Write-Host "Starting ResearchPilot..." -ForegroundColor Green

& $mavenCommand spring-boot:run

if ($LASTEXITCODE -ne 0) {
    throw "ResearchPilot failed to start. Exit code: $LASTEXITCODE"
}

