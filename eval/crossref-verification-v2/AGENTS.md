# Crossref Verification Dataset v2 约束

- v1 冻结案例与历史 FAIL 证据不可修改或覆盖。
- 新案例必须从 `NEEDS_REVIEW` 开始；`expected.policy_status`、`expected.formal_admission` 与字段 oracle 必须为 `null`。
- 未获得人工明确 `APPROVE`、reviewer、reviewed_at 和 review_version 前，不得晋升为正式案例。
- 不得让模型、生产策略输出或在线 API 响应自行决定 Ground Truth。
- 不得虚构 DOI、标题、作者、年份、venue、work type、出版社或来源时间。
- 新来源必须保存真实原始快照、仓库相对路径、URL、采集时间与 SHA-256；在线响应不得覆盖已批准快照。
- v1 acceptance 已被观察，不得作为 v2 调参后的独立 holdout。
- lookup protocol 与 orchestration 案例必须放入独立数据集，不得混入 v2 书目/策略数据集。
- 未测量、缺失、异常、未审核或未冻结结果均不得报告为通过或零错误。
