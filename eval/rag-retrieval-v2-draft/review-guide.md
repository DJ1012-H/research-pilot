# v2 review guide

The cases have formal Codex-reviewed labels under the user's explicit
authorization of 2026-08-12. On 2026-08-13 Asia/Shanghai, the user audited the
packet and accepted all 12 labels. `user-audit-v0.1.json` records that decision
without erasing or rewriting the earlier review history.

Use `human-review-packet-v0.1.md` as the retained audit packet containing the
complete query, proposed label, candidate-paper legend, and decision method. For each case,
judge relevance against the exact query and year window using the
MySQL-authoritative candidate catalog and the DOI/title/abstract evidence.
Do not use retrieval rank as the relevance label.

Keep `TUNING` cases separate from `FIXED_ACCEPTANCE` cases. Do not tune a
threshold or Top-K against the four fixed cases. For an intentionally
irrelevant query, an empty relevant set is valid only after explicit review;
it still requires provenance and a reviewer-owned decision.

No labels changed during the accepted audit, so the existing case and manifest
hashes remain valid. Before applying any future user revision, recompute each changed case hash and the
manifest LF-SHA256, regenerate `review-queue-v0.2.jsonl` (or a new version),
and rerun the reviewed metric script. Do not copy `retrievalOutput` into
`relevantPaperIds`.
