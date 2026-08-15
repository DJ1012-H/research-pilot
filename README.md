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
- Disabled-by-default trusted RAG answer at `POST /api/research/ask`; it reads only the active MySQL/Qdrant index, admits only current `VERIFIED` ABSTRACT evidence, and publishes Java-assembled citations.
- Reproducible `RagDemo` startup, fixed offline RAG replay, read-only real-service verifier, frozen retrieval runner, and staged Collection recovery rehearsal for the Day 6 candidate closeout.
- Stable errors, request correlation (`X-Request-Id`), low-cardinality Micrometer observations, Swagger UI, and deterministic offline regression tests.

It is **not** a PDF/full-text RAG system, a multi-Agent workflow, an asynchronous queue, or a frontend application. Day 5 answers are bounded to trusted abstract Segments.

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
| `RagDemo` | LLM, MySQL, Flyway, Ollama embedding, Qdrant; no OpenAlex/Crossref; Redis only with `-EnableCache` | Starts trusted RAG over existing MySQL papers; rebuild is opt-in. |

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

### 4. RAG demo

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 `
  -Mode RagDemo -MysqlHost localhost -MysqlDatabase research_pilot `
  -MysqlUsername research_pilot -LlmBaseUrl "https://your-llm-provider.example/v1" `
  -LlmModelName "your-model"
```

`RagDemo` enables MySQL/Flyway, Ollama, Qdrant, retrieval, and answer while
disabling OpenAlex, Crossref, Redis cache, and startup rebuild. Add
`-RebuildRagIndex` only for a controlled rebuild. See [the Day 6 RAG demo
guide](docs/demo/trusted-rag-demo.md) and [candidate acceptance](docs/demo/v1.1.0-rag-demo-acceptance.md).

The script sets all feature switches and RAG integration switches explicitly
for its selected mode:

`LLM_ENABLED`, `OPENALEX_ENABLED`, `CROSSREF_ENABLED`, `FLYWAY_ENABLED`,
`LITERATURE_PERSISTENCE_ENABLED`, `LITERATURE_CACHE_ENABLED`,
`OLLAMA_EMBEDDING_ENABLED`, `QDRANT_ENABLED`, `RAG_INDEXING_ENABLED`,
`RAG_REBUILD_ON_STARTUP`, `RAG_RETRIEVAL_ENABLED`, and `RAG_ANSWER_ENABLED`.

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

Replay the three release-acceptance paths with fixed offline fixtures and
redacted summaries only:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\replay-trusted-demo.ps1
```

Replay the fixed Day 6 RAG orchestration paths:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\replay-rag-demo.ps1
```

For the complete regression and packaged JAR:

```powershell
.\mvnw.cmd clean verify
```

## Operational boundaries and RAG baseline

MySQL is the durable source of truth for enabled task evidence. Redis is a short-lived cache of mapped OpenAlex/Crossref port results; it is not a vector store and cannot admit papers directly.

The trustworthy-search baseline is frozen at `v1.0.0-demo`. The RAG extension keeps the trusted-search API unchanged and disabled-by-default. Day 3 adds an opt-in MySQL-to-Qdrant indexing path: Flyway V3 records the current paper trust state and active embedding version, while Qdrant remains a derived index that can be rebuilt or discarded without changing paper eligibility.

Only `VERIFIED` papers with normalized DOIs may be projected. MySQL remains authoritative; Qdrant is a rebuildable derived index, and every future retrieval match must be re-admitted through MySQL. The initial `qwen3-embedding:0.6b` baseline measured 1024 dimensions for both Chinese and English inputs on 2026-08-06; startup must still recheck the live dimension before a Collection is created.

After WSL 2, Docker Desktop, Windows-native Ollama, and `qwen3-embedding:0.6b` are installed:

```powershell
docker compose -f .\infra\docker-compose.rag.yml up -d
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-rag-environment.ps1 -RestartQdrant
```

