# Trusted RAG Day 6 demonstration guide

Status: `CANDIDATE / NOT READY` as of 2026-08-12. This is local candidate
evidence, not a release announcement.

## Trusted search versus trusted RAG

Trusted search verifies external candidates and returns only formally eligible
papers. Trusted RAG is a separate read-only path: Qdrant supplies candidates,
current MySQL state re-admits them, Java reconstructs the Segment, and only then
the existing model boundary is called. Qdrant or Ollama failure therefore does
not change `POST /api/literature/search`.

## Data flow and index contract

```text
MySQL VERIFIED PaperDTO -> RagDocumentBuilder -> Ollama -> Qdrant
                                                        |
                                                        v
                         MySQL re-admission -> DeepSeek via ModelInvoker
                                                        |
                                                        v
                                                  CitationGuard
```

The initial Collection is `research_pilot_paper_segments_v1`, using cosine
vectors and `embeddingVersion=qe06b-d1024-t1-c350-o30-n1`. Points contain
identity, verification metadata, segment coordinates, content hash, source
timestamp, and controlled payload text. Payload text is never used as answer
evidence. Current MySQL `VERIFIED` state, normalized DOI, source timestamp, and
reconstructed Segment hash must match before admission.

Each paper has one `METADATA` Segment and zero or more `ABSTRACT` Segments.
`maxEvidence=5` is a generation bound, not a retrieval quality threshold; no
`minScore` is configured.

## Reproducible startup

`RagDemo` explicitly enables LLM, MySQL persistence, Flyway, Ollama embedding,
Qdrant, retrieval, and answer generation. It disables OpenAlex, Crossref,
Redis cache, and startup rebuild by default. `-RebuildRagIndex` is required for
a controlled MySQL-to-Qdrant rebuild.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 `
  -Mode RagDemo -MysqlHost localhost -MysqlDatabase research_pilot `
  -MysqlUsername research_pilot -LlmBaseUrl "https://your-llm-provider.example/v1" `
  -LlmModelName "your-model"
```

The script prompts for the LLM API key and MySQL password without echoing or
writing them. RagDemo does not require Crossref contact configuration and does
not automatically fetch papers.

## Fixed offline replay

The replay uses production retrieval, MySQL re-admission, evidence numbering,
validation, and response assembly. Only embedding, index, and model providers
are local fakes. This is fixture orchestration evidence, not a real
cross-language embedding measurement.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\replay-rag-demo.ps1
```

It covers `CROSS_LANGUAGE_CITED_ANSWER`, `YEAR_FILTER_EFFECT`, and
`INSUFFICIENT_EVIDENCE`. The script prints only scenario, status, counts, and
model/repair/citation counts.

## Read-only real-service acceptance

After the operator starts the application and dependencies:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-rag-demo.ps1 `
  -Question "Which papers study selective state space models for dense prediction?" `
  -FromYear 2023 -ToYear 2024
```

The verifier requires application, MySQL, Ollama Embedding, and Qdrant to be
`UP`; Redis may be `DOWN` or `DISABLED`. It checks dimensions, counts Qdrant
Points by type without printing payload text, proves a year-filter change,
validates successful answer citations, and validates zero model/repair calls
for insufficient evidence. It fails closed when any condition is unmeasured.
Only `-ShowPublicAnswer` prints the public answer and Citation DTOs.

## Retrieval evaluation and recovery

`run-rag-retrieval-eval.ps1` validates the cases LF-SHA256 and every frozen case
hash before issuing read-only retrieval calls. The current eval checkout has 7
`NEEDS_REVIEW` cases, not the planned 12 or more; no human `relevantPaperIds`
or provenance exists, so Recall@1/3/5 and MRR are `UNMEASURED`. Retrieval
output must never create Ground Truth. The current real acceptance has four
evidence items and has not exercised the `maxEvidence=5` boundary. External
model cost is `UNMEASURED`.

`verify-rag-rebuild-recovery.ps1` has `CaptureBaseline`, `DeleteCollection`,
and `VerifyRestored` stages. Delete is disabled unless the operator supplies
the exact Collection name, confirmation phrase, baseline state, destructive
switch, and separate authorization. It deletes only that Collection, never a
Docker named volume. This Day 6 closeout does not execute deletion. After
`RagDemo -RebuildRagIndex`, `VerifyRestored` compares version, Point/Segment
counts, active MySQL evidence, and fixed retrieval IDs.

## Release boundary

`CitationGuard` proves citation syntax, existence, and ownership by the current
request; it does not prove semantic entailment or full-text factual correctness.
The candidate remains `NOT READY` until 12 or more cases have human labels and
provenance, formal metrics are measured, real deletion/rebuild recovery passes,
all three real demonstrations pass, unrelated worktree files are removed by
their owner, and the user separately authorizes push and tagging. No PDF, OCR,
full-text, reranker, hybrid search, frontend, multi-agent, MCP, dependency, or
migration work is part of this closeout.
