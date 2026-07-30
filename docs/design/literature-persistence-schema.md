# Literature persistence schema (2026-08-04)

## Scope and ownership

This document defines the first immutable Flyway migration for the literature
research persistence boundary. It creates database contracts only. It does not
add MyBatis entities or mappers, repositories, persistence services,
transactions, retries, runtime writes, or agent action/observation/trace
storage. Those runtime concerns remain explicitly reserved for the next
milestone.

The source of truth is the Java domain model: `SearchPlan`, `AgentState`,
`SearchResponse`, `PaperDTO`, `VerificationResult`, `VerificationEvidence`, and
`FieldVerificationEvidence`. This schema does not replace those types, change
their public API semantics, or permit raw provider data to bypass Java
validation.

## Tables and relationships

| Table | Responsibility | Key relationships |
| --- | --- | --- |
| `literature_search_task` | Minimal audited result of one literature research task. | Root table; one-to-many plan attempts and verification evidence. |
| `literature_plan_attempt` | One normalized `SearchPlan` generation or refinement attempt. | Task foreign key; `(search_task_id, attempt_no)` is unique. |
| `literature_paper` | Projection of a formal paper eligible for storage. | Global `normalized_doi` uniqueness; optional `openalex_id` is distinct. |
| `literature_verification_evidence` | Minimal final Crossref verification audit for a candidate in one task. | Required task FK; nullable paper FK for non-formal candidates. |
| `literature_verification_field_evidence` | Ordered field-level evidence list. | Required evidence FK and unique field ordinal. |

All foreign-key columns have indexes. Foreign keys use database-default
`RESTRICT` behavior: no cascade delete can silently erase audit evidence.

## Admission, constraints, and time semantics

Only a Java-validated `VERIFIED` paper with a non-null normalized DOI may be
written to `literature_paper` by a future persistence service. `normalized_doi`
is `NOT NULL` and globally unique, the final database guard against duplicate
formal-paper writes. Java's `DoiNormalizer` remains authoritative for
normalization. `openalex_id` is optional and separately unique when present; it
is not a DOI, and candidate-to-Crossref association remains explicit rather
than list-position based.

Status values are bounded `VARCHAR` rather than MySQL `ENUM`; Java owns enum
mapping and business validation. The schema enforces positive requested counts
and attempt numbers; non-negative counters, budgets, query length, and
versions; candidate/verification-count conservation; `completed_at >=
started_at`; SearchPlan's `1..50` result and `result_limit..100` candidate
limits; and engineering score bounds of `[0,1]` (not probabilities).

All timestamps are `DATETIME(6)` and represent UTC instants. Future Java code
must use `Instant` in UTC. `created_at` is immutable creation time, `updated_at`
is written by its owning persistence operation, and `version` is a non-negative
optimistic-lock counter. No trigger hides an update from the application layer.

## Evidence mapping and data minimization

`literature_verification_evidence` maps the current `VerificationResult`
status, engineering score, source, reference DOI, and rule version. The child
table maps ordered `FieldVerification` / `FieldVerificationEvidence` entries:
field name/status, normalized compared values, optional similarity, and a
bounded reason code. The child table is necessary because field evidence is a
list; opaque provider JSON would not be auditable.

The `*_canonical` columns are reserved for stable, ordered, Java-owned
representations of validated collections. They are not provider JSON or model
drafts. The schema excludes raw user queries (only SHA-256-size hash and length
are reserved), complete prompts, raw model responses/drafts, agent traces,
exception stacks, credentials, headers, API keys, and complete
OpenAlex/Crossref JSON. `abstract_text` is allowed but nullable because current
formal-paper admission does not require an abstract; its runtime retention
policy is not implemented here.

## Flyway operation and test coverage

Spring Boot manages `flyway-core` and `flyway-mysql`. `FLYWAY_ENABLED` defaults
to `false` in `.env.example`; enabling it requires an explicit operator choice
and safe datasource. V1 must never be edited after acceptance: all changes use
a later `V2__...sql` migration. `docs/sql/init-database.sql` remains the
administrator bootstrap guide and is not a Flyway migration.

`LiteraturePersistenceMigrationTest` runs V1 on an isolated H2 in-memory
database in MySQL compatibility mode, without `flyway clean`. It checks schema
creation, Flyway history, idempotent repeat migration without data loss, DOI
uniqueness, task foreign keys, task/attempt uniqueness, and invalid
count/version/score rejection. H2 does not prove every MySQL 8 collation,
storage-engine, precision, or DDL behavior; a disposable dedicated MySQL
database is required before describing MySQL-specific behavior as measured.
Normal tests never create, clear, or migrate a real database.

## Reserved next boundary

The next milestone may introduce controlled runtime persistence only after its
transaction, retry, idempotency, retention, and failure-handling design is
reviewed. This migration does not pre-implement agent-step storage, failed-task
incremental writes, cache/RAG/embedding integration, PDF output, or frontend
changes.
