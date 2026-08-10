# Crossref Policy Benchmark v0.1

## Scope and frozen inputs

This report evaluates the 14 reviewed `crossref-verification-v1` cases against the production policy and formal-output gate pinned in `manifests/policy-benchmark-v0.1.json`. The baseline commit is `9770c046ede5758b443dd2ca1af375b57fd88154`.

The run is offline and deterministic. It calls no Crossref/OpenAlex endpoint, model, database, Redis instance, or other external service. Existing case labels, review records, source snapshots, calibration split, and Ground Truth remain unchanged.

The legacy v1 `formal_result_eligible` field is diagnostic only because its historical semantics predate the current production gate. For this benchmark, expected formal admission is derived from the reviewed status being `VERIFIED` and the reviewed reference DOI normalizing successfully.

## Result: FAIL

- Evaluated reviewed cases: 14/14.
- Policy-status exact matches: 4/14.
- Calibration status matches: 2/10.
- Frozen acceptance status matches: 2/4.
- Formal-admission matches: 4/14.
- False `VERIFIED`: 1.
- False formal admission: 1.
- False formal exclusion: 9.
- Exceptions: 0.

Acceptance fails closed because exact-match requirements were not met and all three safety/completeness error counts are above zero.

## Preserved failure records

| Cases | Reviewed expectation | Production result | Reason | Impact |
|---|---|---|---|---|
| `0001`–`0009` | `VERIFIED` | `CONFLICTED` | `HARD_FIELD_CONFLICT_AUTHORS` | 9 false formal exclusions |
| `0013` | `PARTIALLY_VERIFIED` | `VERIFIED` | `DOI_EXACT_MATCH_NO_HARD_CONFLICT` | 1 false `VERIFIED`; 1 false formal admission |

Cases `0010` and `0011` correctly remain `CONFLICTED`; cases `0012` and `0014` correctly remain `VERIFIED`.

The `0001`–`0009` outcome does not by itself prove that either the production policy or the reviewed status is wrong. Those cases contain a reviewed first-author oracle but no reviewed full-author-set oracle. A human review must decide whether a provider subset should be `MATCH`, `EXPLAINABLE_DIFFERENCE`, or `MISMATCH` before any policy or label change.

Case `0013` likewise remains a decision record: the reviewed online-first year difference expects `PARTIALLY_VERIFIED`, while the current DOI-first policy treats an explainable year difference as non-conflicting and returns `VERIFIED`. Do not tune against the frozen acceptance case or rewrite it to make this report pass.

## Reproduction

```powershell
.\scripts\run-crossref-policy-evaluation.ps1
```

Generated machine-readable and Markdown outputs are written to `target/evaluation/crossref-verification-v1/`. The script exits non-zero while `acceptance_passed=false`.
