# ResearchPilot

ResearchPilot is a synchronous, controlled literature-retrieval Agent. It turns a topic request into a trusted search plan, retrieves candidates from OpenAlex, verifies them with Crossref, and returns only formally eligible papers: `VERIFIED` papers with normalized DOIs. When enough verified evidence with usable abstracts exists, it can also produce a citation-checked abstract-level review.

The public API is deliberately conservative. A successful HTTP request can legitimately return `NO_VERIFIED_RESULTS`; it never fills a result with partially verified papers or invents an evidence review.

## What is available now

- Trusted search-plan generation with JSON, schema, business, and security validation.
- A bounded `LiteratureResearchAgent`: at most two OpenAlex rounds and one controlled plan refinement.
- Candidate deduplication and Crossref DOI/bibliographic verification; formal output is gated by `VERIFIED` plus a normalized DOI.
- Up to `min(requested target, 10)` new Crossref lookups per round, so small requests do not spend the larger capacity while the default 20-paper target fits two rounds; larger requested limits may still return an honest partial result.
- Citation-validated, abstract-level review with one bounded repair and a safe no-review fallback.
- Optional Flyway V1/V2 and MyBatis persistence. Once enabled, persistence failures fail closed rather than reporting a false success.
- Optional Redis cache-aside decorators for OpenAlex and Crossref. Redis failures fail open to the providers; cache hits still go through verification.
- Stable errors, request correlation (`X-Request-Id`), low-cardinality Micrometer observations, Swagger UI, and deterministic offline regression tests.

It is **not** a PDF/full-text RAG system, a vector database, a multi-Agent workflow, an asynchronous queue, or a frontend application.

## Prerequisites

- Java 21.
- The checked-in Maven Wrapper (`.\mvnw.cmd`); a first build needs access to Maven Central unless dependencies are already cached.
- Only for a real trusted search: an OpenAI-compatible chat-model endpoint, OpenAlex access, and Crossref contact details.
- Only for the full demo: an authorized, disposable or otherwise approved MySQL 8 schema. Redis is required only when the cache is enabled.

`.env.example` is a variable reference, not an automatically loaded configuration file. Do not copy real secrets into it or commit a local `.env`. The startup script prompts for secrets and keeps them in its process environment only.

## Reproducible modes

| Mode | External services contacted | Purpose |
| --- | --- | --- |
| `OfflineBuild` | None | Runs `clean verify`; no LLM, OpenAlex, Crossref, MySQL, or Redis access. |
| `TrustedSearch` | LLM, OpenAlex, Crossref | Starts the live trusted-search path. Persistence is disabled; Redis is optional. |
| `FullDemo` | LLM, OpenAlex, Crossref, MySQL; Redis only with `-EnableCache` | Starts the live path with Flyway migration and durable task evidence. |

### 1. Deterministic offline build

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 -Mode OfflineBuild
```

This mode exits after the Maven build. It is the safe default for a new checkout and does not start the application.

### 2. Live trusted search

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 `
  -Mode TrustedSearch `
  -LlmBaseUrl "https://your-llm-provider.example/v1" `
  -LlmModelName "your-model" `
  -CrossrefMailto "your-email@example.com" `
  -CrossrefUserAgent "ResearchPilot/0.1 (contact: your-email@example.com)"
```

The script securely prompts for the LLM API key. An OpenAlex API key and Crossref Plus token are optional and are also prompted without echoing. Use `-EnableCache -RedisHost localhost` only when Redis is intentionally available.

### 3. Full persistence demo

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 `
  -Mode FullDemo `
  -MysqlHost localhost -MysqlDatabase research_pilot -MysqlUsername research_pilot `
  -LlmBaseUrl "https://your-llm-provider.example/v1" `
  -LlmModelName "your-model" `
  -CrossrefMailto "your-email@example.com" `
  -CrossrefUserAgent "ResearchPilot/0.1 (contact: your-email@example.com)"
```

