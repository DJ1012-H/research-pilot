# ResearchPilot

ResearchPilot is a synchronous, controlled literature-retrieval Agent. It turns a topic request into a trusted search plan, retrieves candidates from OpenAlex, verifies them with Crossref, and returns only formally eligible papers: `VERIFIED` papers with normalized DOIs. When enough verified evidence with usable abstracts exists, it can also produce a citation-checked abstract-level review.

The public API is deliberately conservative. A successful HTTP request can legitimately return `NO_VERIFIED_RESULTS`; it never fills a result with partially verified papers or invents an evidence review.

## What is available now

- Trusted search-plan generation with JSON, schema, business, and security validation.
- A bounded `LiteratureResearchAgent`: at most two OpenAlex rounds and one controlled plan refinement.
- Candidate deduplication and Crossref DOI/bibliographic verification; formal output is gated by `VERIFIED` plus a normalized DOI.
- A public result target of 1–15 papers, defaulting to 5, with up to `min(requested target, 10)` new Crossref lookups per round. Small requests do not spend the larger capacity, while a 15-paper request can use the second controlled round and may still return an honest partial result.
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

## Offline Crossref evaluation

The `crossref-verification-v1` assets preserve 14 reviewed offline cases, their
source hashes, mutation lineage, the frozen 10/4 calibration split, and the
historical field-level calibration report. They do not call live provider APIs.

Run the policy and formal-admission benchmark on Windows with:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-crossref-policy-evaluation.ps1
```

The current versioned production baseline intentionally returns a non-zero exit
code because `acceptance_passed=false`. The committed v0.1 report remains the
historical baseline, while the runner emits the current v0.2 result. A successful
JUnit build means the recorded PASS/FAIL evidence is reproducible, not that the
policy benchmark passed. New cases enter `crossref-verification-v2` as
`NEEDS_REVIEW` with null expected labels until explicitly approved by a human.

## Architecture and demo evidence

The executable boundaries, four diagrams, three demo paths, and evidence labels are in [docs/demo/trusted-agent-demo.md](docs/demo/trusted-agent-demo.md). It distinguishes:

- **REAL NETWORK** — a variable external observation, never an SLA or benchmark.
- **OFFLINE FIXTURE** — a fixed provider-response snapshot.
- **DETERMINISTIC TEST** — fixed clocks, fakes, Mockito, or H2 tests.

Run the focused deterministic demonstration suite with:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-trusted-demo.ps1
```

Replay the three release-acceptance paths with fixed offline fixtures and
redacted summaries only:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\replay-trusted-demo.ps1
```

For the complete regression and packaged JAR:

```powershell
.\mvnw.cmd clean verify
```

## Operational boundaries and RAG baseline

MySQL is the durable source of truth for enabled task evidence. Redis is a short-lived cache of mapped OpenAlex/Crossref port results; it is not a vector store and cannot admit papers directly.

The trustworthy-search baseline is frozen at `v1.0.0-demo`. The 2026-08-10 infrastructure baseline adds a pinned, loopback-only Qdrant Compose service, a controlled Ollama/Qdrant environment check, and a frozen index contract. It does not add RAG business code or change the trusted-search API.

Only `VERIFIED` papers with normalized DOIs may be projected. MySQL remains authoritative; Qdrant is a rebuildable derived index, and every future retrieval match must be re-admitted through MySQL. The initial `qwen3-embedding:0.6b` baseline measured 1024 dimensions for both Chinese and English inputs on 2026-08-06; startup must still recheck the live dimension before a Collection is created.

After WSL 2, Docker Desktop, Windows-native Ollama, and `qwen3-embedding:0.6b` are installed:

```powershell
docker compose -f .\infra\docker-compose.rag.yml up -d
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-rag-environment.ps1 -RestartQdrant
```

The script validates Docker Compose, measures and reports the real embedding dimension and latency without logging vectors or input text, checks Qdrant HTTP readiness and collection listing, and optionally verifies stop/start recovery without deleting the named volume. See [ADR-001](docs/decisions/ADR-001-vector-store-qdrant.md) and the [trusted RAG index contract](docs/design/trusted-rag-index-contract.md).

The opt-in Java embedding adapter is disabled by default. When explicitly enabled, it calls the loopback Ollama `/api/embed` endpoint with the configured model and fails closed unless the response contains exactly one finite, non-empty, 1024-dimensional vector per controlled input for the initial embedding version. Default unit tests use a fake embedding port or a mocked HTTP server and do not require Ollama.

## Troubleshooting and security

- `MODEL_NOT_CONFIGURED`: start `TrustedSearch` or `FullDemo` with valid LLM configuration; `OfflineBuild` deliberately disables the model.
- `LITERATURE_PERSISTENCE_FAILED`: confirm the authorized MySQL schema is reachable and migrated. Do not disable persistence merely to conceal a failed durable write.
- Maven `Permission denied: getsockopt`: Maven was prevented from reaching its dependency repository; grant repository access and rerun before judging code quality.
- Cache disabled: Redis is not a prerequisite. Cache enabled: confirm only the configured Redis host/port is reachable; a later cache operation still fails open by design.

Never commit API keys, passwords, tokens, authorization headers, private hosts, real email addresses, raw provider JSON, prompts, model drafts, cache keys, or full abstracts. Historical milestones and dated validation evidence are retained in [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md); design details remain under [docs](docs).
