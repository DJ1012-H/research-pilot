# RAG evidence admission Day 2 observation

Date: 2026-08-14 Asia/Shanghai

Evidence label: `REAL NETWORK / LIVE LOCAL SERVICES`

This is one authorized functional observation over the current small corpus.
It is not a benchmark, SLA, refusal-rate measurement, or holdout result.

## Runtime boundary

- MySQL: `UP`, trust authority and re-admission source
- Redis: `UP`
- Ollama embedding: `UP`, 1,024 dimensions
- Qdrant: `UP`, Collection `research_pilot_paper_segments_v1`
- Collection snapshot: 27 verified points, 18 metadata points, 9 abstract
  points from 9 papers
- Configured model: `deepseek-v4-flash` through the shared `ModelInvoker`

## Related-query path

Question: `Which papers study selective state space models for dense prediction?`

- Request ID: `98c1cc63-b4a5-49b8-ac5b-92ed383a10cc`
- HTTP/business status: `200 / SUCCESS`
- MySQL-re-admitted ABSTRACT evidence: 5
- Relevance-admitted evidence: 3
- Generation evidence: 3
- `relevanceJudgeCallCount`: 1
- `answerModelCallCount`: 1
- Total `modelCallCount`: 2
- Repair count: 0
- Elapsed time: 11,205 ms reported by the verifier
- Judge token observation: 2,704 input, 430 output, 3,134 total
- Answer token observation: 2,219 input, 506 output, 2,725 total

The public answer contained only Java-owned citation metadata associated with
the relevance-admitted request evidence.

## Semantic-negative path

Question: `Which state-space models predict protein folding structures?`

- Request ID: `3ea24f16-7413-4463-8d6d-e3f989b971ce`
- HTTP/business status: `200 / INSUFFICIENT_EVIDENCE`
- `relevanceJudgeCallCount`: 1
- `answerModelCallCount`: 0
- Total `modelCallCount`: 1
- Relevance-admitted evidence: 0
- Generation evidence: 0
- Answer text and citations: empty
- Judge token observation: 2,772 input, 101 output, 2,873 total
- HTTP elapsed time: 2,375 ms

The shared model log contains no `rag_answer` operation for this request,
matching the public zero answer-call diagnostic.

## Deterministic no-candidate control

A request restricted to a nonexistent paper ID returned
`INSUFFICIENT_EVIDENCE` with zero Judge calls, zero answer calls, zero admitted
evidence, and zero generation evidence. This control remains distinct from the
semantic-negative path, which requires one bounded relevance judgment.

## Conclusion and limit

The Day 2 functional gate passed: a related query reached answer generation
only after a Java-validated evidence subset, while one reviewed semantic
negative stopped before answer generation. Refusal quality across the frozen
v3-lite dataset remains `UNMEASURED` until the later evaluation day runs the
tuning and fixed-holdout protocol.
