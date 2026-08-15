# RAG retrieval v3-lite

`rag-retrieval-v3-lite` is a small, frozen, bilingual evaluation dataset for
the interview-readiness version of ResearchPilot. It measures positive
retrieval and evidence refusal over the exact paper corpus available on
2026-08-13. It does not claim production coverage or independent Ground Truth.

## What changed from v2

- Stable `relevantDois` replace database-local `relevantPaperIds`.
- The candidate catalog covers the current 18 papers and records content hashes.
- Labels target the nine papers with an `ABSTRACT` segment because the answer
  path requires abstract evidence.
- The corpus contains 24 cases: 12 tuning and 12 untouched fixed holdout.
- Each split contains six positive and six negative cases and is balanced
  between Chinese and English.
- Twelve negatives cover semantic refusal as well as deterministic empty sets.

The historical `rag-retrieval-v2-draft` evidence is intentionally not mutated.

## Files

- `candidate-catalog.json`: live-derived, frozen 18-paper snapshot; DOI is the
  stable identity and `paperIdSnapshot` is diagnostic only.
- `tuning-cases.json`: the only cases available for Day 2 prompt or policy
  tuning.
- `fixed-holdout-cases.json`: frozen acceptance cases; do not tune against them.
- `review-notes.md`: label rationale, v2 migration audit, and review limits.
- `user-audit-v0.1.json`: the repository owner's explicit acceptance of all
  24 supplied labels.
- `schema/retrieval-case.schema.json`: case contract.
- `manifest.json`: counts, review state, file hashes, and acceptance boundary.
- `../../scripts/validate-rag-retrieval-v3-lite.ps1`: offline fail-closed
  structural and hash validation.

## Label status

Every case is `USER_AUDITED_CODEX_REVIEWED`. Codex reviewed the complete frozen
catalog under the user's request to complete Day 1, and the repository owner
explicitly accepted all 24 supplied labels on 2026-08-13. This was an audit of
the supplied decisions, not blind independent relabeling or inter-rater Ground
Truth. Retrieval output did not create the labels.

## Validation

From the `eval` worktree root:

```powershell
.\scripts\validate-rag-retrieval-v3-lite.ps1
```

The script performs no network or model call. It fails closed on malformed
DOIs, duplicate queries, split imbalance, missing catalog entries,
metadata-only positive labels, case-hash drift, or manifest file-hash drift.

## Day 1 boundary

This dataset preparation does not implement an evidence-relevance judge, tune
a score threshold, run the fixed holdout, or report Recall/MRR/refusal metrics.
Those activities belong to later days and remain `UNMEASURED` here.