The script validates Docker Compose, measures and reports the real embedding dimension and latency without logging vectors or input text, checks Qdrant HTTP readiness and collection listing, and optionally verifies stop/start recovery without deleting the named volume. See [ADR-001](docs/decisions/ADR-001-vector-store-qdrant.md), the [trusted RAG index contract](docs/design/trusted-rag-index-contract.md), and the [trusted RAG retrieval contract](docs/design/trusted-rag-retrieval-contract.md).

The opt-in Java embedding adapter is disabled by default. When explicitly enabled, it calls the loopback Ollama `/api/embed` endpoint with the configured model and fails closed unless the response contains exactly one finite, non-empty, 1024-dimensional vector per controlled input for the initial embedding version. Default unit tests use a fake embedding port or mocked HTTP and do not require Ollama or Qdrant.

The Day 3 rebuild path is also disabled by default. It requires all of the following switches to be enabled deliberately: `FLYWAY_ENABLED`, `LITERATURE_PERSISTENCE_ENABLED`, `OLLAMA_EMBEDDING_ENABLED`, `QDRANT_ENABLED`, and `RAG_INDEXING_ENABLED`. Set `RAG_REBUILD_ON_STARTUP=true` only for a controlled rebuild run. The rebuild creates or validates the frozen Collection and its required Payload Indexes, skips Embedding when Point IDs and exact content hashes are unchanged, updates metadata-only payload changes without re-embedding, removes points for papers that are no longer currently `VERIFIED`, verifies the final point count, and runs one Point-ID self-query with forced `VERIFIED`, paper-ID, and embedding-version filters before activating the version in MySQL. An empty index cannot pass this activation gate.

Day 4 adds a diagnostics-only trusted-vector retrieval path at `POST /api/research/retrieve`. It is controlled by `RAG_RETRIEVAL_ENABLED=false` and returns a stable `RAG_RETRIEVAL_DISABLED` result until explicitly enabled. The server selects the MySQL `active=1`/`SUCCEEDED` index version, embeds the normalized query with that version's configured profile, sends a bounded Top-K request to Qdrant with forced `embeddingVersion` and `VERIFIED` filters, then batch-reads every candidate from MySQL. Only current `VERIFIED` papers with a normalized, matching DOI, verification version, source timestamp, and deterministic current `contentHash` are returned. Qdrant payload business fields and scores never raise trust status; result fields such as title, DOI, year, and venue come from MySQL. Invalid, unavailable, stale, malformed, or dimension-mismatched dependencies fail closed without changing `POST /api/literature/search`.

The endpoint accepts a bounded query, optional `topK` (default 5, maximum 20), publication-year range, `paperIds`, and `segmentTypes`. It returns candidate/admission counts, elapsed time, bounded excerpts, and a stable failure code; it never returns vectors or logs the complete query. Ordinary tests use fakes, Mockito, and H2. The local Day 4 evaluation assets belong to the separate `eval/rag-retrieval-v1/` evaluation branch and remain `NEEDS_REVIEW` until a human supplies relevance judgments, so Recall@K and MRR are not measured by the implementation tests.

Day 5 adds `POST /api/research/ask` with a separate read-only answer boundary. The server forces `ABSTRACT`, the active embedding version, and `VERIFIED` retrieval filters, then uses only the current MySQL-reconstructed Segment text. At least one MySQL-re-admitted ABSTRACT is required before the existing OpenAI-compatible `ModelInvoker` is called. A single `rag_evidence_relevance` operation must first return a Java-validated subset of current-request evidence IDs; invalid output fails closed, and an empty subset returns `INSUFFICIENT_EVIDENCE` without calling `rag_answer`. Admission prompt v2 requests provider JSON mode with temperature zero and a bounded output, but Java remains the trust boundary for closed JSON Schema, DTO, state, and current-request evidence validation. Safe diagnostics include a low-cardinality admission failure subcode and never include the raw model output. The answer model may return only statement text and admitted citation IDs. Java performs syntax, schema, business, and citation validation, allows at most one controlled answer repair, and assembles public citation metadata from trusted evidence. Diagnostics separate Judge calls, answer calls, admitted evidence, generation evidence, and repairs. There is deliberately no scalar `minScore` threshold: reported vector scores are relatedness observations, not trust probabilities. `CitationGuard` proves citation format, membership, and request ownership only, not semantic entailment or full-text factual correctness. Ordinary answer tests remain offline; real MySQL/Ollama/Qdrant/DeepSeek checks require explicit operator authorization.

