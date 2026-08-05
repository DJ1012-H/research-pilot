# ADR-001: MySQL authority and a rebuildable Qdrant index

- Status: Accepted
- Original decision: 2026-07-16
- RAG baseline frozen: 2026-08-10

## Context

ResearchPilot already uses MySQL for durable literature-task evidence and Redis for short-lived provider-result caching. The available Redis service does not provide RediSearch, and cache availability is not an authority for paper eligibility.

Adding semantic retrieval creates a second representation of trusted papers. That representation can be stale, unavailable, or rebuilt, so it cannot decide whether a paper is verified.

## Decision

- MySQL remains the durable source of truth for papers, verification status and evidence, task traces, and the active index version.
- Redis remains a fail-open, TTL-bound cache. It does not store embeddings or admit papers.
- Qdrant stores only vectorized segments projected from papers that are currently `VERIFIED` and have a normalized DOI.
- Qdrant is a derived index that can be fully rebuilt from MySQL. Qdrant payload status is never accepted as proof of verification.
- Results returned by Qdrant must be re-admitted against current MySQL state before answer generation.
- Existing trusted literature search remains available when Ollama or Qdrant is disabled or unavailable.

The local infrastructure pins Qdrant `v1.18.2`, binds HTTP `6333` and gRPC `6334` to loopback only, and stores data in a Docker named volume. The embedding model is Windows-native Ollama with `qwen3-embedding:0.6b`.

The vector dimension must be measured from a real Ollama response before collection creation. It is not hard-coded from documentation or assumptions.

The complete collection, payload, point-identity, versioning, rebuild, and deletion rules are frozen in [the trusted RAG index contract](../design/trusted-rag-index-contract.md).

## Consequences

Benefits:

- Trust admission stays in the existing Java/MySQL boundary.
- Index loss does not lose task or verification facts.
- Deterministic point IDs and content hashes make repeated indexing idempotent.
- Embedding, template, dimension, or chunking changes can migrate through a new version without silently mixing vectors.

Costs:

- MySQL and Qdrant can be temporarily inconsistent.
- Retrieval requires a MySQL re-admission step.
- Rebuild, drift detection, retry, and version activation need explicit implementation and tests.
- Docker Desktop, WSL 2, Ollama, and Qdrant add local operational dependencies.

## Failure and rollback

If Qdrant or Ollama is unavailable, RAG is disabled or returns an explicit degraded result; the trusted literature search API continues unchanged. A failed new index never replaces the active version. The old collection is retained until the new collection passes count, dimension, filter, and retrieval checks.

Named-volume or collection deletion is not part of normal restart. It requires explicit human confirmation after MySQL rebuildability has been verified.
