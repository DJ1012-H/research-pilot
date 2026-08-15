# Evaluation

ResearchPilot separates deterministic tests, live-service observations,
development regressions, and fixed-holdout evidence. None of these is presented
as a production benchmark or SLA.

## Dataset

The current `rag-retrieval-v3-lite` dataset lives on the separate `eval` branch:

- 18 frozen DOI-identified candidate papers;
- 9 papers with answer-eligible ABSTRACT Segments;
- 24 bilingual cases: 12 tuning and 12 fixed holdout;
- each split contains 6 positive and 6 negative cases;
- labels use stable DOIs and include review provenance;
- dataset, case, and parameter hashes are retained;
- the fixed holdout is not rerun or relabelled after inspection.

The labels are repository-owner-audited Codex review, not independent expert
inter-rater Ground Truth. The corpus is intentionally small and domain-heavy.

## Fixed Day 3 result

The fixed holdout remains `FAIL`.

| Metric | Result |
| --- | ---: |
| Cases matching expected outcome | 10/12 |
| Positive answer success | 4/6 |
| Positive evidence hit | 4/6 |
| Citation precision on successful positives | 1.0 |
| Retrieval Recall@5 | 1.0 |
| Retrieval Hit@5 | 1.0 |
| Retrieval MRR | 1.0 |
| Semantic-negative refusal | 5/5 |
| Deterministic empty refusal | 1/1 |
| Infrastructure failures | 0 |
| Model-contract failures | 2 |

Retrieval found reviewed evidence for every positive case and all six negative
cases were refused. Two positive cases stopped fail-closed after the relevance
Judge, so positive answer success, positive evidence hit, and the zero-failure
requirement did not pass. The result is useful failure evidence, not a green RAG
quality claim.

See the [full Day 3 report](demo/rag-v3-lite-day3-evaluation.md).

## Subsequent development regressions

Admission v2 introduced provider JSON mode, temperature zero, versioned prompt
and schema contracts, bounded output, explicit empty-response classification,
and safe low-cardinality validation diagnostics. It did not weaken Java
validation or add a fallback parser.

The three already revealed cases were reused only as burned development
regressions. Admission succeeded 3/3, while the complete answer chain succeeded
2/3 because one answer and its single repair failed validation. That result is
retained as `FAIL` with `NONE_BURNED_CASES` acceptance authority.

A later targeted run of the same burned case succeeded with a 2,000-token Judge
budget, one Judge call, one Answer call, no repair, two admitted citations, and
5,207 provider-reported tokens. Its authority is `NONE_BURNED_CASE`; it does
not replace the failed fixed holdout or prove independent acceptance.

See the [admission v2 repair record](demo/rag-evidence-admission-v2-fix.md).

## Reproduction boundaries

```powershell
# Deterministic Java verification; no live providers or model are required.
.\mvnw.cmd clean verify

# In the eval worktree, validate the frozen dataset and hashes without network.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\scripts\validate-rag-retrieval-v3-lite.ps1
```

Real evaluation requires an explicitly started application, healthy authorized
dependencies, and a separate cost confirmation. The fixed holdout must not be
rerun. Development regression output must not overwrite existing evidence or
retain question, prompt, answer, or raw provider bodies.
