# ResearchPilot 可信 RAG 与 Qdrant 亮点扩展计划

> 计划日期：2026-08-10 ～ 2026-08-15
>
> 计划状态：待执行，不提前改变 8 月 10 日前的可信检索 Agent 主线
>
> 扩展目标：在已核验论文闭环稳定后，增加“可信准入、可重建索引、可评测检索、可校验引用”的真实 RAG 能力
>
> 推荐里程碑：`v1.1.0-rag-demo`（以前置演示版 `v1.0.0-demo` 已验收为条件）

## 1. 扩展定位

本扩展不是为了简单展示“使用了向量数据库”，而是在 ResearchPilot 已有可信检索链路上增加一个可解释的知识检索层：

```text
中文研究问题
    ↓
可信检索 Agent
    ↓
OpenAlex 候选 + Crossref 跨源核验
    ↓
仅 VERIFIED 论文进入 MySQL
    ↓
可信索引准入与内容投影
    ↓
Ollama 本地多语言 Embedding
    ↓
Qdrant 向量与元数据索引
    ↓
带过滤条件的 Top-K 检索
    ↓
DeepSeek 基于检索证据生成回答
    ↓
Java 校验引用、证据范围和降级结果
```

扩展完成后，项目应能在面试中清楚回答以下问题：

1. 为什么 OpenAlex 候选不能直接进入 RAG。
2. 为什么 MySQL 是事实来源，而 Qdrant 只是可重建索引。
3. 如何保证 Qdrant 中只存在 `VERIFIED` 论文。
4. 如何支持中文问题检索英文论文摘要。
5. 如何防止模型生成不存在的论文引用。
6. 如何评估检索质量，而不是只展示一个看起来合理的回答。
7. Embedding 模型或维度变化时，为什么必须创建新索引版本。

## 2. 前置门禁

### 2.1 进入扩展阶段的必要条件

8 月 10 日只有在以下条件全部满足后，才能开始 RAG 扩展：

- `POST /api/literature/search` 能通过真实模型、OpenAlex 和 Crossref 返回正式论文。
- 正式论文列表只包含 `VERIFIED`，最终论文具有标准化 DOI。
- MySQL 已保存检索任务、论文、核验证据和 Agent 轨迹。
- 同步 Agent 具有总时间、搜索轮次、候选数和外部调用预算。
- 三组核心演示覆盖首次满足、重规划后满足、证据不足。
- 全量自动测试通过。
- 工作区无未提交修改，远程仓库与本地同步。
- 前置演示版已形成可复现提交或标签。

### 2.2 门禁失败时的处理

如果 8 月 10 日前置门禁未通过：

1. 不接入 Qdrant，不创建新的 RAG 业务包。
2. 8 月 10 日优先修复可信检索闭环。
3. 扩展计划整体顺延，不用删减核验、测试和引用校验来追赶日期。
4. 可以提前安装 Docker Desktop 和 Ollama，但不得将环境安装误记为 RAG 功能完成。

## 3. 技术路线与固定决策

### 3.1 组件选择

| 职责 | 选择 | 固定理由 |
|---|---|---|
| 聊天与答案生成 | 现有 DeepSeek/OpenAI-compatible `ChatModel` | 复用已验证模型边界，不增加第二套聊天模型 |
| Embedding | Windows 原生 Ollama + `qwen3-embedding:0.6b` | 无外部 Embedding 服务和密钥；模型约 639 MB；支持中英文和跨语言检索 |
| 向量数据库 | Qdrant Docker 容器 | 与普通 Redis 职责分离；支持向量、Payload、过滤和删除 |
| 事实来源 | MySQL | 保存论文、核验状态和索引版本事实 |
| 缓存 | Redis | 继续承担 API 缓存、短期状态和 TTL，不保存向量 |
| Java 集成 | LangChain4j `OllamaEmbeddingModel` 与 `QdrantEmbeddingStore` | 与当前 Java 21、Spring Boot、LangChain4j 技术栈一致 |

### 3.2 依赖版本策略

项目当前 LangChain4j 主版本保持 `1.17.2`，扩展阶段优先使用与其对应的模块：

- `dev.langchain4j:langchain4j-ollama:1.17.2`
- `dev.langchain4j:langchain4j-qdrant:1.17.2-beta27`

