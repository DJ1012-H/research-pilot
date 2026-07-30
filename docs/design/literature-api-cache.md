# External literature API cache

## Scope and placement

This is an optional Redis cache-aside layer around `OpenAlexSearchPort` and
`CrossrefSearchPort`. The cached decorators are the primary port beans and
delegate to the existing HTTP adapters on a miss. `LiteratureSearchService`,
the controlled Agent, review flow, MySQL persistence, and public DTOs have no
Redis dependency.

Only already-mapped internal results are serialized in a versioned envelope:
`OpenAlexSearchResult`, `CrossrefLookupResult`, and
`CrossrefBibliographicLookupResult`. Each read verifies JSON, envelope schema
version, provider, operation, result kind, payload size, and the record
constructors' invariants. A corrupt entry is a cache miss; the implementation
deletes only that exact key and calls the real adapter. It never caches provider
DTOs, headers, raw HTTP JSON, exceptions, `VerificationResult`,
`SearchResponse`, Agent state, review input, review drafts, prompts, or model
output.

## Keys

The default prefix is `research-pilot:literature:v1`. Keys have one of these
forms:

- `research-pilot:literature:v1:openalex:search:{sha256}`
- `research-pilot:literature:v1:crossref:doi:{sha256}`
- `research-pilot:literature:v1:crossref:bibliographic:{sha256}`

OpenAlex digests cover normalized search text, date range, work types,
languages, sort, and effective page size. DOI keys first apply
`DoiNormalizer`. Bibliographic digests use normalized title, first author,
year, and source. Keys never contain the raw values, credentials, Crossref
mailto, authorization data, provider base URLs, or API tokens. Change the
prefix/version when key canonicalization, envelope schema, or result structure
becomes incompatible.

## Configuration

All properties are validated in Java. TTLs, failure cooldown, and maximum
payload bytes must be positive. The conservative defaults are:

```text
LITERATURE_CACHE_ENABLED=false
LITERATURE_CACHE_KEY_PREFIX=research-pilot:literature:v1
OPENALEX_CACHE_TTL=15m
CROSSREF_CACHE_TTL=24h
CROSSREF_NOT_FOUND_CACHE_TTL=5m
LITERATURE_CACHE_FAILURE_COOLDOWN=30s
LITERATURE_CACHE_MAX_PAYLOAD_BYTES=2097152
```

Existing `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD`, and
`REDIS_DATABASE` retain their existing connection semantics. Do not commit real
hosts, passwords, or private endpoints.

## Cacheability and fallback

| Result | Stored? | TTL |
| --- | --- | --- |
| OpenAlex mapped success, including empty candidates | Yes | `OPENALEX_CACHE_TTL` |
| Crossref DOI `FOUND` | Yes | `CROSSREF_CACHE_TTL` |
| Crossref bibliographic `FOUND_SINGLE`/`FOUND_MULTIPLE` | Yes | `CROSSREF_CACHE_TTL` |
| Adapter-mapped Crossref `NOT_FOUND` | Yes | `CROSSREF_NOT_FOUND_CACHE_TTL` |
| 401/403/429/5xx, timeout, transport, empty/invalid response, disabled or missing configuration | No | n/a |

Redis unavailability does not change the external API result or its exception
semantics. The first cache failure opens a short, Clock-driven cooldown; calls
during it bypass Redis and invoke the adapter. A failed cache write still
returns the successful adapter result. The cache has no effect on the two-round
Agent limit, refinement limit, Crossref-call budget, candidate budget, deadline,
or `VERIFIED` admission rule. Cached Crossref metadata still goes through
`PaperVerificationService`, `VerificationPolicy`, and `EligiblePaperFilter`.

## Testing and optional smoke test

Ordinary tests use in-memory fakes and Mockito and never contact Redis,
OpenAlex, Crossref, or an LLM. The focused test command is:

```powershell
.\mvnw.cmd "-Dtest=*Cache*,*Redis*,ArchitectureConstraintsTest,PaperVerificationServiceTest,AgentExecutionLoopTest,LiteraturePersistenceFacadeIntegrationTest" test
```

On 2026-07-30 the focused command ran 50 tests with 0 failures/errors and 2
expected skips for the disabled real-Redis smoke. `clean verify` ran 454 tests
with 0 failures/errors and 4 expected opt-in network-test skips and built the
executable JAR.

`RedisCacheSmokeTest` is explicitly opt-in:

```powershell
$env:RUN_REDIS_CACHE_SMOKE="true"
$env:REDIS_HOST="<authorized-test-host>"
$env:REDIS_PORT="6379"
$env:REDIS_USERNAME="<authorized-test-user>"
$env:REDIS_PASSWORD="<prompt-or-secret-store-value>"
$env:REDIS_DATABASE="0"
.\mvnw.cmd "-Dtest=RedisCacheSmokeTest" test
```

The authorized 2026-07-30 run passed 2 tests with 0 failures/errors/skips. The
test generated a random dedicated prefix at runtime, observed a miss followed
by a hit without a second adapter call, verified a positive TTL no greater than
the configured OpenAlex TTL, and deleted only its exact key in `finally`. The
second test pointed Redis at an unreachable local port and confirmed direct
adapter fallback. No key, query, DOI, private host, or credential was logged.
Neither `FLUSHDB`, `FLUSHALL`, nor namespace-wide deletion is used.
