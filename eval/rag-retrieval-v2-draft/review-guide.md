# v2 draft review guide

This is a reviewer audit guide. The user explicitly authorized Codex to act
as the reviewer on 2026-08-12. The labels are formal Codex-reviewed labels,
but they are not independent human inter-rater review.

For each case, the authorized reviewer should inspect the current MySQL-authoritative
paper set and the corresponding Crossref verification evidence, then record
the relevant paper IDs, the reviewer's identity, the decision date, and a
short provenance reference. A paper ID may be entered only when the reviewer
can explain why it is relevant to the exact query and filter window.

Keep `TUNING` cases separate from `FIXED_ACCEPTANCE` cases. Do not tune a
threshold or Top-K against the four fixed cases. For an intentionally
irrelevant query, an empty relevant set is valid only after authorized review; it
must still have explicit provenance and `REVIEWED` status.

Before promoting any case, verify its frozen hash after the review fields are
updated. Do not copy `retrievalOutput` into `relevantPaperIds`. Retrieval
output may be stored separately as observed evidence, but it is never Ground
Truth.

Formal Recall@1/3/5, Hit@5, MRR, and threshold decisions are recorded in
`reviewed-metrics-v0.1.json` after the reviewed cases were frozen and the
metric runner validated their manifest and hashes.