执行时不得只为接入 RAG 顺手升级 Spring Boot、LangChain4j 主版本或 MyBatis-Plus。若依赖冲突无法在半天内解决，先使用 Qdrant 官方 Java Client 隔离适配，不扩大为全项目依赖升级。

Qdrant 镜像必须锁定经过当天验证的稳定版本，不能在可复现演示中使用 `latest`。计划基线可从 `qdrant/qdrant:v1.18.2` 开始验证，最终以实际通过集成测试的镜像标签和摘要为准。

### 3.3 部署拓扑

```text
Windows
├─ Spring Boot / Java 21
├─ MySQL
├─ Ollama
│  └─ qwen3-embedding:0.6b
├─ Docker Desktop / WSL 2
│  └─ Qdrant
└─ PowerShell 验收脚本

CentOS
└─ Redis
```

Qdrant 使用 Docker 命名卷，不直接把 Windows 工作目录绑定为 `/qdrant/storage`。命名卷可以降低 Docker Desktop/WSL 文件系统挂载问题对演示数据的影响。

### 3.4 业务边界

- 只有 MySQL 中当前状态为 `VERIFIED` 的论文能够构造 RAG 文档。
- `PARTIALLY_VERIFIED`、`REJECTED`、`SOURCE_UNAVAILABLE`、`NOT_FOUND` 和未检查论文不得进入索引。
- OpenAlex/Crossref 原始 JSON 不进入向量库。
- Embedding 输入只包含标准化、允许展示的字段。
- Qdrant Payload 不能成为可信状态的事实来源。
- Qdrant 数据丢失时，系统必须能从 MySQL 重建。
- RAG 故障不得影响原有可信论文检索接口。

## 4. 当前阶段索引什么

### 4.1 必须索引

每篇 `VERIFIED` 论文使用以下内容构建检索文本：

- 标题。
- OpenAlex 摘要。
- 作者展示名。
- 年份。
- 期刊或会议名称。
- 关键词或查询规划中已确认的主题词。
- DOI。

推荐文档模板：

```text
Title: {title}
Authors: {authors}
Year: {year}
Venue: {venue}
Keywords: {keywords}
Abstract: {abstract}
DOI: {doi}
```

字段标签使用稳定英文，字段内容保留原文。这样既能支持英文论文内容，也能让中文查询通过多语言 Embedding 完成跨语言召回。

### 4.2 摘要分块

- 标题和元数据组成一个 `METADATA` Segment。
- 有摘要时创建一个或多个 `ABSTRACT` Segment。
- 初始分块参数：每段最多约 350 tokens，重叠约 30 tokens。
- 大多数摘要应保持为一个完整 Segment；不能为了展示 Chunk 技术而无意义切碎短摘要。
- 分块参数必须配置化，并写入 `embeddingVersion` 或 `indexVersion`。

### 4.3 PDF 可选扩展

8 月 15 日只有在全部硬性验收完成后，才允许加入 PDF 演示：

- 最多选择 1～3 篇已核验且合法开放获取的 PDF。
- 记录 PDF 来源 URL、下载时间、许可或开放获取依据。
- 使用 Apache Tika 或 LangChain4j 文档解析器提取文本。
- 只做普通文本型 PDF，不做 OCR、扫描件、表格恢复和公式语义解析。
- PDF Segment 必须带页码或可定位信息；不能把 PDF 全文引用伪装成摘要引用。
- PDF 功能失败不能影响摘要级 RAG 里程碑。

## 5. Qdrant 数据设计

### 5.1 Collection

推荐名称：

```text
research_pilot_paper_segments_v1
```

初始配置：

| 配置 | 值 |
|---|---|
| Vector | 单一 dense vector |
| Size | 1024，创建前必须用真实 Ollama 响应确认 |
| Distance | Cosine |
| Shard | 单机默认 |
| Replication | 单机默认 |
| Storage | Docker named volume |

禁止在没有验证真实向量长度时硬编码创建 Collection。启动检查应先调用 Embedding 模型，确认维度与 Collection 一致。

### 5.2 Point ID

Point ID 必须稳定、可重建，推荐由以下字段生成确定性 UUID：

```text
paperId | embeddingVersion | segmentType | segmentIndex
```

同一论文、同一索引版本和同一 Segment 重建后必须得到相同 Point ID，保证 Upsert 幂等。

### 5.3 Payload

