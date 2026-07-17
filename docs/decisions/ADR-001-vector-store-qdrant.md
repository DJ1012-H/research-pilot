# ADR-001：使用 Qdrant 作为向量数据库

- 状态：已采纳
- 决策日期：2026-07-16
- 计划实施阶段：第三阶段

## 背景

当前 Redis 是基础 Redis，已经验证可以正常连接，但没有加载 RediSearch/Search 模块，因此不能使用 FT.CREATE、FT.SEARCH 或 KNN 向量检索。

基础 Redis 连接成功不代表具备向量检索能力。

## 决策

系统采用以下存储职责划分：

- MySQL 保存论文、检索任务、核验记录和最终可靠状态。
- Redis 保存缓存、任务进度、限流信息和具有 TTL 的临时状态。
- Qdrant 保存论文文本块向量和用于过滤、引用追溯的元数据。

第一阶段只记录该技术决策。第三阶段实现 Embedding 和向量检索时正式接入 Qdrant。

## 一致性原则

- MySQL 是业务数据的最终事实来源。
- Qdrant 索引可以根据 MySQL 数据重新构建。
- 使用稳定的 paperId、chunkId 和 pointId。
- 重复索引必须幂等，不能产生重复向量。
- 更换 Embedding 模型或维度后创建新的 Collection。
- 只有符合核验策略的论文才能写入 Qdrant。

## 风险

Qdrant 会增加一个部署组件，并带来 MySQL 与向量索引之间的短暂一致性问题。

通过幂等写入、失败重试、稳定标识和索引重建流程降低风险。

## 回退方案

如果 Qdrant 暂时无法部署，系统保留文献检索、核验、MySQL 和 Redis 功能，暂时关闭向量 RAG，不在基础 Redis 上假设 RediSearch 可用。
