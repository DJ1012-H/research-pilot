# maxEvidence=5 real-boundary data plan v0.1

## Completion status

`COMPLETED_PASS` on 2026-08-13. The controlled MySQL-driven rebuild produced
21 points: 15 `METADATA` plus 6 `ABSTRACT` points from six distinct papers
(`paperId` 5, 6, 7, 10, 14, and 15). A live `topK=10` answer request observed
`retrievalSummary.evidenceCount=6`, `diagnostics.generationEvidenceCount=5`,
`modelCallCount=1`, and `SUCCESS`; all public citation positions were bounded
to the five-item generation evidence set. The exact runtime and code hashes are
frozen in `max-evidence-boundary-observation-v0.1.json`.

## Measured starting point

The active Collection has 19 points: 15 `METADATA` and 4 `ABSTRACT`. The four
ABSTRACT points belong to four distinct papers (`paperId` 6, 10, 14, and 15).
An ABSTRACT-only live retrieval with `topK=10` admitted exactly those four
papers. The answer path deduplicates by `paperId`, so adding more segments to
one of those papers cannot exercise the generation truncation boundary.

## Minimum data addition

Add or enrich **two distinct papers** so that the trusted MySQL source contains
at least six different current papers with all of the following properties:

- current verification status is `VERIFIED`;
- normalized DOI is non-empty and unique;
- abstract is non-empty and survives the normal document builder;
- topic is close enough to the same controlled query that all six ABSTRACT
  points can be admitted in one retrieval;
- persistence goes through the existing trusted search/verification path, not
  direct Qdrant insertion or an ad-hoc SQL-only fixture.

Two newly persisted papers would produce at least 23 points after rebuild
(17 metadata plus 6 abstract). Enriching two already persisted metadata-only
papers would produce at least 21 points (15 metadata plus 6 abstract). Either
shape is sufficient; six **distinct admitted paper IDs** is the real gate.

## Rebuild and test

1. Persist the two trusted abstracts in MySQL and run the controlled
   MySQL-driven Qdrant rebuild for the active embedding version.
2. Call `POST /api/research/retrieve` with `segmentTypes=["ABSTRACT"]` and
   `topK=10`. Require `admittedPaperCount >= 6`, six distinct paper IDs, and
   `results[0..5].matchedSegmentType == ABSTRACT`.
3. Call `POST /api/research/ask` with the same question and `topK=10`. Require
   `retrievalSummary.evidenceCount >= 6` while the generation input count is
   exactly 5.
4. Require every public citation position to be in `P1..P5`; no sixth evidence
   item may enter the prompt or citation allow-list.

The public response now exposes the safe post-truncation integer
`generationEvidenceCount` in diagnostics. `verify-rag-demo.ps1` asserts
`retrieved > 5` and `generationEvidenceCount == 5`; the observability change
does not alter retrieval ranking, evidence selection, or the model prompt.

## What does not count

- Five or fewer admitted ABSTRACT papers: the limit is reached at most, not
  demonstrably truncating an excess item.
- Six segments from fewer than six papers: retrieval deduplicates by paper.
- METADATA results: the answer path forces `ABSTRACT` evidence.
- Direct Qdrant test points: MySQL re-admission would reject them and they do
  not prove the real trust boundary.