| 字段 | 用途 |
|---|---|
| `paperId` | 回查 MySQL 论文记录 |
| `doi` | 对外引用和去重 |
| `title` | 检索结果展示 |
| `publicationYear` | 年份过滤 |
| `venue` | 来源展示与过滤 |
| `language` | 语言分析 |
| `verificationStatus` | 必须为 `VERIFIED` |
| `verificationVersion` | 记录核验规则版本 |
| `segmentType` | `METADATA`、`ABSTRACT`、可选 `PDF_TEXT` |
| `segmentIndex` | 同一论文内部排序 |
| `embeddingModel` | `qwen3-embedding:0.6b` |
| `embeddingVersion` | 模型、维度、模板与分块策略联合版本 |
| `contentHash` | 判断内容是否需要重新 Embedding |
| `sourceUpdatedAt` | MySQL 来源记录更新时间 |
| `text` | 被 Embedding 的文本或受控展示文本 |

至少为 `paperId`、`doi`、`verificationStatus`、`publicationYear`、`embeddingVersion` 建立适当 Payload Index。

### 5.4 版本与重建规则

以下任一变化都必须提升 `embeddingVersion`，并创建新 Collection 或完成显式迁移：

- Embedding 模型变化。
- 向量维度变化。
- 文档模板变化。
- 分块参数变化。
- 文本标准化规则发生不兼容变化。

MySQL 记录当前激活的索引版本。切换流程为：

1. 创建新 Collection。
2. 从 MySQL 全量读取符合准入条件的论文。
3. 生成新向量并 Upsert。
4. 检查数量、维度和抽样检索。
5. 切换激活版本。
6. 保留旧 Collection 直到回归测试完成。
7. 经人工确认后再删除旧 Collection。

## 6. API 与响应设计

### 6.1 检索调试接口

建议先实现内部检索接口：

```text
POST /api/research/retrieve
```

请求字段：

- `query`：必填，最大长度受限。
- `topK`：默认 5，范围 1～10。
- `fromYear`、`toYear`：可选。
- `doi`：可选精确过滤，仅用于调试。

响应字段：

- `query`。
- `embeddingModel`、`embeddingVersion`。
- `matches`。
- 每个 Match 的 `paperId`、DOI、标题、Segment 类型、分数、文本片段。
- `elapsedMs`。

该接口用于把“检索质量”和“生成质量”分开评估，不直接调用聊天模型。

### 6.2 RAG 问答接口

建议新增：

```text
POST /api/research/ask
```

响应至少包含：

- `answer`。
- `citations`：引用编号、paperId、标题、DOI、年份、来源、Segment 类型。
- `retrievalSummary`：Top-K、最低分数、实际证据数。
- `insufficientEvidence`。
- `message`。
- `requestId`。
- `elapsedMs`。

引用编号使用 `[P1]`、`[P2]` 等稳定形式。Java 必须校验：

- 回答中的每个引用编号都存在于本次检索证据。
- `citations` 中的论文来自本次检索结果。
- 引用 DOI 与 MySQL 正式论文一致。
- 没有证据时不能生成看似确定的结论。
- 引用校验失败最多允许一次受控修正，仍失败则返回降级结果。

### 6.3 管理操作

索引重建不建议默认暴露为公开 HTTP 接口。第一版优先采用以下之一：

- 启动参数控制的管理接口，默认关闭。
- Spring Boot `ApplicationRunner`/命令行任务。
- 仅开发环境启用的内部端点。

无论采用哪种方式，都必须防止普通用户任意触发全量 Embedding。

## 7. 检索与生成策略

### 7.1 检索

- Query 使用同一 `qwen3-embedding:0.6b` 生成向量。
- 服务端强制添加 `verificationStatus=VERIFIED` 与当前 `embeddingVersion` 过滤。
- 默认 Top-K 为 5，最大 10。
- `minScore` 不在计划阶段拍脑袋冻结；先以配置值启动，再通过标注问题集校准。
- 检索分数是向量相似度，不是事实可信概率。
- Qdrant 返回后重新从 MySQL确认论文仍为 `VERIFIED`，防止索引状态滞后。

### 7.2 Prompt 和外部文本安全

摘要、标题和 PDF 文本均视为不可信外部内容：

- 在 Prompt 中明确标记为“参考材料”，不能作为系统指令。
- 不执行材料中的命令、链接或提示词。
- 不把外部文本拼接到 System Prompt 权限区域。
- 限制单条 Segment 和总上下文长度。
- 日志不记录完整摘要、完整 Prompt 和模型原始回答。

