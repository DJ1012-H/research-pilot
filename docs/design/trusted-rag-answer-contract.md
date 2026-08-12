# Trusted RAG answer contract

Status: Day 5 implementation contract. The route is disabled unless
`RAG_ANSWER_ENABLED=true` is explicitly supplied.

## Read-only evidence boundary

`POST /api/research/ask` reads the current active `SUCCEEDED` MySQL index
version, uses its embedding profile, queries Qdrant with server-forced
`ABSTRACT`, active `embeddingVersion`, and `VERIFIED` filters, and re-admits
every candidate through the Day 4 MySQL boundary. It does not call OpenAlex or
Crossref, write MySQL, rebuild an index, create a Collection, upsert/delete a
Point, or use Qdrant payload text as answer evidence.

Only Java-reconstructed current Segment text from `RagDocumentBuilder` may be
sent to the model. A changed or stale Qdrant payload is rejected by the
existing DOI, verification-version, source-timestamp, Segment and content-hash
checks. Multiple hits for one paper keep the deterministic highest-score hit.

## Generation and validation

At least one re-admitted `ABSTRACT` is required before generation. The existing
OpenAI-compatible ChatModel is reached only through `ModelInvoker` with the
fixed operation `rag_answer`; request and response logging remain disabled.
The model receives bounded untrusted evidence and can return only statement
text plus `P1`-style IDs. Java runs:

```text
raw String -> JSON syntax -> JSON Schema -> strict DTO mapping
           -> business validation -> CitationGuard -> public response
```

There is at most one initial call and one Java-controlled repair. Provider
retry inside `ModelInvoker` is not a second Java repair. A provider failure,
deadline, invalid second draft, unknown citation, or other unmeasured outcome
fails closed without publishing partial text.

`CitationGuard` proves citation syntax, membership and ownership by this
request only. It does not prove semantic entailment or full-text factual
correctness. Public DOI, title, year, venue, paper ID, Segment coordinates and
content hash are assembled from trusted Java evidence, never copied from model
output.

## Degradation and evidence

No re-admitted ABSTRACT returns `INSUFFICIENT_EVIDENCE`, an empty answer and
empty citations, with zero model and repair calls. Retrieval dependency failure,
generation failure, validation failure and deadline have distinct stable
failure codes. `minScore` is intentionally absent until human relevance
judgments calibrate the Day 4 evaluation; a reported lowest score is only a
relatedness observation.

The route is default-off. Offline tests use fakes, Mockito and H2. Real
MySQL/Ollama/Qdrant/DeepSeek acceptance is separate, requires explicit
authorization, and must report external calls and cost separately.