`FullDemo` sets `FLYWAY_ENABLED=true` and `LITERATURE_PERSISTENCE_ENABLED=true`. Supply only a schema you are authorized to migrate. The script never runs `flyway clean`, drops data, creates database users, or performs destructive database operations. Add `-EnableCache -RedisHost localhost` only if Redis is also part of the authorized demo.

The script sets all feature switches explicitly for its selected mode:

`LLM_ENABLED`, `OPENALEX_ENABLED`, `CROSSREF_ENABLED`, `FLYWAY_ENABLED`, `LITERATURE_PERSISTENCE_ENABLED`, and `LITERATURE_CACHE_ENABLED`.

## Run and demonstrate the API

After a live mode starts, open:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>
- Dependency status: <http://localhost:8080/api/system/status>

Use [http/research-pilot.http](http/research-pilot.http) with an IDE HTTP client, or send:

```http
POST http://localhost:8080/api/literature/search
Content-Type: application/json

{
  "query": "近五年遥感变化检测研究",
  "fromYear": 2021,
  "toYear": 2026,
  "limit": 5
}
```

`X-Request-Id` is returned on every synchronous HTTP response. The search response includes `taskId`, `status`, `candidateCount`, `deduplicatedCount`, `verificationSummary`, `papers`, `review`, `terminationReason`, and `elapsedMs`.

- HTTP 200 with `NO_VERIFIED_RESULTS` is a valid business result.
- `PARTIAL_SUCCESS` means some formal results survived the gate; it is not fabricated completeness.
- `papers` contains only `VERIFIED` papers with normalized DOIs.
- HTTP 503 `LITERATURE_PERSISTENCE_FAILED` means enabled durable persistence could not complete; the service fails closed.
- A review citation proves mapping to the current evidence set, not a full-text claim.

`/api/system/status` intentionally probes currently configured MySQL and Redis clients. In an offline or cache-disabled start, a reported dependency state is diagnostic only; do not treat it as proof that persistence or cache is enabled.

## Architecture and demo evidence

The executable boundaries, four diagrams, three demo paths, and evidence labels are in [docs/demo/trusted-agent-demo.md](docs/demo/trusted-agent-demo.md). It distinguishes:

- **REAL NETWORK** — a variable external observation, never an SLA or benchmark.
- **OFFLINE FIXTURE** — a fixed provider-response snapshot.
- **DETERMINISTIC TEST** — fixed clocks, fakes, Mockito, or H2 tests.

Run the focused deterministic demonstration suite with:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-trusted-demo.ps1
```

For the complete regression and packaged JAR:

```powershell
.\mvnw.cmd clean verify
```

## Operational boundaries and RAG gate

MySQL is the durable source of truth for enabled task evidence. Redis is a short-lived cache of mapped OpenAlex/Crossref port results; it is not a vector store and cannot admit papers directly.

Qdrant, embeddings, PDF ingestion, and RAG business code are not part of the current application. The planned 2026-08-10 to 2026-08-15 RAG/Qdrant work remains gated on the trustworthy-search demo: only `VERIFIED` papers may be projected, Qdrant would be a rebuildable derived index, and any future RAG failure must not affect this trusted-search API.

## Troubleshooting and security

- `MODEL_NOT_CONFIGURED`: start `TrustedSearch` or `FullDemo` with valid LLM configuration; `OfflineBuild` deliberately disables the model.
- `LITERATURE_PERSISTENCE_FAILED`: confirm the authorized MySQL schema is reachable and migrated. Do not disable persistence merely to conceal a failed durable write.
- Maven `Permission denied: getsockopt`: Maven was prevented from reaching its dependency repository; grant repository access and rerun before judging code quality.
- Cache disabled: Redis is not a prerequisite. Cache enabled: confirm only the configured Redis host/port is reachable; a later cache operation still fails open by design.

Never commit API keys, passwords, tokens, authorization headers, private hosts, real email addresses, raw provider JSON, prompts, model drafts, cache keys, or full abstracts. Historical milestones and dated validation evidence are retained in [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md); design details remain under [docs](docs).