### 7.3 生成

- 模型只根据本次检索证据回答。
- 无法支持的内容必须明确说证据不足。
- 不允许模型补充未检索到的论文。
- 论文基本信息以 Java/数据库字段为准，不能让模型重写 DOI、作者和年份。
- 答案生成与引用映射分离：模型生成引用编号，Java 组装最终 Citation DTO。

## 8. 本地环境安装计划

### 8.1 Docker Desktop 与 WSL 2

先在 PowerShell 检查：

```powershell
wsl --version
wsl --status
Get-ComputerInfo | Select-Object WindowsProductName, WindowsVersion, OsBuildNumber
```

如果 WSL 未安装，在管理员 PowerShell 中执行：

```powershell
wsl --install
```

如果已安装但版本较旧：

```powershell
wsl --update
```

系统提示重启时手工重启，不要在保存中的开发工作未关闭时自动重启。

从 Docker 官方页面下载 `Docker Desktop Installer.exe`。推荐使用 WSL 2 的当前用户安装方式：

```powershell
Start-Process "$HOME\Downloads\Docker Desktop Installer.exe" `
    -Wait `
    -ArgumentList "install", "--user", "--backend=wsl-2"
```

启动 Docker Desktop 并接受适用的许可条款后验证：

```powershell
docker version
docker compose version
docker info
```

### 8.2 Ollama

从 Ollama 官方 Windows 页面下载并安装。安装后验证：

```powershell
ollama --version
```

拉取轻量多语言 Embedding 模型：

```powershell
ollama pull qwen3-embedding:0.6b
ollama list
```

真实向量冒烟测试：

```powershell
$body = @{
    model = "qwen3-embedding:0.6b"
    input = "遥感影像变化检测"
} | ConvertTo-Json

$response = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:11434/api/embed" `
    -ContentType "application/json" `
    -Body $body

$response.embeddings.Count
$response.embeddings[0].Count
```

验收要求：

- 返回至少一个向量。
- 向量非空。
- 记录真实向量维度。
- 同一模型的中文和英文输入维度一致。
- 不在文档中假设维度，必须以真实响应确认。

### 8.3 Qdrant

项目中新增独立基础设施文件：

```text
infra/docker-compose.rag.yml
```

Compose 必须满足：

- 锁定 Qdrant 镜像版本。
- 暴露 `6333` HTTP 与 `6334` gRPC。
- 使用 Docker named volume。
- 配置健康检查。
- 不把服务暴露到公网。
- 不提交真实 API Key。

启动和验证：

```powershell
docker compose -f .\infra\docker-compose.rag.yml up -d
docker compose -f .\infra\docker-compose.rag.yml ps
Invoke-RestMethod -Uri "http://localhost:6333"
Invoke-RestMethod -Uri "http://localhost:6333/collections"
```

停止但保留数据：

```powershell
docker compose -f .\infra\docker-compose.rag.yml stop
```

重新启动：

```powershell
docker compose -f .\infra\docker-compose.rag.yml start
```

普通重启不得使用 `down -v`。删除命名卷属于破坏性操作，只能在明确需要清空索引并已确认 MySQL 可重建时执行。

## 9. 每日执行计划

## Day 1｜8 月 10 日｜前置验收、环境安装与架构冻结

### 当日目标

在不破坏 `v1.0.0-demo` 的前提下，完成扩展阶段门禁、Docker/Ollama/Qdrant 环境和 RAG 设计冻结。

### 每日项目内容

- 验证可信检索 Agent 的最终标签、测试和三条演示路径。
- 创建独立功能分支，不直接在演示标签上开发。
- 检查 Windows、WSL 2、虚拟化和可用磁盘空间。
- 安装或更新 WSL 2。
- 安装 Docker Desktop 并验证 Docker Compose。
- 安装 Windows 原生 Ollama。
- 拉取 `qwen3-embedding:0.6b`。
- 执行中文和英文 Embedding 冒烟测试，记录真实维度和耗时。
- 新增 `infra/docker-compose.rag.yml`，使用 Qdrant 命名卷。
- 启动 Qdrant，验证 HTTP、gRPC 端口和 Dashboard。
- 编写 ADR：MySQL 为事实来源，Qdrant 为可重建派生索引。
- 冻结 Collection、Payload、Point ID、索引版本和删除策略。

