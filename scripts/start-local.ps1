[CmdletBinding()]
param(
    [ValidateSet("OfflineBuild", "TrustedSearch", "FullDemo", "RagDemo")]
    [string]$Mode = "OfflineBuild",

    [switch]$RunTests,
    [switch]$EnableCache,
    [switch]$RebuildRagIndex,

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
    [string]$RedisUsername = "",

    [string]$OllamaEmbeddingBaseUrl = "http://127.0.0.1:11434",
    [string]$OllamaEmbeddingModel = "qwen3-embedding:0.6b",
    [string]$QdrantBaseUrl = "http://127.0.0.1:6333",
    [string]$QdrantCollectionName = "research_pilot_paper_segments_v1"
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

# A caller may have exported values from another mode. Clear the complete
# feature/configuration surface before applying this mode's explicit values.
$modeEnvironmentNames = @(
    "LLM_ENABLED", "OPENALEX_ENABLED", "CROSSREF_ENABLED", "FLYWAY_ENABLED",
    "LITERATURE_PERSISTENCE_ENABLED", "LITERATURE_CACHE_ENABLED",
    "OLLAMA_EMBEDDING_ENABLED", "OLLAMA_EMBEDDING_BASE_URL", "OLLAMA_EMBEDDING_MODEL",
    "QDRANT_ENABLED", "QDRANT_BASE_URL", "QDRANT_COLLECTION_NAME",
    "QDRANT_BATCH_SIZE", "QDRANT_SCROLL_PAGE_SIZE", "QDRANT_CONNECT_TIMEOUT", "QDRANT_READ_TIMEOUT",
    "OLLAMA_EMBEDDING_CONNECT_TIMEOUT", "OLLAMA_EMBEDDING_READ_TIMEOUT",
    "RAG_INDEXING_ENABLED", "RAG_REBUILD_ON_STARTUP", "RAG_RETRIEVAL_ENABLED",
    "RAG_ANSWER_ENABLED", "RAG_EMBEDDING_VERSION", "RAG_EMBEDDING_DIMENSIONS",
    "RAG_RETRIEVAL_DEFAULT_TOP_K", "RAG_RETRIEVAL_MAX_TOP_K", "RAG_RETRIEVAL_CANDIDATE_MULTIPLIER",
    "RAG_RETRIEVAL_MAX_CANDIDATE_POINTS", "RAG_RETRIEVAL_MAX_QUERY_LENGTH", "RAG_RETRIEVAL_MAX_PAPER_IDS",
    "RAG_RETRIEVAL_MAX_EXCERPT_CHARS", "RAG_RETRIEVAL_EARLIEST_YEAR", "RAG_RETRIEVAL_LATEST_YEAR",
    "RAG_ANSWER_DEFAULT_TOP_K", "RAG_ANSWER_MAX_TOP_K", "RAG_ANSWER_MAX_EVIDENCE",
    "RAG_ANSWER_MAX_SEGMENT_CHARS", "RAG_ANSWER_MAX_CONTEXT_CHARS", "RAG_ANSWER_MAX_PROMPT_CHARS",
    "RAG_ANSWER_MAX_RAW_DRAFT_CHARS", "RAG_ANSWER_MAX_REPAIR_PROMPT_CHARS", "RAG_ANSWER_TOTAL_TIMEOUT",
    "CROSSREF_MAILTO", "CROSSREF_USER_AGENT", "OPENALEX_API_KEY", "CROSSREF_PLUS_TOKEN",
    "LLM_BASE_URL", "LLM_MODEL_NAME", "MYSQL_URL", "MYSQL_USERNAME",
    "REDIS_HOST", "REDIS_PORT", "REDIS_USERNAME", "LLM_API_KEY", "MYSQL_PASSWORD", "REDIS_PASSWORD"
)
foreach ($environmentName in $modeEnvironmentNames) {
    Remove-Item -Path "Env:$environmentName" -ErrorAction SilentlyContinue
}

# Always override inherited feature switches so each mode is reproducible.
$env:LLM_ENABLED = "false"
$env:OPENALEX_ENABLED = "false"
$env:CROSSREF_ENABLED = "false"
$env:FLYWAY_ENABLED = "false"
$env:LITERATURE_PERSISTENCE_ENABLED = "false"
$env:LITERATURE_CACHE_ENABLED = "false"
$env:OLLAMA_EMBEDDING_ENABLED = "false"
$env:QDRANT_ENABLED = "false"
$env:RAG_INDEXING_ENABLED = "false"
$env:RAG_REBUILD_ON_STARTUP = "false"
$env:RAG_RETRIEVAL_ENABLED = "false"
$env:RAG_ANSWER_ENABLED = "false"
$env:OLLAMA_EMBEDDING_BASE_URL = $OllamaEmbeddingBaseUrl.TrimEnd('/')
$env:OLLAMA_EMBEDDING_MODEL = $OllamaEmbeddingModel
$env:QDRANT_BASE_URL = $QdrantBaseUrl.TrimEnd('/')
$env:QDRANT_COLLECTION_NAME = $QdrantCollectionName
$env:RAG_EMBEDDING_VERSION = "qe06b-d1024-t1-c350-o30-n1"
$env:RAG_EMBEDDING_DIMENSIONS = "1024"
# Redis clients remain configured with a harmless local endpoint even when the
# cache is disabled; this does not probe or require Redis.
$env:REDIS_HOST = $RedisHost
$env:REDIS_PORT = $RedisPort.ToString()
$env:REDIS_USERNAME = $RedisUsername
Remove-Item -Path "Env:REDIS_PASSWORD" -ErrorAction SilentlyContinue

Write-Host "Project directory: $repoRoot" -ForegroundColor Cyan
Write-Host "Mode: $Mode" -ForegroundColor Cyan

if ($RebuildRagIndex -and $Mode -ne "RagDemo") {
    throw "-RebuildRagIndex is supported only with -Mode RagDemo."
}

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

if ($LlmBaseUrl -notmatch "^https?://") {
    throw "LlmBaseUrl must start with http:// or https://."
}
if ($EnableCache -and [string]::IsNullOrWhiteSpace($RedisHost)) {
    throw "RedisHost is required when -EnableCache is used."
}

$env:LLM_ENABLED = "true"
$env:LLM_BASE_URL = $LlmBaseUrl.TrimEnd('/')
$env:LLM_MODEL_NAME = $LlmModelName
$env:LITERATURE_CACHE_ENABLED = "false"

$secretNames = @("LLM_API_KEY", "OPENALEX_API_KEY", "CROSSREF_PLUS_TOKEN", "MYSQL_PASSWORD", "REDIS_PASSWORD")
try {
    $env:LLM_API_KEY = Read-PlainTextSecret "Enter LLM API key"
    if ([string]::IsNullOrWhiteSpace($env:LLM_API_KEY)) {
        throw "LLM API key is required for $Mode."
    }

    if ($Mode -eq "TrustedSearch" -or $Mode -eq "FullDemo") {
        $CrossrefMailto = Require-Text $CrossrefMailto "CrossrefMailto"
        $CrossrefUserAgent = Require-Text $CrossrefUserAgent "CrossrefUserAgent"
        if ($CrossrefMailto -notmatch "^[^@\s]+@[^@\s]+\.[^@\s]+$") {
            throw "CrossrefMailto must be a valid contact email address."
        }
        if ($CrossrefMailto -match "your-email@example\.com") {
            throw "CrossrefMailto still contains the documentation placeholder."
        }
        $env:OPENALEX_ENABLED = "true"
        $env:CROSSREF_ENABLED = "true"
        $env:CROSSREF_MAILTO = $CrossrefMailto
        $env:CROSSREF_USER_AGENT = $CrossrefUserAgent
        $env:OPENALEX_API_KEY = Read-PlainTextSecret "Enter optional OpenAlex API key, or press Enter to omit"
        $env:CROSSREF_PLUS_TOKEN = Read-PlainTextSecret "Enter optional Crossref Plus token, or press Enter to omit"
    }

    if ($EnableCache) {
        Test-RequiredTcpService $RedisHost $RedisPort "Redis"
        $env:LITERATURE_CACHE_ENABLED = "true"
        $env:REDIS_HOST = $RedisHost
        $env:REDIS_PORT = $RedisPort.ToString()
        $env:REDIS_USERNAME = $RedisUsername
        $env:REDIS_PASSWORD = Read-PlainTextSecret "Enter Redis password, or press Enter if empty"
    }

    if ($Mode -eq "FullDemo" -or $Mode -eq "RagDemo") {
        Test-RequiredTcpService $MysqlHost $MysqlPort "MySQL"
        $env:MYSQL_URL = "jdbc:mysql://${MysqlHost}:${MysqlPort}/${MysqlDatabase}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
        $env:MYSQL_USERNAME = $MysqlUsername
        $env:MYSQL_PASSWORD = Read-PlainTextSecret "Enter MySQL password"
        $env:FLYWAY_ENABLED = "true"
        $env:LITERATURE_PERSISTENCE_ENABLED = "true"
    }

    if ($Mode -eq "RagDemo") {
        $env:OLLAMA_EMBEDDING_ENABLED = "true"
        $env:QDRANT_ENABLED = "true"
        $env:RAG_INDEXING_ENABLED = "true"
        $env:RAG_RETRIEVAL_ENABLED = "true"
        $env:RAG_ANSWER_ENABLED = "true"
        $env:RAG_REBUILD_ON_STARTUP = $RebuildRagIndex.IsPresent.ToString().ToLowerInvariant()
    }

    Write-Host "Non-sensitive configuration:" -ForegroundColor Cyan
    Write-Host "  LLM model       : $env:LLM_MODEL_NAME"
    Write-Host "  OpenAlex        : $env:OPENALEX_ENABLED"
    Write-Host "  Crossref        : $env:CROSSREF_ENABLED"
    Write-Host "  Persistence     : $env:LITERATURE_PERSISTENCE_ENABLED"
    Write-Host "  Flyway          : $env:FLYWAY_ENABLED"
    Write-Host "  Redis cache     : $env:LITERATURE_CACHE_ENABLED"
    Write-Host "  Ollama embedding: $env:OLLAMA_EMBEDDING_ENABLED"
    Write-Host "  Qdrant          : $env:QDRANT_ENABLED"
    Write-Host "  RAG indexing    : $env:RAG_INDEXING_ENABLED"
    Write-Host "  RAG rebuild     : $env:RAG_REBUILD_ON_STARTUP"
    Write-Host "  RAG retrieval   : $env:RAG_RETRIEVAL_ENABLED"
    Write-Host "  RAG answer      : $env:RAG_ANSWER_ENABLED"
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
