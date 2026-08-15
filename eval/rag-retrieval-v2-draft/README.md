# RAG retrieval v2 reviewed candidate dataset

This directory is a candidate-only expansion of the frozen `rag-retrieval-v1`
acceptance set. It contains 12 bilingual cases: 8 `TUNING` cases and 4
`FIXED_ACCEPTANCE` cases.

## Review status

The status vocabulary is deliberately separated so that `REVIEWED` is not
mistaken for independent human inter-rater review:

- Case lifecycle: `REVIEWED` in `cases.jsonl` and the current
  `review-queue-v0.2.jsonl`.
- Reviewer and authority: `codex`, under explicit user authorization on
  2026-08-12.
- Ground Truth status: `USER_AUDITED_CODEX_REVIEWED`; retrieval output stayed
  separate and did not create labels.
- User audit: `COMPLETED_ACCEPT_ALL`. On 2026-08-13 Asia/Shanghai, the user
  replied `RAG v2 审核：全部 ACCEPT` for all 12 labels. The decision is retained
  in `user-audit-v0.1.json`; no case label or case hash changed.
- Independent blind inter-rater relabeling: `NOT_PERFORMED`. The user audited
  the supplied review packet rather than independently recreating every label.
- Historical `review-queue.jsonl`: `SUPERSEDED`, retained unchanged for audit.
  Its status transition is recorded in `review-queue-v0.1-supersession.json`.

## Metrics and cost evidence

The current user-audited metrics are in
`runs/rag-eval-20260812T173345535Z-ed95a8f0/reviewed-metrics.json` (schema
v0.3). The repaired real-service top-5 measurements are Recall@1=0.2833,
Recall@3=0.4467, Recall@5=0.5800, Hit@5=0.9000, and MRR=0.8250. Empty-set
rejection is 0.5000 and semantic-negative rejection is 0.0000. Threshold
selection remains `UNMEASURED_INSUFFICIENT_REVIEWED_SEMANTIC_NEGATIVES`.

`reviewed-metrics-v0.1.json` and `reviewed-metrics-v0.2.json` remain unchanged
as historical records derived from the earlier observation. The metric change
is not model drift: the repaired runner now sends year filters and explicit
UTF-8 query bytes, and the canonical run preserves that exact evidence.

`cost-observation-v0.2.json` is the current cost observation. It records the
provider-reported 2,614 input, 1,104 output, and 3,718 total tokens and reports
both cache-miss and cache-hit estimates because the actual cache state is
unknown. `cost-observation-v0.1.json` remains unchanged as the earlier
character-estimate-only historical observation.

## Reproducibility boundary

Each case hash is the SHA-256 of the canonical string
`caseId|queryLanguage|queryText|relevanceJudgmentProvenance|reviewStatus`.
The manifest records the LF-SHA256 of the complete `cases.jsonl` file.

The four fixed cases are deliberately separated from tuning cases so that
future threshold or Top-K changes cannot silently tune against the release
acceptance queries. The current Codex-reviewed labels are reviewer-owned
judgments, not model- or retrieval-derived labels.

Run the complete chain with `scripts/run-rag-reviewed-eval.ps1`. It creates a
new timestamped directory, snapshots cases/manifest/catalog, captures one live
observation, computes fail-closed metrics, repeats the observation, and compares
all deterministic fields. The canonical run's `reproducibility-check.json`
reports `PASS`, 12 compared cases, and zero differences; volatile IDs,
timestamps, and elapsed times are explicitly excluded.

Known calibration limits, Qdrant findings, and the exact data needed for a real
`maxEvidence=5` truncation test are recorded in
`evaluation-investigation-v0.1.md`, `qdrant-transport-investigation-v0.2.md`,
and `max-evidence-boundary-data-plan-v0.1.md`. That boundary is now
`COMPLETED_PASS`: the live observation in
`max-evidence-boundary-observation-v0.1.json` records six MySQL-re-admitted
ABSTRACT evidence items truncated to five generation inputs, with one model
call and citation positions bounded to `P1..P5`.