### 每日学习内容

- Embedding、向量维度、Cosine Similarity 的含义。
- WSL 2、Docker Desktop、Container、Image、Volume 的区别。
- Qdrant Collection、Point、Vector、Payload 和 Payload Index。
- 为什么向量相似度不能被解释为事实可信度。
- 为什么向量数据库不能替代 MySQL。

### 当日验收标准

- 前置可信检索演示仍可运行。
- `docker version` 和 `docker compose version` 成功。
- Ollama Embedding API 对中英文输入均返回同维度非空向量。
- Qdrant `6333` 可访问，容器状态健康。
- 停止并重新启动 Qdrant 后服务恢复。
- ADR 和架构设计明确可重建边界。
- 未修改现有检索契约。

### 建议 Git 提交

```text
chore: add local trusted rag infrastructure baseline
```

## Day 2｜8 月 11 日｜Embedding 边界与可信文档投影

### 当日目标

将 MySQL 中的 `VERIFIED` 论文转换为稳定、可测试、可版本化的 RAG 文档，但暂不写入 Qdrant。

### 每日项目内容

- 引入与现有 LangChain4j 版本兼容的 Ollama 与 Qdrant 模块。
- 新增 `EmbeddingProperties`，配置 base URL、模型、维度、超时、批大小和版本。
- 使用 `OllamaEmbeddingModel` 建立 Embedding Port/Adapter。
- 模型关闭、服务不可用、超时和维度不一致时返回明确异常。
- 定义 `VerifiedPaperProjection` 或等价只读投影。
- 实现 `RagDocumentBuilder`，只接受 `VERIFIED` 论文。
- 实现稳定文本模板、Segment 类型、Segment Index 和 `contentHash`。
- 实现确定性 Point ID。
- 对缺失摘要、超长摘要、中文标题、英文标题和特殊字符补测试。
- 保证 Prompt、摘要和模型原始响应不进入日志。

### 每日学习内容

- Document、TextSegment、EmbeddingModel、Embedding 的职责。
- 为什么 Embedding 输入模板必须版本化。
- 内容哈希、幂等 Upsert 和索引重建。
- 中文查询与英文文档的跨语言 Embedding。
- 外部论文摘要中的 Prompt Injection 风险。

### 当日验收标准

- 非 `VERIFIED` 论文构建文档时被拒绝。
- 同一论文重复构建得到相同 Point ID 和 `contentHash`。
- 内容变化后 `contentHash` 变化。
- 中文和英文测试文本可生成固定维度向量。
- Ollama 不可用时错误清晰，不影响原文献检索接口。
- 单元测试不依赖真实 Ollama。
- 单独的受控冒烟测试验证真实 Ollama。

### 建议 Git 提交

```text
feat: add versioned verified paper embedding pipeline
```

## Day 3｜8 月 12 日｜Qdrant 索引、幂等写入与重建

### 当日目标

完成从 MySQL 可信论文到 Qdrant 的可重建索引闭环。

### 每日项目内容

- 新增 `QdrantProperties` 和配置校验。
- 实现 Collection 初始化与维度一致性检查。
- 创建必要的 Payload Index。
- 实现批量 Embedding 和批量 Upsert。
- 实现索引计数、按 paperId 删除、全量重建和版本切换。
- 论文从 `VERIFIED` 变为不符合准入条件时删除对应 Points。
- 内容未变化时跳过重复 Embedding。
- 将 Qdrant 和 Ollama 状态加入 `/api/system/status`，与应用存活状态分离。
- 添加 Qdrant 不可用、维度错误、部分批次失败和重复执行测试。
- 使用少量真实已核验论文完成一次索引冒烟。

### 每日学习内容

- Upsert 幂等、批处理和失败重试。
- Payload Filter 与 Payload Index。
- Collection 版本迁移和双索引切换。
- 派生索引最终一致性。
- 为什么不能使用 Qdrant Payload 反向覆盖 MySQL 核验状态。

### 当日验收标准

- 只有 `VERIFIED` 论文被写入。
- Qdrant Point 数量与应索引 Segment 数量一致。
- 连续执行两次重建后数量不翻倍。
- 更改一篇论文后只更新对应 Points。
- 降级一篇论文的核验状态后对应 Points 被删除。
- 删除 Qdrant 数据后可从 MySQL 完整重建。
- Qdrant 失败不影响 `/api/literature/search` 的正确性。

