# Crossref Verification Dataset v2 Review Queue

## Current state

- Formal cases: 0.
- Reviewed cases: 0.
- Unreviewed intake cases: 20.
- Review authorization: recorded for `intake-v0.1`.
- Human case decisions: 0/20; reviewer not yet assigned.
- Independent holdout: not frozen.
- Acceptance state: `UNMEASURED`.
- Real API evidence: 20 OpenAlex/Crossref snapshot pairs plus the frozen OpenAlex selection response in `fixtures/intake-v0.1`.

The `intake-v0.1` batch was acquired on 2026-08-10 using a deterministic OpenAlex sample seed and independent Crossref DOI lookups. It contains 20 distinct primary sources, 14 OpenAlex topic fields, publication years from 1977 through 2025, and four observed language states (`en`, `it`, `pt`, and missing). These are intake-diversity observations, not reviewed coverage or acceptance results.

The immutable acquisition record is `manifests/intake-batch-v0.1.json`; its 20 draft records are in `draft/review-queue-v0.1.jsonl`. Every record remains `NEEDS_REVIEW`, every expected label and field oracle is `null`, and `provenance.review` is `null`.

The explicit task authorization `APPROVE review intake-v0.1` is recorded in `review/intake-v0.1/review-session-v0.1.json`. The generated JSONL review packet and Markdown guide only unfold observed OpenAlex/Crossref fields. They intentionally keep the reviewer, review timestamp, every field oracle, policy status, formal-admission decision, and rationale empty. Authorization to begin review is not case-level Ground Truth approval.

This directory is the versioned intake boundary for new independent bibliographic and policy cases. It does not replace or rewrite `crossref-verification-v1`.

## Required workflow

1. Acquire genuine OpenAlex/Crossref raw snapshots with explicit authorization.
2. Record repository-relative paths, source URLs, retrieval timestamps, and SHA-256 provenance.
3. Add draft records with `review_state=NEEDS_REVIEW`; every expected status and field oracle remains `null`.
4. Obtain explicit human `APPROVE` and record reviewer metadata.
5. Freeze calibration and a new independent holdout before policy/threshold execution.
6. Run the holdout once for a formal attempt; preserve failures and never tune on that holdout.

The first review wave must prioritize independent source pairs and the gaps in `manifests/coverage-plan-v0.1.json`, especially full-author-set judgments, no-DOI unique/ambiguous matches, missing fields, venue/work-type conflicts, and multilingual identity forms. Coverage targets are engineering planning inputs, not statistical representativeness claims or measured results.

`schema/review-queue-case.schema.json` intentionally permits only `NEEDS_REVIEW` records. Promotion to reviewed/frozen data requires a separate reviewed-case schema and explicit human approval; that schema is not created until the first review is authorized.

## Reproducible acquisition boundary

The intake script requires an explicit network confirmation and refuses to overwrite any existing batch output:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\acquire-crossref-v2-intake.ps1 -ConfirmPublicApiCalls
```

The execution-policy override applies only to that PowerShell process. The script uses anonymous read-only public API requests, sequential throttling, bounded retries, a fixed sample seed, unique primary-source selection, and a maximum of three cases per OpenAlex topic field. Public API authentication and limits can change, so future batches must re-check the providers' current official documentation before acquisition.

## Human review preparation

Review preparation is offline, verifies the immutable intake hashes, and refuses to overwrite an existing review session:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-crossref-v2-review.ps1 -ConfirmReviewAuthorization -ApproverId <approver-id>
```

The reviewer must separately provide an identity and case-level judgments. Each field judgment must be one of `MATCHED`, `EXPLAINABLE_DIFFERENCE`, `MISMATCHED`, or `UNKNOWN`, with a human rationale. `UNKNOWN`, missing decisions, and partial review remain fail closed and cannot enter a frozen split.
