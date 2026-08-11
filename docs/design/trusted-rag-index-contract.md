# Trusted RAG index contract

Status: frozen for the 2026-08-10 infrastructure baseline, Day 2 Java projection foundation, and opt-in Day 3 rebuild path. No public retrieval or RAG answer API is enabled by this document.

## Authority and admission

- MySQL is the only durable source of truth for papers, verification evidence, and the active index version.
- Qdrant is a rebuildable derived index. Losing or deleting the index never changes MySQL state.
- A paper is eligible only when its current MySQL status is `VERIFIED` and its DOI is normalized.
- Every retrieval result must be re-admitted against current MySQL state before it can become answer evidence.
- `PARTIALLY_VERIFIED`, `REJECTED`, `SOURCE_UNAVAILABLE`, `NOT_FOUND`, and unchecked papers are never indexed.
- Qdrant and embedding failures must not change the existing `POST /api/literature/search` contract or availability.

## Initial collection

The initial collection name is `research_pilot_paper_segments_v1`. It uses one dense vector and cosine distance.

The initial environment observation on 2026-08-06 measured 1024 dimensions from both Chinese and English inputs using Ollama 0.32.5 and `qwen3-embedding:0.6b`. The cold Chinese request took 23,938 ms and the following English request took 4,611 ms. These timings are observations, not service-level objectives.

A repeat smoke on 2026-08-09 using the same Windows-native Ollama model measured 1024 dimensions for both inputs; the Chinese request took 5,524 ms and the English request took 139 ms. These timings are also observations, not service-level objectives.

The initial `embeddingVersion` is `qe06b-d1024-t1-c350-o30-n1`. It identifies the model family, measured dimension, template, chunk size, overlap, and normalization generation. The dimension must still not be trusted as a source-code assumption. Before creating or opening the collection, the controlled environment check must:

1. call the configured Ollama model with one Chinese and one English input;
2. require exactly one non-empty vector for each input;
3. require both vectors to have the same dimension;
4. record that measured dimension and use it when creating the collection.

The local baseline model is `qwen3-embedding:0.6b`. A model, dimension, text-template, chunking, or incompatible normalization change requires a new `embeddingVersion` and a new collection or an explicit migration. A live dimension other than 1024 is a configuration mismatch for this initial version; it must fail closed rather than silently create a different schema under the same version.

## Segments and content

Each admitted paper has one `METADATA` segment. It may also have one or more `ABSTRACT` segments. `PDF_TEXT` remains outside the required milestone.

The stable metadata template is:

```text
Title: {title}
Authors: {authors}
Year: {year}
Venue: {venue}
Keywords: {keywords}
DOI: {doi}
```

An abstract segment uses the same metadata header followed by:

```text
Abstract: {abstract segment}
```

Initial abstract chunking is at most approximately 350 tokens with approximately 30 tokens of overlap. Short abstracts stay whole. For `qe06b-d1024-t1-c350-o30-n1`, the implemented rules are frozen as follows:

- Normalize every controlled string to Unicode NFC.
- Treat Java Unicode whitespace plus Unicode space, line, and paragraph separators as whitespace; collapse each run to one ASCII space and trim leading and trailing whitespace. Reject non-whitespace ISO control characters.
- Preserve the source order of authors and non-blank keywords, joining either list with comma plus one ASCII space. Missing optional scalar fields render as an empty value after their label. Template lines use LF (`\n`) and the final line has no trailing newline.
- The deterministic approximate tokenizer emits each Han, Hangul, Hiragana, Katakana, or Bopomofo code point as one token; emits each maximal run of other Unicode letters, digits, and combining marks as one token; and emits every other non-whitespace code point as one token.
- Abstract windows contain at most 350 such tokens. A following window starts at the first of the preceding window's final 30 tokens. An abstract of 350 tokens or fewer stays whole. Segment text starts and ends at token boundaries without adding ellipses or other text.
- `METADATA` has segment index 0. `ABSTRACT` indices start at 0 independently and increase in chunk order.

Raw OpenAlex/Crossref JSON, prompts, model drafts, internal traces, secrets, and unapproved full text are excluded. `contentHash` is the lowercase SHA-256 hex digest of the exact normalized UTF-8 text sent to the embedding model.

## Stable point identity

The canonical point-name fields are:

```text
paperId | embeddingVersion | segmentType | segmentIndex
```

The canonical name serializes the four fields without surrounding whitespace as `paperId|embeddingVersion|segmentType|segmentIndex`. Decimal numbers use their ordinary base-10 representation and `segmentType` uses its uppercase enum name. The implementation must validate every field before concatenation, reject a string field containing the separator, encode the canonical name as UTF-8, and derive an RFC 4122 UUID version 5 using the fixed namespace `74fbcd22-6592-5cd8-a606-29d5ad4e5e9f`.

The same eligible paper, index version, segment type, and segment index must therefore produce the same point ID. Repeated upserts replace the same point rather than create duplicates.

## Payload

Every point carries these fields:

| Field | Rule |
|---|---|
| `paperId` | MySQL paper identifier used for re-admission |
| `doi` | Normalized DOI used for display and exact filtering |
| `title` | Controlled display title |
| `publicationYear` | Integer year |
| `venue` | Controlled display venue |
| `language` | Analysis/filter metadata, not a trust signal |
| `verificationStatus` | Always `VERIFIED` at write time |
| `verificationVersion` | Java verification-policy version |
| `segmentType` | `METADATA` or `ABSTRACT` for the initial milestone |
| `segmentIndex` | Zero-based ordering within the paper and segment type |
| `embeddingModel` | `qwen3-embedding:0.6b` for the initial version |
| `embeddingVersion` | Model, dimension, template, normalization, and chunking version |
| `contentHash` | Lowercase SHA-256 of the embedded text |
| `sourceUpdatedAt` | MySQL source-record timestamp |
| `text` | Exact controlled text embedded for this point |

Payload indexes are required for `paperId`, `doi`, `verificationStatus`, `publicationYear`, and `embeddingVersion`.

## Version activation, rebuild, and deletion

1. Create a new collection with the live-measured vector size.
2. Read eligible papers from MySQL and generate deterministic points.
3. Upsert and verify point counts, dimensions, payload filters, and sampled retrieval.
4. Persist the new active index version in MySQL.
5. Make retrieval use the active version.
6. Retain the old collection until regression checks pass.
7. Delete an old collection only after explicit human confirmation.

When a paper is deleted or ceases to be `VERIFIED`, delete all of its points for the affected index version by `paperId` filter. Failure is retried and audited; it cannot restore eligibility. A full rebuild into a new collection is the recovery path for drift or Qdrant data loss.

Normal operation must never run `docker compose down -v`. Removing the named volume or a collection is a destructive maintenance action and requires explicit confirmation after MySQL rebuildability has been checked.
