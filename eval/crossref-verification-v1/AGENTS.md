# Crossref Verification Dataset v1 约束

- 不得为了让测试通过而修改 Ground Truth。
- 不得让模型自行决定 Ground Truth。
- 不得虚构 DOI、标题、作者、年份、期刊或出版社。
- 不得修改人工审核过的冻结数据。
- 不得以在线响应覆盖已批准的离线快照。
- 不得创建没有 `parent_case_id` 的 mutation。
- 不得将 protocol 或 orchestration 案例放入本数据集。
- 不得修改生产业务状态枚举以加入 `NEEDS_REVIEW`。
- 不得复制 `src/test/resources/openalex/works-response.json`；必须通过 provenance 引用它。
- 不得修改 v1 冻结案例中的 `expected`、`provenance.review` 或 lineage；新语义必须版本化。
- v1 的 `formal_result_eligible` 仅作为历史诊断字段；当前正式准入评测必须使用版本化 manifest 中声明的 oracle。
- policy benchmark 必须固定生产策略源码哈希和阈值；缺失、异常、未分配 split 或指标未测量均不得判为通过。
- policy benchmark 的已知 FAIL 是历史证据，不得通过修改测试期望、删除案例或改动 split 来掩盖。