### 建议 Git 提交

```text
feat: add idempotent qdrant index rebuild
```

## Day 4｜8 月 13 日｜可过滤检索与小型评测集

### 当日目标

先证明检索结果可测、可解释，再接入答案生成。

### 每日项目内容

- 实现 Query Embedding。
- 实现 Qdrant Top-K 检索。
- 服务端强制添加可信状态和当前索引版本过滤。
- 支持可选年份过滤。
- Qdrant 返回后从 MySQL 二次确认论文仍符合准入条件。
- 实现 `/api/research/retrieve` 调试接口。
- 准备至少 12 个中英文标注问题：
  - 8 个用于阈值和 Top-K 调整。
  - 4 个作为固定验收问题。
- 记录 Hit@K、MRR、平均检索耗时和无关结果案例。
- 调整 `topK` 和 `minScore`，但不得用最终 4 个验收问题反复调参。
- 保存评测输入、预期 DOI 和实际结果，不保存秘密。

### 每日学习内容

- Top-K、Hit@K、Recall@K、MRR 的区别。
- 相似度阈值、误召回和漏召回。
- 为什么检索评测与生成评测必须分开。
- 元数据过滤与纯向量相似度的组合。
- 小数据集上的评测局限。

### 当日验收标准

- 中文问题能够召回相关英文论文。
- 过滤条件不能被用户文本绕过。
- 结果只包含当前仍为 `VERIFIED` 的论文。
- 固定验收集达到事先约定的 Hit@5 目标；推荐目标为不低于 75%。
- 未达到目标时记录失败类型，不通过降低可信门槛伪造成功。
- 每条检索结果可以回溯到 MySQL 论文和 Qdrant Segment。

### 建议 Git 提交

```text
feat: add evaluated trusted paper retrieval
```

## Day 5｜8 月 14 日｜带引用的 RAG 问答与证据降级

### 当日目标

实现真实可演示的可信 RAG 问答，同时保证引用只能来自本次检索证据。

### 每日项目内容

- 定义 `ResearchQuestionRequest`、`ResearchAnswerResponse` 和 Citation DTO。
- 使用 LangChain4j AI Services 或 RetrievalAugmentor 组装 RAG 生成链路。
- 继续复用现有 DeepSeek `ChatModel` 和敏感日志边界。
- 将检索内容作为不可信参考材料注入，不进入高权限指令区域。
- Prompt 要求每个关键观察带 `[P1]` 等引用。
- 实现 Java 引用解析与 Citation DTO 组装。
- 实现非法引用、未知编号、重复引用和无引用结论校验。
- 校验失败最多修正一次。
- 证据不足时返回 `insufficientEvidence=true`，不生成确定性综述。
- 新增 `/api/research/ask`。
- 覆盖成功、引用修正、无证据、Ollama 故障、Qdrant 故障和模型故障测试。

### 每日学习内容

- Naive RAG 与 Advanced RAG 的区别。
- RetrievalAugmentor、ContentRetriever、ContentInjector 的职责。
- Grounded Answer、Citation Validation 和 Abstention。
- RAG 中的间接 Prompt Injection。
- 为什么 DOI、作者和年份应由 Java DTO 输出，而不是由模型重新生成。

### 当日验收标准

- 回答只引用本次检索出的论文。
- 每个引用编号都能解析为真实 DOI 和标题。
- 模型输出不存在的 `[P99]` 时被拦截。
- 零证据时不生成带论文结论。
- Qdrant 或 Ollama 不可用时返回明确错误或降级信息。
- 原有 Search Agent、Crossref、持久化和缓存回归测试全部通过。
- 完成至少一次真实中文问题到英文论文引用的全链路演示。

### 建议 Git 提交

```text
feat: add citation-validated trusted rag answers
```

## Day 6｜8 月 15 日｜质量收口、可复现演示与可选 PDF

### 当日目标

完成扩展里程碑，不以临时环境或一次偶然回答冒充交付。

### 每日项目内容

- 运行全量自动测试和 RAG 定向测试。
- 从停止状态启动 Docker Desktop、Qdrant、Ollama 和 Spring Boot。
- 按 README 从 MySQL 重建 Qdrant 索引。
- 执行固定检索评测集并保存结果摘要。
- 执行三组 RAG 演示：
  1. 中文问题检索英文论文并生成带 DOI 引用的回答。
  2. 年份过滤影响检索结果。
  3. 证据不足时系统拒绝扩写。
