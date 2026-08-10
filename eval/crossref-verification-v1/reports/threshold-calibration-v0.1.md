# Crossref Threshold Calibration v0.1

## Scope and production baseline

This report evaluates only the reviewed `crossref-verification-v1` dataset with
the production field-evidence components merged through `8079c35`. The fixture
line-ending baseline is `03115d5`. It does not call external APIs, create
labels, decide paper-level verification, or alter production configuration.

The production defaults are title strong/possible `0.92` / `0.85`, author
overlap `0.60`, source match `0.85`, and publication-year tolerance `1`.
These are engineering rules, not probabilities or statistical confidence.

## Dataset and frozen split

The dataset version is `crossref-verification-v1`. All 14 formal JSONL cases
are `REVIEWED`; none are `NEEDS_REVIEW`, so `excluded_cases` is empty.

- Calibration (10): 0001, 0003–0009, 0012, 0014.
- Acceptance (4, frozen): 0002 equivalence, 0010 first-author replacement,
  0011 distinct-title replacement, and 0013 online-first year difference.

The split is stored in `manifests/calibration-split-v0.1.json` and validated
against its dedicated schema. Acceptance IDs were selected before running the
field-evidence checks and are never used to tune thresholds.

## Calibration and candidate values

Every reviewed case provides five human-supported field expectations: DOI,
title, first author, publication year, and venue. The adapter constructs the
unchanged `CandidatePaper` and `CrossrefWorkMetadata` contracts directly from
JSONL and fails rather than repairing an input.

At production defaults, 48 of 50 calibration field expectations agree. Cases
0012 and 0014 have the same reviewed venue expectation (`MATCH`) but produce
`MISMATCH` with `SOURCE_SIMILARITY_BELOW_THRESHOLD`; the raw venue score is
`5/7 = 0.714285...`. This is below every evaluated source threshold (0.80,
0.85, 0.90), so this calibration set does not support a threshold-only change.

The finite candidate values evaluated independently on calibration data were:

- title strong: 0.90, 0.92, 0.94;
- title possible: 0.82, 0.85, 0.88;
- author overlap: 0.50, 0.60, 0.70;
- source match: 0.80, 0.85, 0.90;
- year tolerance: 0, 1.

No title case fell into a range that supported changing either title threshold.
The dataset has no human field oracle for author-set overlap, so it cannot
support changing `authorOverlap`. Year tolerance `0` changes the reviewed
online-first case 0013 from `EXPLAINABLE_DIFFERENCE` to `MISMATCH`; tolerance
`1` preserves its human-supported field outcome.

Recommendation: retain all current production defaults. A future main-branch
change would need explicit review of venue normalization (for example,
singular/plural naming) before considering a lower source threshold.

## Frozen acceptance result

Nineteen of 20 acceptance field expectations agree at the retained defaults.
Case 0013 retains the expected year `EXPLAINABLE_DIFFERENCE`, but its venue has
the same `5/7` mismatch observed in calibration. This is an acceptance failure
record, not a reason to tune on acceptance or alter its case membership.

## Limits and follow-up inputs

The sample is small; field-level human expectations are incomplete for authors;
surname-plus-initial keys can collide; venue abbreviations and aliases have no
complete dictionary; and no `VerificationPolicy` exists. Similarity is not a
probability, and this evaluation cannot compute a paper-level `VERIFIED`
confusion matrix or false-VERIFIED rate.

The next policy phase receives DOI/title/first-author/authors/year/venue
statuses, raw title/author-overlap/source scores, year deltas, missing-field
states, and stable reason codes. Decisions still required include DOI agreement
with title/author conflict, DOI-missing strong evidence, treatment of
`NOT_EVALUATED`, direct rejection conflicts, and whether missing source or
author evidence blocks formal admission.
