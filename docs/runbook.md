# Runbook

This runbook separates safe offline verification from opt-in external-service
execution. Start with the offline path on a new checkout.

## Prerequisites

- Java 21
- the checked-in Maven Wrapper
- PowerShell 7 or Windows PowerShell for repository scripts
- optional live services only for the corresponding mode

`.env.example` is a variable reference and is not loaded automatically. Never
commit a populated `.env`, API key, password, private host, or personal contact
address.

## Offline verification

Windows:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

Linux or macOS:

```bash
./mvnw -B -ntp clean verify
```

The default configuration disables live LLM, provider, database, cache,
embedding, Qdrant, and RAG paths. Opt-in smoke tests are skipped.

The equivalent scripted Windows entry point is:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\scripts\start-local.ps1 -Mode OfflineBuild
```

## Live modes

| Mode | Dependencies | Purpose |
| --- | --- | --- |
| `TrustedSearch` | LLM, OpenAlex, Crossref | Live search and verification without persistence |
| `FullDemo` | TrustedSearch plus MySQL; optional Redis | Durable trusted-search evidence |
| `RagDemo` | LLM, MySQL, Ollama, Qdrant; optional Redis | Read-only trusted RAG over existing papers |

The startup script prompts for secrets without echoing or writing them. Example
for trusted search:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\scripts\start-local.ps1 -Mode TrustedSearch `
  -LlmBaseUrl "https://your-provider.example/v1" `
  -LlmModelName "your-model" `
  -CrossrefMailto "your-email@example.com" `
  -CrossrefUserAgent "ResearchPilot/0.1"
```

Example for RAG over an authorized MySQL schema:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\scripts\start-local.ps1 -Mode RagDemo `
  -MysqlHost localhost -MysqlDatabase research_pilot `
  -MysqlUsername research_pilot `
  -LlmBaseUrl "https://your-provider.example/v1" `
  -LlmModelName "your-model"
```

`RagDemo` does not rebuild the index unless `-RebuildRagIndex` is explicitly
supplied. Only use a database you are authorized to migrate.

## Local endpoints

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Actuator liveness: <http://localhost:8080/actuator/health>
- Dependency status: <http://localhost:8080/api/system/status>
- Trusted search: `POST /api/literature/search`
- Trusted retrieval diagnostics: `POST /api/research/retrieve`
- Trusted cited answer: `POST /api/research/ask`

## Verification scripts

| Command | Boundary |
| --- | --- |
| `scripts/verify-trusted-demo.ps1` | Focused deterministic trusted-search tests |
| `scripts/replay-trusted-demo.ps1` | Fixed offline trusted-search scenarios |
| `scripts/replay-rag-demo.ps1` | Fixed offline RAG orchestration scenarios |
| `scripts/verify-rag-environment.ps1` | Docker, Ollama, and Qdrant readiness |
| `scripts/verify-rag-demo.ps1` | Read-only live RAG checks |
| `scripts/verify-rag-rebuild-recovery.ps1` | Explicit staged Collection recovery rehearsal |

Real provider/model calls, database migrations, index rebuilds, Collection
deletion, and evaluation runs require explicit operator authorization. Ordinary
verification never silently upgrades an offline result into live acceptance.
