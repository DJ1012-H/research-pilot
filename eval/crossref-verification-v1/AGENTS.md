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
