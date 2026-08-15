# RAG v3-lite Day 3 evaluation

Date: 2026-08-14 Asia/Shanghai

Final status: `FIXED_HOLDOUT_FAIL`

This document records the interview-readiness Day 3 evaluation. It is an
authorized real-service observation over MySQL, Redis, Ollama, Qdrant, and the
configured DeepSeek model. It is not a production benchmark, SLA, independent
Ground Truth result, or permission to rerun the fixed holdout.

## Evaluation boundary

- Dataset: `rag-retrieval-v3-lite`
- Review state: `USER_AUDITED_CODEX_REVIEWED`
- Candidate catalog: 18 verified DOI papers, 9 with eligible abstracts
- Cases: 24 total, split before evaluation into 12 tuning and 12 fixed holdout
- Each split: 6 positive, 5 semantic-negative, and 1 deterministic empty-year
  control; 6 Chinese and 6 English questions
- Runtime parameter: `TopK=5`
- Vector score threshold: not used because the small corpus does not calibrate
  a trustworthy scalar threshold
- Evidence-admission prompt: `rag-evidence-admission-v1`
- Answers are not retained. Evidence stores only answer length and UTF-8
  SHA-256, public DOI citations, retrieval scores, statuses, and bounded counts.

The repository owner explicitly authorized sending the 24 questions and the
retrieved public paper evidence to DeepSeek and accepted the real-call cost.

## Frozen acceptance decision

The tuning result was inspected before the fixed holdout. The parameter
decision then froze the following minimums:

| Metric | Minimum |
|---|---:|
| Case outcome accuracy | 0.8333 |
| Positive answer success | 0.8333 |
| Positive evidence hit | 0.8333 |
| Positive citation precision | 0.9000 |
| Positive retrieval Hit@5 | 0.8333 |
| Semantic-negative refusal | 0.8000 |
| Deterministic empty refusal | 1.0000 |

The fixed holdout also required zero failed responses and zero infrastructure
failures. Its output directory is fixed at `fixed-holdout-v0.1` and the Runner
refuses to overwrite it.

## Measured results

| Metric | Tuning | Fixed holdout |
|---|---:|---:|
| Cases matching expected outcome | 11/12 (0.9167) | 10/12 (0.8333) |
| Positive answer success | 5/6 (0.8333) | 4/6 (0.6667) |
| Positive evidence hit | 5/6 (0.8333) | 4/6 (0.6667) |
| Positive citation precision | 1.0000 | 1.0000 |
| Retrieval Recall@1 | 0.5000 | 0.8333 |
| Retrieval Recall@3 | 0.7917 | 1.0000 |
| Retrieval Recall@5 | 0.9583 | 1.0000 |
| Retrieval Hit@5 | 1.0000 | 1.0000 |
| Retrieval MRR | 0.9167 | 1.0000 |
| Negative refusal | 6/6 (1.0000) | 6/6 (1.0000) |
| Semantic-negative refusal | 5/5 (1.0000) | 5/5 (1.0000) |
| Deterministic empty refusal | 1/1 (1.0000) | 1/1 (1.0000) |
| Failed responses | 1 | 2 |
| Infrastructure failures | not separately classified | 0 |
| Model-contract failures | not separately classified | 2 |

The fixed holdout failed three frozen checks: positive answer success, positive
evidence hit, and zero failed responses. It passed case outcome accuracy only
at the exact minimum and passed every retrieval, citation precision, refusal,
and infrastructure check.

## Failure diagnosis

The two failed holdout cases were `rag-v3l-0013` and `rag-v3l-0018`. Both had
successful retrieval with every reviewed relevant DOI present in the top
three. Both stopped after one relevance-Judge call with
`RAG_EVIDENCE_ADMISSION_INVALID`, zero admitted evidence, zero answer calls,
and no published answer or citation.

The tuning set had the same failure class once on `rag-v3l-0003`. Across the
24 cases, retrieval found a relevant DOI within the top five for every positive
case and all 12 negatives were refused safely. The observed bottleneck is
therefore structured relevance-Judge output reliability, not missing labels,
vector recall, MySQL re-admission, or provider availability.

Failing closed is the correct safety behavior, but a 3/12 positive-case model
contract failure rate across both splits is not acceptable answer-path
reliability. Day 3 must remain `FAIL`; 83.3% overall holdout accuracy must not be
presented as a pass.

## Evidence integrity

- Dataset manifest SHA-256:
  `70fff7771baf6c4335bef3159fc953cc95240fd0075ab31b605c635ec4136bae`
- Tuning report SHA-256:
  `e4c47aaae5ef1f029be231e5f44c4f6cc6a6b046479b79c007bede6f53c2e143`
- Tuning observations SHA-256:
  `72cd8275cfaad4ebba11eb4eba1262e4727af006a0023c36c4d8e5178e1f601e`
- Frozen parameter decision SHA-256:
  `185bb4b9c5348fce37fb7507c3edaa5f80d4f8f26f5eb27a14c30fe625528ccc`
- Fixed-holdout report SHA-256:
  `0e44b3847cf32740f18986fffab8f7c95065d542fc1db36b147beb710e5be30e`
- Fixed-holdout observations SHA-256:
  `f8bbea33161c0ffa0bd3b643a0fefdeb1a0275d5817975dfb16e65ddd68b46e4`

The main application commit was `c64bc59`. The tuning and holdout Runner hashes
differ because failure metrics were renamed and split into failed-response,
infrastructure, and model-contract counts after the tuning diagnosis. Request
construction, endpoint behavior, `TopK`, model prompts, labels, and frozen
acceptance thresholds did not change. This procedural difference is retained
as a limitation rather than erased.

## Required remediation

Do not rerun this fixed holdout. A later attempt should:

1. expose a safe low-cardinality admission validation subcode in logs;
2. improve the structured-output contract, preferably through provider-native
   JSON Schema support when the configured client/provider can prove it;
3. otherwise test a versioned prompt with exact valid examples and a tighter
   bounded reason instruction on tuning data only;
4. freeze a new parameter decision and a new independent holdout rather than
   reusing `rag-v3-lite` fixed cases.

The resume-safe claim from this result is: ResearchPilot measured perfect
top-five positive retrieval and negative refusal on a small author-audited
holdout, while its strict fail-closed Judge contract exposed a positive-answer
reliability defect. It is not accurate to claim that Day 3 acceptance passed.

## Subsequent repair status

Admission v2 was implemented after this failed holdout using provider JSON
mode, deterministic bounded output options, exact JSON examples, and safe
validation subcodes. See `rag-evidence-admission-v2-fix.md`. This does not
change the Day 3 result; a new independent holdout is required for any future
acceptance claim.
