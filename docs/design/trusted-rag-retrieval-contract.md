# Trusted RAG retrieval contract

Status: Day 4 diagnostics-only contract. Retrieval is disabled unless
`RAG_RETRIEVAL_ENABLED=true` is explicitly supplied together with authorized
MySQL persistence, Ollama embedding, and Qdrant services.

## Authority and read-only flow

MySQL is authoritative for the current paper identity, normalized DOI,
verification status, verification-rule version, publication fields, and source
timestamp. The MySQL active index row is authoritative for collection name,
embedding version, vector dimensions, and activation status. Qdrant is a
derived candidate index. A Qdrant payload or score can never increase a
paper's trust status.

The request flow is:

```text
bounded HTTP input
  -> current MySQL active SUCCEEDED index version
  -> query embedding with the active profile and measured dimensions
  -> bounded Qdrant query with forced version + VERIFIED filters
  -> deterministic paper-level deduplication
  -> one batch MySQL read for all candidate paper IDs
  -> current VERIFIED + normalized DOI + version + timestamp + content-hash admission
  -> bounded debug response assembled from MySQL business fields
```

The retrieval path does not create collections, upsert points, rebuild an
index, modify MySQL, call a generative model, or alter
`POST /api/literature/search`.

## Request and bounds

`POST /api/research/retrieve` accepts `query`, optional `topK`, an optional
`fromYear`/`toYear` range, `paperIds`, and `segmentTypes`. The server defaults
`topK` to 5, caps it at 20, requests at most `topK * 3` candidate points and
never exceeds 60 points. Query text is NFC-normalized, Unicode whitespace is
collapsed, controls are rejected, and the default maximum length is 1000.
Clients cannot select a collection, model, vector dimension, or embedding
version.

Qdrant always receives `embeddingVersion=<active version>` and
`verificationStatus=VERIFIED`. Optional filters are server-generated. Query
responses must contain finite scores and complete frozen payloads; vectors are
never requested or returned.

## MySQL re-admission

Candidates are ranked by score descending, then `paperId` and point ID. Multiple
Segments for one paper collapse to the highest-score Segment. A candidate is
admitted only when all of the following hold:

- the MySQL row exists and is currently `VERIFIED`;
- the MySQL DOI is present, normalizes to itself, matches the Qdrant DOI, and
  matches the paper's DOI;
- the Qdrant verification version and source timestamp match MySQL;
- the Segment type/index exists in the deterministic current MySQL document;
- its current content hash equals the Qdrant content hash; and
- the requested year, paper-ID, and Segment filters match the MySQL record.

Titles, DOI, year, venue, and source timestamps in the response are taken from
MySQL. The Segment excerpt is bounded and is retained only after the content
hash check. Missing, downgraded, stale, DOI-mismatched, or otherwise invalid
candidates are rejected and never replaced with untrusted results.

## Failure semantics and observability

The response reports `FAILED` for dependency or contract failures,
`NO_TRUSTED_RESULTS` for a valid request with no re-admitted paper, and
`SUCCESS` only when at least one paper survives. Stable codes include
`RAG_RETRIEVAL_DISABLED`, `RAG_ACTIVE_VERSION_MISSING`, `RAG_QUERY_INVALID`,
`RAG_EMBEDDING_UNAVAILABLE`, `RAG_EMBEDDING_DIMENSION_MISMATCH`,
`RAG_INDEX_UNAVAILABLE`, `RAG_INDEX_RESPONSE_INVALID`,
`RAG_INDEX_VERSION_MISMATCH`, `RAG_TRUSTED_SOURCE_UNAVAILABLE`, and
`RAG_NO_TRUSTED_RESULTS`.

Logs may contain only the failure code, input length, requested/candidate/
admitted/rejected counts, and elapsed milliseconds. They must not contain the
complete query, vectors, credentials, raw payload, or high-cardinality DOI and
paper-ID labels.

## Evaluation boundary

The small local acceptance set belongs on the separate `eval` branch under
`eval/rag-retrieval-v1/`. Cases without human-reviewed relevant `paperId`
judgments remain `NEEDS_REVIEW`; Recall@1/3/5, MRR, trusted-result retention,
and MySQL re-admission rejection metrics must be reported `UNMEASURED` until
those judgments exist. Retrieval output must never be used to manufacture the
labels.
