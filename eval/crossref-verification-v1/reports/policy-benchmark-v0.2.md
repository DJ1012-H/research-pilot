# Crossref Policy Benchmark v0.1

- Dataset: `crossref-verification-v1`
- Production baseline: `eac7f34a4565eabb0f7e1f8d8826f7458ecdf2e1`
- Result: **FAIL**
- Status matches: 4/14
- Frozen acceptance matches: 2/4
- False VERIFIED: 1
- False formal admission: 1
- False formal exclusion: 9
- Exceptions: 0

## Failure reasons

- `OVERALL_STATUS_MATCH_COUNT_4_REQUIRED_14`
- `ACCEPTANCE_STATUS_MATCH_COUNT_2_REQUIRED_4`
- `FALSE_VERIFIED_COUNT_1_MAX_0`
- `FALSE_FORMAL_ADMISSION_COUNT_1_MAX_0`
- `FALSE_FORMAL_EXCLUSION_COUNT_9_MAX_0`

## Case results

| Case | Split | Expected | Actual | Admitted | False VERIFIED | Error |
|---|---|---|---|---:|---:|---|
|crv1-case-0001|CALIBRATION|VERIFIED|CONFLICTED|false|false||
|crv1-case-0002|ACCEPTANCE|VERIFIED|CONFLICTED|false|false||
|crv1-case-0003|CALIBRATION|VERIFIED|CONFLICTED|false|false||
|crv1-case-0004|CALIBRATION|VERIFIED|CONFLICTED|false|false||
|crv1-case-0005|CALIBRATION|VERIFIED|CONFLICTED|false|false||
|crv1-case-0006|CALIBRATION|VERIFIED|CONFLICTED|false|false||
|crv1-case-0007|CALIBRATION|VERIFIED|CONFLICTED|false|false||
|crv1-case-0008|CALIBRATION|VERIFIED|CONFLICTED|false|false||
|crv1-case-0009|CALIBRATION|VERIFIED|CONFLICTED|false|false||
|crv1-case-0010|ACCEPTANCE|CONFLICTED|CONFLICTED|false|false||
|crv1-case-0011|ACCEPTANCE|CONFLICTED|CONFLICTED|false|false||
|crv1-case-0012|CALIBRATION|VERIFIED|VERIFIED|true|false||
|crv1-case-0013|ACCEPTANCE|PARTIALLY_VERIFIED|VERIFIED|true|true||
|crv1-case-0014|CALIBRATION|VERIFIED|VERIFIED|true|false||