The interview-readiness Day 3 Runner evaluates a separately frozen DOI-labelled tuning/holdout corpus through both `/api/research/retrieve` and `/api/research/ask`, requires explicit real-model cost confirmation, hashes answer text instead of retaining it, freezes parameters before the holdout, and refuses to overwrite the single fixed-holdout directory. The first `rag-retrieval-v3-lite` holdout is preserved as `FAIL`: retrieval Recall@5/Hit@5/MRR and semantic-negative refusal were all 1.0, but two of six positive answers stopped fail-closed on invalid relevance-Judge output. See `docs/demo/rag-v3-lite-day3-evaluation.md`; do not describe this result as a passed RAG acceptance benchmark.

`GET /api/system/status` reports Ollama Embedding and Qdrant separately from application liveness. When the Ollama adapter is enabled, this status call performs one controlled live embedding probe; a failed RAG dependency reports `DOWN` without changing the application field from `UP` or disabling `POST /api/literature/search`.

The controlled 2026-08-11 acceptance used an authorized MySQL 8 schema,
Windows-native Ollama 0.32.7 with `qwen3-embedding:0.6b`, and
`qdrant/qdrant:v1.18.2`. Flyway V3 activated 13 current `VERIFIED` papers as 13
points. A second rebuild reused all 13 embeddings and preserved the complete
point identity/content snapshot. Live mutation checks proved payload-only
metadata updates, one-paper re-embedding after a content change, deletion after
a verification downgrade, and restoration after re-admission. Qdrant retained
all 13 points across a container stop/start using the named volume. During a
controlled Ollama outage, `/api/system/status` reported Ollama Embedding as
`DOWN` while application, MySQL, Qdrant, and Actuator liveness remained `UP`.
These are dated local acceptance observations, not an SLA or a reason to enable
RAG by default.

Real-service acceptance is intentionally not part of ordinary `clean verify`.
It changes an authorized test schema and derived index, and may temporarily stop
local services. Keep it behind an explicit operator action, preserve a recovery
baseline, and restore changed paper values and trust state before completion.

Day 6 adds a read-only verifier:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-rag-demo.ps1
```

It fails closed when readiness, dimensions, year-filter behavior, answer
fields, citation ownership, or zero model calls cannot be confirmed. Redis may
be `DOWN` or `DISABLED`; Qdrant payload text is never printed.

## Troubleshooting and security

- `MODEL_NOT_CONFIGURED`: start `TrustedSearch` or `FullDemo` with valid LLM configuration; `OfflineBuild` deliberately disables the model.
- `LITERATURE_PERSISTENCE_FAILED`: confirm the authorized MySQL schema is reachable and migrated. Do not disable persistence merely to conceal a failed durable write.
- Maven `Permission denied: getsockopt`: Maven was prevented from reaching its dependency repository; grant repository access and rerun before judging code quality.
- Cache disabled: Redis is not a prerequisite. Cache enabled: confirm only the configured Redis host/port is reachable; a later cache operation still fails open by design.

Never commit API keys, passwords, tokens, authorization headers, private hosts, real email addresses, raw provider JSON, prompts, model drafts, cache keys, or full abstracts. Historical milestones and dated validation evidence are retained in [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md); design details remain under [docs](docs).