- 准备一套真实联网演示和一套固定数据回放。
- 更新 README、架构图、数据流图、Qdrant Schema 和故障排查。
- 增加 PowerShell 环境检查与验收脚本。
- 检查密钥、环境变量、日志、Docker 配置和示例响应。
- 如果全部硬性验收在中午前完成，再尝试 1～3 篇开放获取 PDF。
- 形成扩展阶段开发日志。
- 创建并推送里程碑标签。

### 每日学习内容

- 如何讲解可信检索与可信 RAG 的差别。
- 如何解释向量库数据丢失后的重建流程。
- 如何诚实表达当前评测规模和局限。
- 如何展示“检索正确但生成错误”与“检索错误导致生成错误”。
- 如何将技术亮点转化为简历和面试表达。

### 当日验收标准

- Docker、Ollama、Qdrant 和应用可按文档从停止状态恢复。
- Qdrant 命名卷重启后数据存在。
- 删除 Collection 后能从 MySQL 重建。
- 全量测试通过。
- 固定检索评测结果可复现。
- 三组 RAG 演示全部通过。
- 回答中的论文引用均可从 MySQL、Crossref 核验证据和 Qdrant Segment 回溯。
- RAG 依赖故障不影响核心可信论文检索。
- 仓库不包含真实密钥、个人邮箱、模型原始 Prompt 或未授权 PDF。
- 工作区干净，本地与远程分支同步。
- 标签 `v1.1.0-rag-demo` 指向扩展验收提交。

### 建议 Git 提交

```text
docs: complete trusted rag demo delivery
```

## 10. 测试矩阵

| 层级 | 必测内容 |
|---|---|
| 文档准入 | VERIFIED 可进入；其他状态全部拒绝 |
| 文本构建 | 模板稳定、空摘要、特殊字符、超长文本 |
| Point ID | 重建稳定、不同 Segment 不冲突 |
| Embedding | 非空、维度一致、超时、服务不可用 |
| Qdrant | Collection 创建、Upsert、Filter、Delete、重建、版本不一致 |
| 检索 | 中文查英文、年份过滤、Top-K、低分无结果 |
| 二次准入 | Qdrant 状态滞后时由 MySQL 拦截 |
| 生成 | 正常回答、证据不足、模型异常 |
| 引用 | 有效引用、未知编号、重复编号、缺失引用 |
| 安全 | 外部摘要中的指令不被执行，日志不泄露正文 |
| 降级 | Qdrant/Ollama 故障不破坏原检索接口 |
| 重建 | Collection 删除后从 MySQL 恢复 |
| 回归 | Search Agent、OpenAlex、Crossref、MySQL、Redis 原功能 |

真实外部组件验收与自动测试分离：

- 单元测试使用 Fake/Mock，不依赖 Ollama 和 Qdrant。
- 集成测试使用 Testcontainers 或明确的本地测试 Profile。
- 真实本地冒烟测试单独执行并记录环境版本。
- 不让普通 `mvn test` 因 Docker Desktop 未启动而全部失败。

## 11. 最终演示脚本

### 演示 1：可信准入

1. 展示 MySQL 中 VERIFIED 与非 VERIFIED 论文。
2. 执行索引重建。
3. 展示 Qdrant 中只有 VERIFIED 论文。
4. 展示 Point Payload 中的 DOI、核验版本和索引版本。

### 演示 2：跨语言检索

1. 输入中文研究问题。
2. 展示 Ollama 生成 Query Embedding。
3. 展示 Qdrant 返回英文摘要 Segment。
4. 展示年份和可信状态过滤。
5. 展示检索结果对应的真实 DOI。

### 演示 3：引用约束

1. 调用 `/api/research/ask`。
2. 展示回答中的 `[P1]`、`[P2]`。
3. 展示 Citation DTO 中的论文标题和 DOI。
4. 说明 Java 如何阻止 `[P99]` 等不存在引用。

### 演示 4：证据不足

1. 输入知识库没有覆盖的问题。
2. Qdrant 返回零个或低于阈值的结果。
3. 系统返回证据不足，而不是由模型自由回答。

### 演示 5：可重建

1. 删除测试 Collection 或切换到空 Collection。
2. 从 MySQL 重建。
3. 对比 Point 数量、版本和固定检索结果。

