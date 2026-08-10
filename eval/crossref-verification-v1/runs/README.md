# Policy benchmark runs

`scripts/run-crossref-policy-evaluation.ps1` generates deterministic JSON and Markdown under:

```text
target/evaluation/crossref-verification-v1/
```

The generated JSON contains `acceptance_passed`, fail-closed metric reasons, and one record per reviewed case. The script returns a non-zero exit code whenever acceptance is false, an exception occurs, or required metrics are not satisfied.

Do not copy generated results over reviewed JSONL cases or approved provider snapshots. When intentionally freezing a new production baseline, add a new versioned manifest and report; preserve earlier FAIL evidence.
