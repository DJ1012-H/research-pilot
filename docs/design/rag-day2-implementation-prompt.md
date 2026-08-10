# Day 2 Implementation Prompt: Java Embedding Boundary and Trusted Paper Projection

Use this prompt for the next ResearchPilot implementation task.

## Context

Repository: `C:\javaProject\research-pilot`

The trusted-search release is frozen at `v1.0.0-demo`. The current RAG Day 1
branch is based on `origin/main` and contains the rebased infrastructure
baseline. Docker Desktop, Qdrant, and a Windows restart are intentionally
deferred; Day 2 must be implementable and testable without them. Windows-native
Ollama may be available, but a live Ollama call is not required for the default
test run.

Read these files before editing:

- `docs/decisions/ADR-001-vector-store-qdrant.md`
- `docs/design/trusted-rag-index-contract.md`
- `README.md` and the latest relevant `DEVELOPMENT_LOG.md` entry
- the existing `PaperDTO`, `VerificationResult`, `DoiNormalizer`, persistence
  entities/facade, and current port/adapter packages

## Objective

Implement only the Java-side Day 2 foundation:

1. a provider-neutral Java Embedding Port and a Windows-native Ollama adapter;
2. a deterministic `RagDocumentBuilder` for controlled metadata/abstract text;
3. a `VerifiedPaperProjection` that admits only currently trusted papers and
   produces rebuildable, embedding-ready derived documents.

Do not implement Qdrant access, Collection creation, vector upserts, retrieval,
RAG answer generation, or a public RAG API.

## Non-negotiable boundaries

- MySQL remains the only authority for paper identity, verification state,
  verification evidence, and source timestamps.
- Admit a paper only when its current verification status is `VERIFIED` and its
  DOI is already normalized. Reject or return an explicit non-admission result
  for every other status, a missing DOI, or an invalid projection input.
- Do not use Qdrant payload state as an admission signal. Do not query Qdrant.
- Do not change `POST /api/literature/search`, `SearchResponse`, existing result
  limits, Agent budgets, Crossref limits, verification thresholds, persistence
  schema, or trusted-demo behavior.
- Do not add PDF/OCR, generic full-text ingestion, reranking, asynchronous
  execution, multi-Agent behavior, frontend work, Kubernetes, or MCP.
- Do not expose OpenAlex/Crossref DTOs, raw JSON, prompts, model drafts,
  internal traces, secrets, or unapproved full text in the RAG document payload.
- Preserve the two existing uncommitted Day 1 documentation edits. Do not
  reset, stash, restore, clean, or overwrite unrelated work.

## Required design

### 1. Embedding Port and Ollama adapter

Create one minimal provider-neutral port in the existing application/integration
style. Keep Ollama request/response JSON types inside the adapter package.

The port/adapter contract must:

- accept the configured model and one or more controlled text inputs;
- return immutable vectors plus the measured dimension and elapsed observation
  needed by callers;
- fail closed on transport errors, malformed JSON, a missing/empty vector,
  unexpected vector count, non-finite values, or inconsistent dimensions;
- use the existing project HTTP conventions and configuration style where
  possible, with loopback Ollama defaults and no hard-coded credentials;
- never log input text, vector values, API keys, or high-cardinality identifiers;
- keep the live dimension observable. Do not silently coerce a live dimension
  into the frozen `qe06b-d1024-t1-c350-o30-n1` version.

The initial model is `qwen3-embedding:0.6b`. A live dimension other than 1024
must fail closed for this initial version. Do not add a Qdrant dependency.

### 2. RagDocumentBuilder

Build deterministic, provider-independent segments from controlled paper fields
using the frozen contract:

- one `METADATA` segment for every admitted paper;
- zero or more `ABSTRACT` segments only when a controlled abstract is present;
- the exact metadata header and abstract prefix in
  `docs/design/trusted-rag-index-contract.md`;
- deterministic Unicode/whitespace normalization, with the chosen tokenizer
  and chunk overlap rule explicitly documented before using the existing
  `embeddingVersion`;
- approximately 350-token maximum chunks and approximately 30-token overlap,
  with short abstracts kept whole;
- no raw provider JSON or uncontrolled full text;
- lowercase SHA-256 of the exact normalized UTF-8 embedding text as
  `contentHash`.

Do not silently invent a tokenizer or change chunking under the existing
`embeddingVersion`. If the implementation choice differs from the frozen
contract, update the contract/version explicitly or stop and report the
conflict instead of guessing.

### 3. VerifiedPaperProjection

Create a small immutable projection model that receives the authoritative
MySQL paper identifier and controlled paper/verification/source fields. It must
produce the document segments and all payload fields required by the frozen
contract, including:

`paperId`, normalized `doi`, title, publication year, venue, language,
`VERIFIED` status, existing verification-policy version, segment type/index,
embedding model/version, `contentHash`, `sourceUpdatedAt`, and exact embedded
text.

Use the numeric MySQL paper identifier for identity. Never substitute an
OpenAlex ID or DOI for `paperId`. Reuse the existing verification-policy
version convention instead of duplicating a conflicting literal.

For point identity, derive UUID version 5 from the fixed namespace
`74fbcd22-6592-5cd8-a606-29d5ad4e5e9f` and the canonical UTF-8 name:

`paperId | embeddingVersion | segmentType | segmentIndex`

Validate all fields before constructing the name and reject any field containing
the separator. Repeated projection of the same source must produce the same
point ID, while a changed segment/version/index must produce a different ID.

## Focused validation only

Add the smallest useful deterministic test set; do not run a full environment
acceptance, Docker command, Qdrant command, live database migration, dependency
audit, repository-wide security scan, or unrelated regression suite for this
Day 2 task.

Required focused coverage:

- verified plus normalized DOI is admitted; every other verification status or
  invalid DOI is rejected without an embedding call;
- metadata and abstract text are deterministic, including empty/short/long
  abstract boundaries and overlap behavior;
- content hashes are stable and change when exact embedded text changes;
- UUIDv5 point IDs are stable, separator-safe, and sensitive to canonical-field
  changes;
- adapter parsing rejects empty, malformed, mismatched, non-finite, or
  unexpected vectors and does not log text/vector contents;
- a fake Embedding Port proves the projection/builder layer is independent of
  Ollama and Qdrant.

Prefer focused unit tests and existing test utilities. Do not add tests merely
to increase counts. The default test command should target only the new/changed
test classes, for example:

```powershell
.\mvnw.cmd "-Dtest=RagDocumentBuilderTest,VerifiedPaperProjectionTest,OllamaEmbeddingAdapterTest" test
```

Use the actual class names after implementation. If dependency resolution is
blocked, report it; do not change repositories or build configuration merely
to force a pass.

## Completion checklist

Before handing off:

1. confirm the public trusted-search contract and budgets are unchanged;
2. confirm no Qdrant client, Collection, vector write, retrieval, or RAG API
   was added;
3. run only the focused tests above and `git diff --check`;
4. report changed files, the chosen normalization/chunking rule, admission
   behavior, point-ID derivation, and any unresolved contract ambiguity;
5. do not claim Day 2 or RAG acceptance without live Qdrant evidence in a later
   explicitly authorized environment task.

Do not commit or push unless the surrounding task explicitly authorizes that
delivery step.
