# Crossref Verification Dataset v1

## 目标与边界

本数据集为离线、确定性的书目字段匹配评测做准备，唯一评测对象为：

```text
CandidatePaper + CrossrefWorkMetadata -> VerificationResult
```

它不评测 HTTP 状态码、Retry-After、429/5xx/超时、重试、查询预算、候选顺序、网络可用性或编排停止策略。这些 protocol 场景继续由现有 JUnit 测试覆盖；未来如有需要，应分别建立 `crossref-lookup-protocol-v1` 与独立的 orchestration 评测集。本目录不创建这两个未来数据集。

当前已有字段级校准测试和离线论文级 policy benchmark。入口为
`scripts/run-crossref-policy-evaluation.ps1`，输出写入 `target/evaluation/`；它不会调用在线 Crossref API、OpenAlex API 或模型。

## 数据与审核状态

`expected.review_state` 是数据集审核流程状态，`expected.verification_status` 是生产模型兼容的核验输出状态；两者不能混用，也不向生产 `VerificationStatus` 增加 `NEEDS_REVIEW`。`NOT_FOUND` 和 `SOURCE_UNAVAILABLE` 属于 lookup/protocol 层，未来由独立的 `crossref-lookup-protocol-v1` 评测；本任务不创建该数据集。

- `NEEDS_REVIEW`：尚无人工确认的字段级真值。`verification_status` 与 `formal_result_eligible` 必须为 `null`，`provenance.review` 必须为 `null`；`evidence_score_range` 可以为 `null`，不得为未审核案例虚构分数范围。
- `REVIEWED`：人工审核完成。必须提供非空核验状态、正式输出资格、理由及审核人、审核时间、审核版本。

### v1 当前数据集统计（2026-07-25）

- 总案例数：14；Ground Truth：2；mutation：12。
- 案例级主要结果：`MATCHED` 11、`EXPLAINABLE_DIFFERENCE` 1、`MISMATCHED` 2。
- verification status：`VERIFIED` 11、`PARTIALLY_VERIFIED` 1、`CONFLICTED` 2、`REJECTED` 0。
- `formal_result_eligible=true`：14 条。
- 当前 YAML 中声明的 12 种 mutation 均至少有一条正式案例；`ONLINE_FIRST_YEAR` 的整体状态为 `PARTIALLY_VERIFIED`。
- 本数据集是小规模、人工审核、确定性、离线回归测评集，不宣称具有统计学代表性。

`draft/seed-cases.jsonl` 保留首条 Ground Truth；新增的第二条 Ground Truth 与其两个 mutation 位于 `generated/online-first-and-unicode-hyphen-cases.jsonl`，均已完成来源固定和人工审核。当前不保留临时 review queue 文件。

## 目录职责

- `schema/`：Draft 2020-12 数据、provenance 与 mutation 结构契约。
- `draft/`：待人工审核的原始案例；不等于冻结测试集。
- `fixtures/approved-references/`：经人工审核、可复核的离线 Crossref 快照。
- `fixtures/candidates/`：经人工审核、可复核的离线 OpenAlex 原始响应快照。
- `mutations/`：可追溯 mutation 规则，不批量生成案例。
- `manifests/`：已有 Fixture 的 provenance 与 mutation 文件清单。
- `generated/`：已审核的 v1 案例文件。
- `reports/`：人工可读的校准与 policy benchmark 冻结报告。
- `runs/`：运行约定；可重复生成的机器结果默认写入 `target/evaluation/`，不覆盖人工审核数据。

## Provenance、SHA-256 与 lineage

案例级 `provenance` 固定包含 `candidate_source_id`、`reference_source_id` 与 `review`。两个 source ID 必须能解析到 `manifests/source-provenance.json` 的 `sources[].source_id`；审核信息只放在案例级 `review`，不放在来源记录。来源记录继续保存 `origin_type`、`source_path`、`source_url`、`retrieved_at` 与 `source_sha256`。

引用仓库 Fixture 时，`source_path` 必须是仓库相对路径，且 `source_sha256` 必须与实际文件内容相同；不要复制原始 Fixture。`source_sha256` 属于来源 provenance，不属于 `reference` 元数据。在线响应不得直接覆盖已批准的离线快照。当前 0012–0014 使用 OpenAlex 与 Crossref 的原始 UTF-8 快照，并由 manifest 固定采集时间和哈希。

`case_id` 使用 `crv1-case-0001` 格式，在 NEEDS_REVIEW、REVIEWED、mutation 与未来冻结测试集间保持稳定；案例生命周期不得通过修改 case_id 表达，审核状态仅由 `expected.review_state` 表达。

每个案例都要声明 lineage。基例固定为 `is_mutation=false`，三个 mutation 字段均为 `null`；mutation 必须给出可追溯的 `parent_case_id`、`mutation_id` 和 `mutation_version`。不得创建没有父案例的 mutation。

当前 v1 直接使用已审核的 `generated/*.jsonl` 作为离线回归数据，不额外创建 `verification-test.jsonl`。后续若需要冻结派生文件，必须保留来源快照、规则版本和审核版本。

## 当前不评测的范围、限制与 v2 候选

当前不评测实时网络可用性、HTTP 状态码、Retry-After、429/5xx/超时、重试、查询预算、候选排序、网络 orchestration 或停止策略；这些仍由现有协议层测试覆盖。已知限制包括样本量小、人工审核成本高、venue 跨提供方命名差异需要人工判断，以及 `ONLINE_FIRST_YEAR` 依赖当前 print-first 年份策略。

v1 的 `formal_result_eligible` 是早期审核语义，与当前生产“仅 `VERIFIED` 且 DOI 可规范化才正式准入”的门禁不等价。policy benchmark 因此保留该字段作诊断，但不把它当作当前准入 oracle；准入 oracle 在版本化 manifest 中显式声明。`v0.1` 保留 `main@9770c04` 的历史钉住证据，`v0.2` 钉住合并 RAG 版本标识后的当前 `main@eac7f34`；两者不得互相覆盖。

### 版本化 policy benchmark（2026-08-10）

- 历史生产基线：`v0.1` / `main@9770c04`；当前生产基线：`v0.2` / `main@eac7f34`。
- 字段校准历史结果仍为 calibration `48/50`、acceptance `19/20`。
- 论文级状态精确匹配为 `4/14`，冻结 acceptance 为 `2/4`，正式准入匹配为 `4/14`。
- `crv1-case-0001` 至 `0009` 因当前策略把完整作者集合 `AUTHORS` 作为硬冲突而被判为 `CONFLICTED`；v1 只具备第一作者人工 oracle，不能据此自动修改生产策略或 Ground Truth。
- `crv1-case-0013` 的人工状态为 `PARTIALLY_VERIFIED`，当前策略输出 `VERIFIED`，形成 1 次错误 `VERIFIED` 和 1 次错误正式准入。
- 当前验收状态仍为 **FAIL**；`v0.1` 报告保持历史不变，当前结果写入 `policy-benchmark-v0.2` 输出。

新的独立样本、完整作者集合 oracle、缺失 DOI、多参考歧义、缺失字段、venue/work-type 冲突和多语言命名进入
`crossref-verification-v2` 的人工审核流程。lookup protocol 与 orchestration 仍必须使用独立数据集，不能混入本目录。
