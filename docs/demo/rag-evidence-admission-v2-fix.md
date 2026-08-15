# RAG evidence admission v2 reliability fix

Date: 2026-08-14 Asia/Shanghai

Status: `LIVE_TARGETED_BURNED_CASE_SUCCESS_HISTORICAL_REGRESSION_FAIL`

The frozen `rag-retrieval-v3-lite` holdout remains `FAIL`. This change repairs
the observed structured-output reliability defect; it does not alter, rerun,
or relabel that holdout.

## Observed defect

Three of twelve positive cases across the Day 3 tuning and fixed-holdout splits
stopped after one relevance-Judge call with
`RAG_EVIDENCE_ADMISSION_INVALID`. Retrieval had already returned a reviewed
relevant DOI within the top five for every positive case. Negative refusal and
provider availability were not the failing boundaries.

## Implemented change

- The relevance Judge now creates a LangChain4j `ChatRequest` with provider
  `ResponseFormat.JSON`, `temperature=0`, and at most 2,000 output tokens. The
  larger bound leaves room for providers that count hidden reasoning against
  the completion budget while keeping the expected JSON contract small.
- The versioned prompt is now `rag-evidence-admission-v2`. It retains the full
  JSON Schema and untrusted-data rules and adds exact relevant/rejection JSON
  examples, an exact three-key contract, and a one-sentence reason bound.
- DeepSeek supports `response_format={"type":"json_object"}` but not the
  application's complete business contract. Java still validates syntax,
  closed JSON Schema, strict DTO mapping, relevant/empty consistency, current
  request evidence ownership, and unique allowed IDs.
- Empty JSON-mode content is classified as `EMPTY_RESPONSE` and surfaced to
  the RAG diagnostic as `RAG_ADMISSION_MODEL_EMPTY_RESPONSE`; every invalid
  state still fails closed. There is
  no Markdown stripping, substring extraction, fallback parser, second Judge
  call, or Judge repair.
- Admission failures now expose a low-cardinality `failureDetailCode`, such as
  `RAG_ADMISSION_JSON_INVALID` or `RAG_ADMISSION_SCHEMA_INVALID`. Logs and the
  public diagnostic never contain the model output, prompt, question, abstract,
  or provider exception message.

Official behavior references:

- DeepSeek JSON Output:
  <https://api-docs.deepseek.com/guides/json_mode/>
- LangChain4j structured outputs:
  <https://docs.langchain4j.dev/tutorials/structured-outputs/>

## Acceptance boundary

Offline tests prove request construction, token bounds, prompt versioning,
strict validation, one-call behavior, safe subcodes, public serialization, and
failure closure. A restarted application must still run a small burned-case
diagnostic before this fix is described as live-verified.

The diagnostic may reuse the three already revealed failing cases only as
development regressions. It cannot change the historical holdout result or
serve as a new independent acceptance set. A future PASS requires a separately
frozen holdout that was not used to design this v2 fix.

`scripts/run-rag-admission-v2-regression.ps1` hard-codes those three case IDs,
requires an explicit real-model-cost switch, refuses to overwrite evidence,
retains no question or answer text, and labels its output as having no
acceptance authority.

## First restarted probe

The first restarted probe on 2026-08-14 loaded the v2 response contract and
made one real relevance-Judge call. Provider transport and authentication
succeeded, but the provider returned no assistant text under the original
256-token bound; the answer model was not called. This observation motivated
the 1,024-token bound and explicit empty-response classification above. The
application must be restarted again before that second change can be tested.

## Second restarted burned-case regression

After explicit authorization, the dedicated Runner executed exactly the three
revealed cases on the restarted application. Evidence admission succeeded in
all three cases, so the v2 change repaired the observed admission boundary:

- `rag-v3l-0003`: full request `SUCCESS`; Judge=1, Answer=1, repair=0;
  admitted/generated evidence=3/3.
- `rag-v3l-0013`: full request `SUCCESS`; Judge=1, Answer=1, repair=0;
  admitted/generated evidence=2/2.
- `rag-v3l-0018`: admission succeeded with 2/2 evidence, but the initial
  answer and its single allowed repair both failed answer validation;
  `RAG_ANSWER_VALIDATION_FAILED`, Judge=1, Answer=2, repair=1.

The three case-level HTTP requests therefore produced seven provider calls,
not three provider calls: three Judge calls, three initial Answer calls, and
one Answer repair. Provider-reported usage totaled 15,310 input tokens and
7,217 output tokens (22,527 total). No further call was made.

The retained result is
`eval/rag-retrieval-v3-lite/runs/day3/admission-v2-regression-20260814-v0.1.json`
in the eval worktree, with SHA-256
`7eed7b1bfa48f2886aaf770ee592550ed45d2f62fd62cf42a9743b489aafd4ca`.
It stores no question or answer text. Its status is `FAIL`, its acceptance
authority is `NONE_BURNED_CASES`, and the historical holdout remains
`FAIL_UNCHANGED`. The current downstream blocker is answer-output validation,
not evidence admission or retrieval.

## Targeted 2,000-token observation

On 2026-08-15, a separately authorized targeted probe reused only the already
burned `rag-v3l-0018` case. With a 1,024-token Judge ceiling, the provider again
returned no assistant text, producing
`RAG_ADMISSION_MODEL_EMPTY_RESPONSE`; no answer call occurred. This confirmed
the empty response before changing the ceiling.

The Judge ceiling was then raised to 2,000 without changing the prompt,
schema, validator, retry policy, retrieval parameters, or answer path. After an
application restart, the same burned case completed successfully in one Judge
call and one Answer call, with no repair: admitted/generated evidence=2/2,
answer length=564, and both expected 2021 DOIs cited. Provider-reported usage
was 2,442 input plus 320 output tokens for the Judge and 1,646 input plus 799
output tokens for the Answer (5,207 total). Full `clean verify` passed 550
tests, with zero failures, zero errors, and seven skipped tests.

This single reused case has no acceptance authority and does not replace the
three-case `FAIL` result or the historical fixed-holdout `FAIL`. The redacted
observation is retained as
`eval/rag-retrieval-v3-lite/runs/day3/admission-v2-rag-v3l-0018-2000-v0.1.json`.
