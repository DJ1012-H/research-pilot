# RAG retrieval v3-lite label review notes

## Review authority and limit

These labels were prepared by Codex on 2026-08-13 after the user asked to
complete Day 1 of the interview-readiness plan. The review used the complete
18-paper frozen candidate catalog plus the nine abstract segments present in
the live derived index. The repository owner then explicitly confirmed all 24
supplied labels on 2026-08-13; the decision is retained in
`user-audit-v0.1.json`.

Every case is therefore marked `USER_AUDITED_CODEX_REVIEWED`. This remains a
user audit of Codex-supplied decisions, not blind independent relabeling or
inter-rater Ground Truth. Retrieval outputs were not used to create or change
a relevance label.

## Evaluation scope

The current answer endpoint admits `ABSTRACT` evidence. v3-lite consequently
labels relevance only among abstract-bearing catalog papers. A metadata-only
paper may be topically related but is not positive answer evidence until an
abstract is acquired, trusted, persisted, and the derived index is rebuilt.

DOI is the stable label key. `paperIdSnapshot` appears only in the candidate
catalog for diagnostics and must be resolved again from the current trust
authority before a live run.

## Balance

| Split | Cases | Positive | Negative | Chinese | English |
|---|---:|---:|---:|---:|---:|
| TUNING | 12 | 6 | 6 | 6 | 6 |
| FIXED_HOLDOUT | 12 | 6 | 6 | 6 | 6 |
| Total | 24 | 12 | 12 | 12 | 12 |

The twelve negative cases cover out-of-domain requests, in-domain topics that
are absent from the frozen corpus, underspecified comparative claims, and
deterministic empty-year controls. This supports a first refusal measurement;
it does not establish production-wide calibration.

## v2 re-review and migration decisions

The historical `rag-retrieval-v2-draft` directory remains unchanged. Its 12
cases were re-read against the expanded 18-paper snapshot:

| Historical case | v3-lite decision |
|---|---|
| `rag-v2-0001`, `0002`, `0009`, `0010` | Migrate the selective-state-space intent to DOI labels. Exclude metadata-only Frequency-Enhanced Mamba and DC-Mamba from answer-evidence labels; add abstract-bearing Samba where semantic segmentation or dense prediction is in scope. |
| `rag-v2-0003`, `0004` | The old single positive, RS-Mamba, is incomplete after corpus growth. Samba is also relevant to long-sequence remote-sensing segmentation. The refreshed intent appears in `rag-v3l-0003` and `0013`. |
| `rag-v2-0005` | Preserve the post-2023 segmentation intent, but refresh it around the two abstract-bearing state-space segmentation papers instead of retaining an ID-only label. |
| `rag-v2-0006` | Preserve change-detection state-space coverage through abstract-bearing CDMamba, ChangeMamba, and RS-Mamba. Metadata-only related papers are catalogued but not answer positives. |
| `rag-v2-0007` | Preserve as a semantic negative concept; v3-lite adds eleven more negative cases rather than treating one negative as sufficient calibration. |
| `rag-v2-0008` | Do not migrate the ambiguous conceptual comparison directly. Replace it with queries whose evidence requirements are reviewable from title and abstract. |
| `rag-v2-0011` | Preserve the year-filter principle with DOI labels and abstract eligibility; do not reuse the historical paper-ID set. |
| `rag-v2-0012` | Preserve a deterministic future-year empty control in both splits. This control is reported separately from semantic-negative refusal. |

## Case-level review summary

- `rag-v3l-0001` through `0006` and `0013` through `0018` each have at least
  one abstract-bearing relevant DOI and a written topical justification.
- `rag-v3l-0007` through `0011` and `0019` through `0023` have no supporting
  paper in the complete frozen catalog.
- `rag-v3l-0012` and `0024` are deterministic empty-window controls.
- The fixed holdout must not be edited or inspected for result-driven tuning
  after the manifest is frozen. Only infrastructure-invalid runs may be
  discarded, and that decision must be recorded before any rerun.

## Remaining independent-review limit

The labels may now be described as author-audited resume evidence. They must
not be described as blind independent labels, inter-rater agreement, or formal
Ground Truth unless a separate reviewer independently relabels the cases.
