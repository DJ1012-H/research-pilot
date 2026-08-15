# Qdrant transport investigation v0.2

## Findings

Two startup failures at 2026-08-13 00:44 and 00:51 Asia/Shanghai are fully
explained by `Connection refused` on the first Collection `GET`: Qdrant was not
listening on port 6333. After the container became healthy, the application and
read-only retrieval probes succeeded.

The earlier Collection-rebuild failure remains a different incident. Its Java
stack ended while writing a 19-point request body and received no Qdrant HTTP
response. A batch-size-1 retry succeeded, but that retry also reused the
already-created Collection and payload indexes. Historical evidence therefore
supports a one-time transport disconnect; it does not prove a batch-size defect.

## Disposable-Collection diagnostic

`QdrantLiveBatchDiagnosticTest` is disabled by default and runs only with
`RUN_QDRANT_LIVE_DIAGNOSTIC=true`. It uses the real adapter, 1,024-dimensional
vectors, 19 synthetic points, production-sized text, unique disposable
Collection names, and cleanup in `finally`.

The 2026-08-13 Asia/Shanghai run produced:

| Scenario | Result | Upsert time |
| --- | --- | ---: |
| fresh/immediate, one 19-point batch | PASS | 55 ms |
| fresh/immediate, batch size 1 | PASS | 123 ms |
| Collection settled 6 s, one 19-point batch | PASS | 39 ms |
| Collection settled 6 s, batch size 1 | PASS | 118 ms |
| 20 repeated 19-point single-batch upserts | PASS | 12.55 ms average, 20 ms max |

A separate request-body probe wrote one byte, paused for 6.5 seconds, then
completed the body. Qdrant returned HTTP 200. This directly falsifies the
earlier hypothesis that Qdrant's reported 5-second client timeout necessarily
closed a slowly streamed request body.

During the diagnostic the container remained healthy with no restart or OOM,
all disposable Collections were deleted, and only
`research_pilot_paper_segments_v1` remained.

## Conclusion

- Current Qdrant batched transport: `PASS` for the measured 19-point workload.
- Startup `Qdrant transport failed`: confirmed operational dependency-order
  failure when Qdrant is not listening.
- Historical larger-request disconnect: `UNCONFIRMED_TRANSIENT_TRANSPORT`.
- Batch size 1: proven recovery workaround for the historical incident, not a
  required steady-state setting based on current evidence.

Do not claim a permanent root cause without a future failure captured with
simultaneous Java exception, container state, and Qdrant server logs.
