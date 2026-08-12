# RAG retrieval v2 reviewed candidate dataset

This directory is a candidate-only expansion of the frozen `rag-retrieval-v1`
acceptance set. It contains 12 bilingual cases: 8 `TUNING` cases and 4
`FIXED_ACCEPTANCE` cases.

The dataset was reviewed by Codex under explicit user authorization on
2026-08-12. Every case is now `REVIEWED` with a reviewer, relevant paper IDs
or an explicitly reviewed empty set, and case-level provenance. The review is
an authorized Codex review, not independent human inter-rater review.
Retrieval output was kept separate and was not used as Ground Truth.

The observed metrics for the captured top-5 retrieval are Recall@1=0.1633,
Recall@3=0.5667, Recall@5=0.7200, Hit@5=1.0000, and MRR=0.7583. Recall and
MRR exclude the two reviewed empty-set cases from their denominator; those
cases are reported separately in `reviewed-metrics-v0.1.json`.

Each case hash is the SHA-256 of the canonical string
`caseId|queryLanguage|queryText|relevanceJudgmentProvenance|reviewStatus`.
The manifest records the LF-SHA256 of the complete `cases.jsonl` file.

The four fixed cases are deliberately separated from tuning cases so that
future threshold or Top-K changes cannot silently tune against the release
acceptance queries. The Codex-reviewed labels are reviewer-owned judgments,
not model- or retrieval-derived labels.