## 12. 范围外事项

以下内容不进入 8 月 10～15 日硬性范围：

- 通用 PDF 上传平台。
- 扫描件 OCR。
- 复杂表格、公式和图像理解。
- 多模态 Embedding。
- 大规模全文爬取。
- 混合检索、稀疏向量和复杂 Reranker。
- 多 Agent 自主研究。
- 前端页面。
- Qdrant 集群、分片、副本和生产高可用。
- 自动生成论文内容真实性或研究质量评分。

这些内容可以进入后续版本，但不能用空接口、空包或未验证代码提前占位。

## 13. 风险与降级方案

| 风险 | 预防 | 降级 |
|---|---|---|
| Docker/WSL 安装需要重启 | 8 月 10 日先处理环境 | 环境未完成则不写 Qdrant 业务代码 |
| 机器内存不足 | 使用 `qwen3-embedding:0.6b`，限制批大小 | 降低批大小，不改为不可信云服务 |
| Ollama CPU 推理较慢 | 记录单条和批量耗时 | 减少演示数据量，保留异步重建设计 |
| Qdrant Windows 挂载问题 | 使用 named volume | 从 MySQL 重建 |
| 向量维度不一致 | 创建 Collection 前真实探测 | 创建新版本 Collection，禁止截断或补零 |
| 跨语言召回不理想 | 建立固定评测集 | 记录基线，后续比较 bge-m3，不伪造指标 |
| 摘要数量不足 | 元数据 Segment 仍可检索 | 只生成有限观察，不扩写全文结论 |
| 模型虚构引用 | Java Citation Guard | 修正一次后降级 |
| 前置可信检索延期 | 强制前置门禁 | 扩展整体顺延 |

## 14. 里程碑完成定义

只有同时满足以下条件，才能创建 `v1.1.0-rag-demo`：

- 前置可信检索演示版已稳定。
- 本地 Ollama Embedding 与 Qdrant 环境可复现。
- 只有 VERIFIED 论文进入向量索引。
- 索引可幂等重建。
- 中文问题能够检索英文论文。
- 检索质量具有固定问题集和指标。
- RAG 回答中的引用由 Java 校验。
- 证据不足时系统主动降级。
- 原有核心接口回归测试通过。
- README、架构图、演示脚本和故障排查齐全。
- 完成秘密扫描、Markdown 检查和 Git diff 检查。
- 工作区干净，提交和标签已推送。

每日仍遵循：

1. 职责单一的小步提交。
2. 当天完成当天推送。
3. 只有完整阶段验收通过后才创建里程碑标签。

## 15. 简历与面试表达

推荐项目亮点表述：

> 在 Java 21、Spring Boot 与 LangChain4j 项目中实现可信学术检索 Agent：使用 OpenAlex 召回候选、Crossref 进行跨源书目核验，仅将 VERIFIED 论文写入 MySQL 和 Qdrant；通过本地多语言 Embedding 支持中文问题检索英文论文，并实现可重建向量索引、元数据过滤、检索评测、证据不足降级与 Java 侧引用校验。

避免以下夸大表述：

- “证明论文绝对真实”。
- “实现了完整学术搜索引擎”。
- “支持任意 PDF 精确解析”。
- “RAG 可以消除大模型幻觉”。
- “向量相似度就是论文可信度”。

## 16. 官方参考资料

- Docker Desktop Windows 安装：<https://docs.docker.com/desktop/setup/install/windows-install/>
- Docker Desktop WSL 2：<https://docs.docker.com/desktop/features/wsl/>
- Ollama Windows：<https://docs.ollama.com/windows>
- Ollama Embeddings：<https://docs.ollama.com/capabilities/embeddings>
- Ollama qwen3-embedding：<https://ollama.com/library/qwen3-embedding>
- Qdrant 本地 Quickstart：<https://qdrant.tech/documentation/quick-start/>
- Qdrant Collection：<https://qdrant.tech/documentation/manage-data/collections/>
- Qdrant Payload：<https://qdrant.tech/documentation/concepts/payload/>
- LangChain4j Ollama Embedding：<https://docs.langchain4j.dev/integrations/embedding-models/ollama/>
- LangChain4j Qdrant：<https://docs.langchain4j.dev/integrations/embedding-stores/qdrant/>
- LangChain4j RAG：<https://docs.langchain4j.dev/tutorials/rag/>
