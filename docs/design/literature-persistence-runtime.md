# Literature runtime persistence (2026-08-05 milestone)

## Scope

V2 adds `literature_agent_step` and `literature_task_paper_result` without
changing the accepted V1 migration. The first table projects the existing
`ExecutionTraceEntry`; its `(trace_id, step_index)` key makes a repeated step
write idempotent. The second table explicitly records the final task-to-paper
relationship, stable result position, and display relevance score.

`LiteratureSearchService` creates one UUID and uses it as the public task ID,
the `AgentRunResult` trace ID, and the persisted step trace ID. No ThreadLocal,
log parsing, or list position is used to infer that relationship.

## Transaction and failure boundary

With `LITERATURE_PERSISTENCE_ENABLED=true`, the service creates the RUNNING
task before planning or external calls. Each completed/terminal trace step is
inserted in its own short transaction. Success finalization writes plans,
eligible formal papers, task-paper results, verification evidence, field
evidence, review status, and terminal task counters in one bounded transaction.
Failure finalization is handled by a separate Spring bean using `REQUIRES_NEW`.
There is no transaction around LLM, OpenAlex, Crossref, review generation, or
the entire Agent loop.

The running AgentState remains authoritative. Database rows are write-only
audit projections and cannot select actions, alter budgets, or admit papers.
Only the already Java-validated `VERIFIED` results with their normalized DOI
are written to `literature_paper`; abstract text is intentionally not written.

## Enablement and limits

Persistence is disabled by default. Enabling it requires both a usable datasource
and a compatible schema (normally `FLYWAY_ENABLED=true` for an empty database).
When enabled, write failures propagate as infrastructure failures; the code does
not silently fall back to the no-op facade. The idempotency scope is restricted
to the same task ID, trace/step, plan attempt, normalized DOI, candidate
fingerprint, and repeated finalization. This does not add HTTP request-level
idempotency because the public API has no accepted `Idempotency-Key` contract.

H2 MySQL-mode migration tests cover the schema and constraints. Real MySQL 8
validation has not been run because no disposable MySQL configuration was
provided. Do not treat the H2 result as MySQL-specific validation. For a
dedicated disposable database, set `MYSQL_URL`, `MYSQL_USERNAME`,
`MYSQL_PASSWORD`, `FLYWAY_ENABLED=true`, and
`LITERATURE_PERSISTENCE_ENABLED=true`, then start the application and make one
search request; never use `flyway clean` against a user database.
