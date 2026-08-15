# RAG Day 6 evaluation investigation v0.1

This note records the investigation, implemented evaluation-chain repairs, and
remaining data gates. No production business logic was changed; changes are
limited to evaluation scripts and an opt-in live diagnostic test.

## 1. Reproducible evaluation flow

### Current reusable pieces

- Main `scripts/run-rag-retrieval-eval.ps1` validates the cases LF-SHA256 and
  every case hash before making read-only retrieval calls.
- Eval `scripts/compute-rag-reviewed-metrics.ps1` validates reviewed labels and
  computes Recall@1/3/5, Hit@5, MRR, and separate empty-set observations.
- `TUNING` and `FIXED_ACCEPTANCE` already exist in the case schema.

### Repairs completed

1. The main runner now sends `fromYear`/`toYear`, writes request JSON as explicit
   UTF-8 bytes, preserves scores and ranked result summaries, validates counts,
   ordering, status, Top-K, and case hashes, and emits the JSONL schema consumed
   by the metric script.
2. Run metadata pins main commit and tracked-dirty state, cases/manifest/catalog/
   runner hashes, Collection status/count/dimensions, active embedding version,
   service locations, status counts, and start/end UTC timestamps.
3. The metric script rejects duplicate/missing/extra observations and invalid
   reviewed provenance, hashes, status, score, or ranking shapes. It reports
   overall and tuning/fixed metrics plus explicit empty-set and semantic-negative
   rejection rates, and never overwrites an existing output.
4. `scripts/run-rag-reviewed-eval.ps1` snapshots inputs into a timestamped run
   directory, performs capture -> metrics -> repeat capture, and fails if a
   deterministic field drifts. Volatile IDs, timestamps, and elapsed times are
   explicitly excluded from equality.
5. Canonical run `runs/rag-eval-20260812T173345535Z-ed95a8f0` passed all 12
   cases with zero deterministic differences and pins every input/script hash.
   Historical v0.1 artifacts remain unchanged.

## 2. Unrelated-query refusal calibration

### Current behavior

- `RagRetrievalService` validates trust by re-reading current MySQL `VERIFIED`
  papers and reconstructing segments, but it does not enforce a semantic
  relatedness threshold.
- Any admitted top-K result is considered a successful retrieval regardless of
  score. `RagAnswerService` generates an answer whenever at least one admitted
  ABSTRACT exists.
- `rag-v2-0007` has an authorized empty relevant set, but the repaired live run
  returned `SUCCESS` with top-1 score 0.60970926. This is a demonstrated
  semantic false acceptance.
- `rag-v2-0012` returned `NO_TRUSTED_RESULTS`, but only because the 2099-2100
  filter produced an empty candidate window. It does not calibrate semantic
  rejection.
- Only one semantic-negative case exists. A defensible threshold is therefore
  still `UNMEASURED`, now due to sample size rather than missing scores.
- Positive top-1 scores range down to 0.5076214, below the semantic negative's
  0.60970926. Selecting 0.60970926 as a threshold would reject real positives;
  this sample already disproves clean scalar separation.

### Minimal calibration plan

1. Add a separate reviewed negative set with at least 24 tuning and 12 fixed
   cases across: clearly out-of-domain, in-domain-but-no-match, multilingual,
   nonsensical/adversarial, and empty-year-window controls.
2. Capture top-1 score, lowest admitted score, rank-score distribution, active
   embedding version, and candidate-catalog hash for positives and negatives.
3. Tune only on `TUNING`, targeting an explicit false-answer ceiling and
   positive abstention ceiling; validate once on `FIXED_ACCEPTANCE`.
4. Report negative rejection rate, false-answer rate, positive abstention rate,
   Recall@K and MRR together. Do not promote a threshold that improves refusal
   while silently destroying positive recall.
5. Only after the fixed holdout passes, add one bounded relatedness property at
   the retrieval/answer admission boundary and keep scores described as
   relatedness, never trust probability.

## 3. Qdrant batched-upsert failure diagnosis

The disposable-Collection matrix is complete. Fresh/immediate and settled
Collections both accepted 19 points as one batch and as batch size 1; 20 more
single-batch attempts also passed. The container did not restart or OOM, and a
6.5-second stalled request body still returned HTTP 200. The hypothesized
five-second request-body timeout is therefore falsified for the current stack.

The two latest startup failures are directly explained by port 6333 connection
refusal before Qdrant was running. The historical rebuild disconnect cannot be
reproduced and remains `UNCONFIRMED_TRANSIENT_TRANSPORT`; batch size 1 is a
recovery workaround, not a proven root-cause fix. Full commands, timings,
cleanup, and evidence boundaries are in `qdrant-transport-investigation-v0.2.md`.

## 4. maxEvidence=5 data boundary

The active index has only four distinct ABSTRACT papers. Because retrieval
deduplicates by paper, two more distinct current VERIFIED papers with normalized
DOIs and non-empty, thematically retrievable abstracts are the minimum data
addition needed to observe six admitted ABSTRACT evidence items and prove that
generation truncates to five. Exact data and assertion requirements are in
`max-evidence-boundary-data-plan-v0.1.md`.
