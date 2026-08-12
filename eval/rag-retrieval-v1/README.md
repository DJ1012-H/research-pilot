# RAG retrieval v1 local acceptance set

This is a small, human-reviewable local acceptance set for Day 4 trusted
vector retrieval. It is not a general academic search benchmark.

Every case is intentionally `NEEDS_REVIEW` with an empty `relevantPaperIds`
set. The cases must not be promoted to formal Recall@K or MRR measurement
until a reviewer supplies relevant paper identifiers and provenance. Current
retrieval output is never used to manufacture Ground Truth.

Each case hash is the LF-SHA256 of the canonical string
`caseId|queryLanguage|queryText|relevanceJudgmentProvenance|reviewStatus`.
The manifest separately records the LF-SHA256 of the complete `cases.jsonl`.
