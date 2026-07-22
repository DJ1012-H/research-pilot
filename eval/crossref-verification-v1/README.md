# Crossref Verification Dataset v1

## 目标与边界

本数据集为离线、确定性的书目字段匹配评测做准备，唯一评测对象为：

```text
CandidatePaper + CrossrefWorkMetadata -> VerificationResult
```

它不评测 HTTP 状态码、Retry-After、429/5xx/超时、重试、查询预算、候选顺序、网络可用性或编排停止策略。这些 protocol 场景继续由现有 JUnit 测试覆盖；未来如有需要，应分别建立 `crossref-lookup-protocol-v1` 与独立的 orchestration 评测集。本目录不创建这两个未来数据集。

当前尚未实现 Benchmark Runner 或字段级核验器。本目录也不会调用在线 Crossref API。

## 数据与审核状态

`expected.review_state` 是数据集审核流程状态，`expected.verification_status` 是生产模型兼容的核验输出状态；两者不能混用，也不向生产 `VerificationStatus` 增加 `NEEDS_REVIEW`。`NOT_FOUND` 和 `SOURCE_UNAVAILABLE` 属于 lookup/protocol 层，未来由独立的 `crossref-lookup-protocol-v1` 评测；本任务不创建该数据集。

- `NEEDS_REVIEW`：尚无人工确认的字段级真值。`verification_status` 与 `formal_result_eligible` 必须为 `null`，`provenance.review` 必须为 `null`；`evidence_score_range` 可以为 `null`，不得为未审核案例虚构分数范围。
- `REVIEWED`：人工审核完成。必须提供非空核验状态、正式输出资格、理由及审核人、审核时间、审核版本。

`draft/seed-cases.jsonl` 是唯一人工维护的草案来源。当前它为空：仓库已有 OpenAlex 候选 Fixture，但没有同一论文的、已批准 Crossref 字段快照，不能据此虚构 candidate/reference 对。未来的工具应从 seed 中筛出 `NEEDS_REVIEW` 记录，生成 `generated/review-queue.jsonl`；不得手工维护 `draft/review-queue.jsonl`。

## 目录职责

- `schema/`：Draft 2020-12 数据、provenance 与 mutation 结构契约。
- `draft/`：待人工审核的原始案例；不等于冻结测试集。
- `fixtures/approved-references/`：经人工审核、可复核的离线 Crossref 快照；当前为空。
- `mutations/`：可追溯 mutation 规则，不批量生成案例。
- `manifests/`：已有 Fixture 的 provenance 与 mutation 文件清单。
- `generated/`、`reports/`、`runs/`：未来工具运行产物；仅保留目录占位。

## Provenance、SHA-256 与 lineage

案例级 `provenance` 固定包含 `candidate_source_id`、`reference_source_id` 与 `review`。两个 source ID 必须能解析到 `manifests/source-provenance.json` 的 `sources[].source_id`；审核信息只放在案例级 `review`，不放在来源记录。来源记录继续保存 `origin_type`、`source_path`、`source_url`、`retrieved_at` 与 `source_sha256`。

引用仓库 Fixture 时，`source_path` 必须是仓库相对路径，且 `source_sha256` 必须与实际文件内容相同；不要复制原始 Fixture。`source_sha256` 属于来源 provenance，不属于 `reference` 元数据。在线响应不得直接覆盖已批准的离线快照。真正保存并批准 Crossref snapshot 前，不存在可供 `reference_source_id` 使用的来源是正常状态，因此当前 seed 继续为空。

`case_id` 使用 `crv1-case-0001` 格式，在 NEEDS_REVIEW、REVIEWED、mutation 与未来冻结测试集间保持稳定；案例生命周期不得通过修改 case_id 表达，审核状态仅由 `expected.review_state` 表达。

每个案例都要声明 lineage。基例固定为 `is_mutation=false`，三个 mutation 字段均为 `null`；mutation 必须给出可追溯的 `parent_case_id`、`mutation_id` 和 `mutation_version`。不得创建没有父案例的 mutation。

冻结测试集只应在人工审核完成、来源快照固定、规则与审核版本确定后，从 REVIEWED 草案中派生；不要现在冻结 `verification-test.jsonl`。
