# RAG retrieval v2 用户审核包 v0.1

## 审核结果

状态：`COMPLETED_ACCEPT_ALL`

用户于 2026-08-13（Asia/Shanghai）回复：`RAG v2 审核：全部 ACCEPT`。
12 条标签全部接受，没有修改 `relevantPaperIds`，因此 case 哈希和
manifest 中的 cases LF-SHA256 均保持不变。正式机器可读记录见
`user-audit-v0.1.json`。此次是对本审核包的用户审计，不等同于盲态独立
inter-rater 重新标注。

## 你需要审核什么

当前 12 条标签是 2026-08-12 经授权完成的 Codex 审核结果。本文件保留当时提交给用户的审核范围、方法和标签，供后续复核。

审核时只看“查询意图 + 年份条件 + 候选论文的 DOI/标题/摘要”。不要把当前检索排名当成标签，也不要因为某篇论文没有被检索到就把它从相关集合删除。

## 审核方法

1. 先确认查询边界：主题、任务类型、语言和年份范围。
2. 对照下方论文目录。只有能用 DOI、标题或摘要解释“为什么直接相关”的论文才保留。
3. 对空集合，确认 15 篇候选中确实没有匹配项。`rag-v2-0012` 是年份窗口为空；`rag-v2-0007` 是语义无关。
4. 对 `FIXED_ACCEPTANCE` 只做标签审计，不用它来选择阈值或 Top-K。
5. 每条回复 `ACCEPT`，或给出 `REVISE [ID...]` 和一句原因。若空集合不成立，也用 `REVISE [ID...]`。

建议优先细看：`0005`（语义分割与变化检测边界）、`0008`（概念比较型查询）、`0007`（真正的无关查询空集）。

## 当前标签

| Case | Split | 查询与过滤条件 | 当前 relevantPaperIds | 当前理由 |
|---|---|---|---|---|
| rag-v2-0001 | TUNING | 用于遥感影像密集预测的选择性状态空间模型有哪些研究？ | [5,6,7,8,13] | 7 直接覆盖 dense prediction；5/6/8/13 是 Mamba/SSM 变化检测这一密集预测子任务。 |
| rag-v2-0002 | TUNING | Which papers use selective state space models for dense prediction in remote sensing? | [5,6,7,8,13] | 0001 的英文同义查询。 |
| rag-v2-0003 | TUNING | 长上下文序列建模如何用于遥感图像语义分割？ | [7] | 7 明确覆盖遥感大图、长程上下文与 semantic segmentation。 |
| rag-v2-0004 | TUNING | Efficient long-context sequence models for remote-sensing image segmentation | [7] | 0003 的英文对应查询。 |
| rag-v2-0005 | TUNING | 2023 年以后用于遥感语义分割的高效序列模型；fromYear=2023 | [7] | 7 是直接语义分割匹配；6、13 虽为 2024 SSM/Mamba，但任务是变化检测，当前未纳入。 |
| rag-v2-0006 | TUNING | Remote sensing change detection with state space sequence modeling | [5,6,7,8,13] | 五篇均以 Mamba/状态空间模型覆盖遥感变化检测；7 的任务集合包含 change detection。 |
| rag-v2-0007 | TUNING | 量子遥感邮件分类的相关论文检索 | [] | 当前目录没有同时满足量子、遥感、邮件、分类组合意图的论文。 |
| rag-v2-0008 | TUNING | What is the difference between spatial change detection and temporal sequence prediction in remote sensing? | [1,6] | 1 提供 spatial-temporal change detection；6 提供 spatiotemporal state-space modeling。 |
| rag-v2-0009 | FIXED_ACCEPTANCE | 中文问题能否检索到使用选择性状态空间模型进行密集预测的英文论文？ | [5,6,7,8,13] | 固定跨语言检索集合。 |
| rag-v2-0010 | FIXED_ACCEPTANCE | Which papers study selective state space models for dense remote-sensing prediction? | [5,6,7,8,13] | 固定英文检索集合。 |
| rag-v2-0011 | FIXED_ACCEPTANCE | 限定 2023 至 2024 年，哪些论文研究用于密集预测的选择性状态空间模型？；fromYear=2023,toYear=2024 | [6,7,13] | 年份排除 2025 的 5、8；保留 2024 的 6、7、13。 |
| rag-v2-0012 | FIXED_ACCEPTANCE | Return no generated answer when the selected trusted paper window contains no admitted abstract evidence.; fromYear=2099,toYear=2100 | [] | 2099-2100 没有候选论文，应返回无可信结果且不调用模型。 |

## 候选论文目录

| ID | 年份 | DOI | 标题 |
|---:|---:|---|---|
| 1 | 2020 | 10.3390/rs12101662 | A Spatial-Temporal Attention-Based Method and a New Dataset for Remote Sensing Image Change Detection |
| 2 | 2021 | 10.1109/lgrs.2021.3056416 | SNUNet-CD: A Densely Connected Siamese Network for Change Detection of VHR Images |
| 3 | 2009 | 10.1016/j.isprsjprs.2009.06.004 | Object based image analysis for remote sensing |
| 4 | 2021 | 10.1109/tnnls.2021.3084827 | A Survey of Convolutional Neural Networks: Analysis, Applications, and Prospects |
| 5 | 2025 | 10.1109/tgrs.2025.3545012 | CDMamba: Incorporating Local Clues Into Mamba for Remote Sensing Image Binary Change Detection |
| 6 | 2024 | 10.1109/tgrs.2024.3417253 | ChangeMamba: Remote Sensing Change Detection With Spatiotemporal State Space Model |
| 7 | 2024 | 10.1109/tgrs.2024.3425540 | RS-Mamba for Large Remote Sensing Image Dense Prediction |
| 8 | 2025 | 10.1109/lgrs.2025.3551754 | Frequency-Enhanced Mamba for Remote Sensing Change Detection |
| 9 | 2021 | 10.1109/tgrs.2021.3085870 | A Deeply Supervised Attention Metric-Based Network and an Open Aerial Image Dataset for Remote Sensing Change Detection |
| 10 | 2021 | 10.1109/tgrs.2021.3095166 | Remote Sensing Image Change Detection With Transformers |
| 11 | 2023 | 10.1109/tgrs.2023.3241436 | Lightweight Remote Sensing Change Detection With Progressive Feature Aggregation and Supervised Attention |
| 12 | 2024 | 10.1016/j.isprsjprs.2024.01.004 | ChangeCLIP: Remote sensing change detection with multimodal vision-language representation learning |
| 13 | 2024 | 10.3390/rs16224186 | DC-Mamba: A Novel Network for Enhanced Remote Sensing Change Detection in Difficult Cases |
| 14 | 2021 | 10.3390/rs13030516 | Vision Transformers for Remote Sensing Image Classification |
| 15 | 2022 | 10.3390/rs14040871 | Deep Learning-Based Change Detection in Remote Sensing Images: A Review |

## 回复模板

如果全部同意，回复：

`RAG v2 审核：全部 ACCEPT。`

如需修改，只列修改项，其余视为 `ACCEPT`：

```text
RAG v2 审核：
- rag-v2-0005: REVISE [7,6,13]；原因：……
- rag-v2-0008: REVISE [1]；原因：……
- rag-v2-0007: ACCEPT_EMPTY
```

如果只审核了一部分，请明确写 `PARTIAL`，未列出的 case 会继续保持 `PENDING_USER_AUDIT`。
