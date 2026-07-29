# ResearchPilot

## 2026-07-29：受控 Agent 工作流接入（提前完成 2026-08-01 阶段）

`POST /api/literature/search` 现在通过既有 HTTP 契约进入 `LiteratureSearchService`，由
`SearchAgent` 生成可信初始计划后初始化并运行 `LiteratureResearchAgent`。Service 不再
直接执行 OpenAlex、Crossref、去重或核验步骤；完整有限状态机仍封装在 Agent 编排组件中。

- 第一轮对非空候选固定执行
  `SEARCH_OPENALEX → DEDUPLICATE_CANDIDATES → VERIFY_WITH_CROSSREF → EVALUATE_RESULTS`；
  没有去重候选时按现有状态机直接评估，不发起无意义的 Crossref 调用。
- 只有可信论文不足且预算仍允许时，`SearchActionDecider` 才能在 `REFINE_PLAN`
  与 `COMPLETE` 之间建议动作。单一合法动作由 Java 直接选择，不调用动作模型。
- `SearchPlanRefiner` 继续携带内部 `ValidatedSearchPlanContext`，调整后的计划重新通过
  完整五层校验；`AgentState.currentPlan` 与可信上下文保持一致，provenance 不进入外部 API。
- 第二轮复用全局 `CandidateDeduplicationKey`，稳定重复候选不会再次进入 Crossref，
  不重复增加唯一候选数、Crossref 调用数或正式论文。
- 所有动作在执行前同时经过 `AgentTransitionPolicy` 和 `AgentBudgetPolicy`；服务器上限为
  2 轮搜索、1 次调整、10 个业务步骤、45 个全局唯一候选、45 次 Crossref 调用和 90 秒。
- Crossref 正式输出仍只允许 `VERIFIED` 且具有规范化 DOI 的论文。deadline、预算拒绝、
  OpenAlex/Crossref 不可用、非法状态和非预期异常均以精确原因安全终止，并保留此前结果。
- 响应继续使用冻结的 `SearchResponse` 字段。`candidateCount` 汇总所有搜索轮次返回的原始
  候选数，`deduplicatedCount` 使用跨轮全局唯一候选数；核验统计必须与去重数守恒，公开
  `papers` 只保留具有规范化 DOI 的 `VERIFIED` 结果。状态由正式论文数决定，而不是仅由
  终止原因决定：足额为 `COMPLETED`，不足但非零为 `PARTIAL_SUCCESS`，零结果为
  `NO_VERIFIED_RESULTS`。
- Service 仅将安全的状态映射为中文用户消息；内部 Trace、终止详情、模型输出和外部原始
  响应均不进入公开 API。完成日志关联 `taskId`、Agent 阶段、终止原因、计数与耗时。
- 内存 Trace 按 `traceId` 隔离，记录连续 `stepIndex`、动作/阶段、决策来源、耗时、
  预算前后值、失败码和终止原因；阶段必须连续、预算必须单调，摘要最多 500 字符。
  Trace 不保存完整输入、Prompt、模型原始输出、凭据、provider JSON、论文全文或完整摘要。
- 离线验收使用固定 Clock、Fake/Mockito 端口和固定模型输出。覆盖首次满足、一次重规划
  后满足、预算限制后的部分结果和零 VERIFIED 路径；真实 LLM/OpenAlex/Crossref smoke
  test 默认关闭，不会由普通 Maven 测试触发。定向 `mvn test` 运行 13 项测试，0 失败、
  0 错误；最终 `./mvnw.cmd clean verify` 运行 365 项测试，0 失败、0 错误、2 项 opt-in
  Crossref smoke test 按预期跳过，并完成 JAR 打包。
- 在开发者手动启动且已安全配置外部服务的实例上，使用同一固定请求完成两次真实
  LLM → OpenAlex → Crossref 端到端联网验收。两次均返回 HTTP 200 和 `COMPLETED`，
  均取得 15 个候选、15 个全局唯一候选、5 篇正式 `VERIFIED` 论文；服务器记录耗时
  分别为 17,395 ms 和 9,244 ms。仓库不记录密钥、Prompt、外部原始响应或完整请求内容。

## 2026-07-30：受控检索计划调整

当 `SearchActionDecider` 已选择 `REFINE_PLAN` 后，系统现在可以生成一次严格受控的检索表达扩展，并把合并后的草稿重新送入完整可信计划校验链。本阶段只生成新的可信 `SearchPlan`，不执行第二轮 OpenAlex 或 Crossref。

- 首轮校验在解析最终值时同步记录 `ConstraintOrigin`：显式请求值为 `USER_EXPLICIT`，实际采用的模型草稿值为 `MODEL_DERIVED`，最终配置默认值为 `SYSTEM_DEFAULT`，服务器预算为 `SYSTEM_FIXED`。
- provenance 与 `SearchPlan` 分离保存在 `SearchPlanValidationResult` 和内部 `ValidatedSearchPlanContext` 中，不改变 `SearchRequest`、Controller、Swagger 或外部响应契约。
- 模型 refinement schema 只允许 `synonyms`、`abbreviations`、`conceptCombinations` 和短原因；年份、语言、文献类型、排序、数量、预算和执行命令均不是合法输出字段。
- `SearchPlanRefiner` 只追加合法、去重、有界的新英文表达。`originalQuery`、topic、年份、语言、文献类型、排序、`resultLimit` 与 `candidateLimit` 从当前可信计划原样复制。
- 合并后的 `SearchPlanDraft` 重新经过 JSON 语法、JSON Schema、DTO 映射、业务规则和执行前安全校验；校验后再次比较冻结字段，失败时不会调用 OpenAlex、Crossref、Redis 或数据库。
- Java 强制每个任务最多调整一次；现有 `SearchActionDecider` 只负责决定是否选择 `REFINE_PLAN`，不修改计划或调用工具。
- 验收证据：47 项定向测试通过；`.\mvnw.cmd clean verify` 从空 `target` 编译、测试并打包成功，346 项测试中 0 失败、0 错误，2 项默认关闭的真实 Crossref smoke test 按预期跳过。

本阶段没有实现完整 Agent while 循环、第二轮检索/核验、综合生成、持久化、缓存或 RAG。

## 2026-07-29: validated agent action decisions (completed early on 2026-07-28)

- `AgentTransitionPolicy` is the structural action whitelist. Its results are immutable; terminal and in-progress states expose no model-selectable action, while Java alone can execute `TERMINATE` for an active state.
- The action model can propose only the existing `AgentAction` values `SEARCH_OPENALEX`, `DEDUPLICATE_CANDIDATES`, `VERIFY_WITH_CROSSREF`, `EVALUATE_RESULTS`, `REFINE_PLAN`, and `COMPLETE`. It never receives `CREATE_INITIAL_PLAN` or `TERMINATE`.
- Raw model output passes a fixed JSON-syntax, JSON-Schema, strict DTO, business, and current-state security validation sequence. The one-field schema rejects additional budget, limit, query, or constraint fields.
- `SearchActionDecider` applies structural and read-only budget checks before one optional AI Services call. A single executable action skips the model. Disabled, unavailable, or invalid model output uses a deterministic action that is still in the filtered allowed set.
- The decider neither starts actions nor issues `ActionExecutionPermit`s and has no OpenAlex/Crossref tool-port or client dependency. Real execution remains behind `LiteratureResearchAgent.prepareAction` and `AgentBudgetPolicy`.
- Validation evidence: 14 focused tests passed; `mvn clean verify` passed 332 tests with 0 failures/errors and 2 expected, opt-in smoke-test skips.
- At this milestone, `SearchPlanRefiner`, real plan refinement, a multi-round autonomous agent loop, Crossref orchestration, persistence, cache, RAG, PDF, and frontend work were not yet implemented.

## 2026-07-28：受控文献研究 Agent 状态与执行预算

在既有可信检索与核验链路之外，新增 `LiteratureResearchAgent` 作为后续完整工作流的受控编排骨架；现有 `SearchAgent` 仍只生成和校验可信 `SearchPlan`，不承担状态管理、动作决策或外部工具调用。

- `AgentState` 是不可变聚合：保存原始请求、可信计划及历史、候选与核验结果、正式论文、全局计数、轻量 Observation 和结构化终止信息。集合均为不可变副本，终止后的状态拒绝继续动作。
- `AgentStage`、`AgentAction`、`AgentObservation` 和 `TerminationReason` 以有限类型表达首轮检索、去重、Crossref 核验和正常/异常终止；不引入模型动作建议、自动循环或动态动作白名单。
- `AgentBudgetPolicy` 在动作开始前统一检查服务器固定预算：最多 2 轮 OpenAlex 搜索、1 次计划调整、8 个业务步骤、45 个跨轮全局唯一候选、45 次 Crossref 调用和 90 秒总截止。截止边界固定为 `now >= deadline`。
- 全局唯一候选继续复用既有身份规则：规范化 DOI、OpenAlex ID、完整精确书目键依次优先；第二轮出现相同稳定键不会重复占用候选预算。
- 新增受控 OpenAlex 执行入口作为最小真实工具边界：预算拒绝时 `OpenAlexSearchPort` 调用次数为零；允许后才调用，并将结果截断到已检查的候选上限、记录 Observation。既有 `LiteratureSearchService` 链路保持不变。
- `app.research-agent` 通过环境变量提供可运维配置，但所有限制都在 Java 中校验且必须为正值；请求中的 `limit` 不会提高这些全局硬上限。
- 新增状态、预算、跨轮去重、截止、终止和端口零调用测试。`.\mvnw.cmd test` 共运行 317 项，0 失败、0 错误，2 项默认关闭的真实 Crossref smoke test 按预期跳过。

## 2026-07-27：可信论文核验闭环与正式结果准入

自然语言请求现在会经过可信 `SearchPlan`、OpenAlex 候选检索、候选标准化与去重、Crossref 查询、字段级证据比较、`VerificationPolicy` 判定和 `EligiblePaperFilter` 正式准入。每个去重候选都保留独立的 Crossref 查询结果，不再依赖列表下标关联候选与参考记录。

- 有规范化 DOI 的候选只走 Crossref DOI 精确查询；DOI 一致且标题、作者、年份无明确冲突时才可判定为 `VERIFIED`。
- 无 DOI 候选只有在唯一、无歧义、强字段匹配且取得可规范化 Crossref DOI 时才可判定为 `VERIFIED`。
- `PARTIALLY_VERIFIED`、`CONFLICTED`、`NOT_FOUND`、`SOURCE_UNAVAILABLE`、`NOT_CHECKED` 和 `REJECTED` 仅用于诊断与统计，不进入正式 `papers`。
- 正式结果按规范化 DOI 再次全局去重，并保持 OpenAlex 候选顺序；`relevanceScore` 是基于原始排名的展示分，不是概率或 Crossref 核验分。
- `.\mvnw.cmd clean verify` 共运行 308 项测试，0 失败、0 错误，2 项显式启用的真实 Crossref smoke test按预期跳过，并成功完成 JAR 打包。
- 真实 Swagger 验收返回 HTTP 200：15 个 OpenAlex 候选、15 个去重候选、5 次 Crossref 查询全部找到，正式返回 5 篇具有规范化 DOI 的 `VERIFIED` 论文。

## 2026-07-25：统一标准化与 Crossref 调用前去重

OpenAlex 原始候选现在会在 Crossref 外部调用前经过确定性的本地标准化和保守去重。原始 `CandidatePaper` 不被覆盖，标准化值、去重键、重复原因和原始候选证据由独立模型保存。

- DOI 继续复用共享 `DoiNormalizer`；标题、第一作者、来源和 OpenAlex Work ID 分别由窄职责标准化器处理。标准化只生成稳定比较值，不推断论文同一性。
- 去重键按 `DOI > OpenAlex ID > 精确书目键（标题 + 第一作者 + 年份）` 分层选择。书目三要素不全时不生成回退键，候选会被保留。
- 组内保留规则和最终输出顺序完全确定；重复组保留被选候选、所有原始成员、使用的键和去重原因。
- `LiteratureSearchService` 先完成候选标准化与去重，再把唯一候选交给 Crossref。重复候选不会重复消耗 DOI 查询或书目回退预算。
- `VerificationEvidence`、字段证据和匹配状态仅为下一阶段建立结构；本阶段不计算模糊相似度、不设置阈值，也不产生 `VERIFIED`。

本阶段坚持保守边界：标题近似、作者名顺序/缩写变体、预印本与正式出版版本不会仅凭字符串相似而自动合并。近期没有多源核验计划，因此暂不扩展跨键连通去重。

## 2026-07-24：Crossref 映射、固定回放与分支整理

当 OpenAlex 候选论文没有可用 DOI 时，系统现在会先对标题执行确定性的本地资格校验；只有合格标题才会请求 Crossref `/works` 的 `query.bibliographic`。有效 DOI 仍只走精确 DOI 查询，DOI `NOT_FOUND` 不会自动回退标题查询。

- 查询模型由 Java 确定性地按“标题、第一作者、年份、来源”构造；默认返回上限为 5，`CROSSREF_BIBLIOGRAPHIC_ROWS` 可配置为 1–10。
- 返回值明确区分 `NOT_FOUND`、`FOUND_SINGLE` 和 `FOUND_MULTIPLE`，保留全部上限内候选，不默认选择第一条。
- 空白、纯符号、超长、控制字符、URL、JSON/XML/HTML、Markdown 代码块和异常重复标题会在 HTTP、限流与重试之前被拒绝。
- `CrossrefPaperMapper` 将外部响应收敛为既有内部字段；日期优先级为 print、online、issued、created。`CrossrefWorkMetadata` 的字段与 public record 构造器保持不变，外部 URL 不进入内部契约。
- 普通测试复用经过人工审核的真实 Crossref 响应快照：Captured date 为 2026-07-22，Integrated/reused date 为 2026-07-24；用途为 DTO 反序列化、Mapper 回放和离线回归，不会被在线 smoke test 覆盖。
- 仓库只保留两条业务分支：`main` 维护应用代码、普通测试和计划文档；`eval/crossref-verification-v1` 维护评测数据集与结构测试。
- Crossref 候选发现不等于字段验证：在 2026-07-24 阶段不会产生 `VERIFIED`，当时的 `SearchResponse.papers` 保持为空。

2026-07-24 阶段的 `main` 执行 `mvn clean verify`：250 项通过、0 失败、0 错误，2 项显式开关控制的真实 Crossref smoke test 按预期跳过，并完成 JAR 打包。

ResearchPilot 是一个基于 Java、Spring Boot、LangChain4j 与 RAG 的学术文献检索 Agent。

第一阶段已经完成工程骨架、MySQL、Redis、真实模型调用、统一异常处理、Swagger 和自动测试。

第二阶段已完成文献检索数据流、接口契约和五个核心数据结构的冻结。原计划
2026-07-20 完成的 OpenAlex 候选论文检索模块已于 2026-07-19 提前完成；
原计划 2026-07-21 完成的 Search Agent 查询规划已于 2026-07-20 提前一天
完成。2026-07-21 原计划中“后续接入 Crossref”的内容已调整为先完成
Crossref DOI 精确查询基础、访问治理与候选编排：包含默认关闭配置、限流、
重试、受控错误和内部元数据摘要。2026-07-22 已完成 OpenAlex 与 Crossref 共用的
DOI 规范化入口、精确查询收敛，以及字段核验评测数据集的离线结构骨架；字段级
核验器、去重与持久化仍按后续计划开发。

## 当前进度

- [x] 第一阶段工程闭环和真实环境验收
- [x] 文献检索类级数据流与模块职责
- [x] `SearchRequest`、`SearchPlan`、`PaperDTO`、`VerificationResult`、`SearchResponse`
- [x] `POST /api/literature/search` 请求、响应契约和单 OpenAlex 运行时编排
- [x] OpenAlex 候选论文检索模块
- [x] Search Agent 查询规划与最多一次结构化输出修正
- [x] JSON 语法、Schema、DTO、业务规则和安全规则五层校验
- [x] Crossref DOI 精确元数据查询、访问治理与候选编排（不等于核验通过）
- [x] OpenAlex 与 Crossref 共享 DOI 规范化、请求前校验和响应 DOI 收敛
- [x] `eval/crossref-verification-v1` 分支中的 Crossref 字段核验评测数据集骨架、来源追踪与变异谱系约束（不等于字段核验已实现）
- [x] 候选字段标准化、分层精确去重和 Crossref 调用前预算保护
- [x] Crossref 字段级元数据核验与 VERIFIED 准入
- [x] 受控动作决策、真实约束来源与一次追加式检索计划调整
- [x] 最多两轮的受控 Agent 执行循环、跨轮去重、预算门禁与内存 Trace
- [ ] 标题/作者/年份/来源相似度、阈值校准与可信度评分
- [ ] MySQL 检索任务、论文和核验记录持久化

## 技术栈

- Java 21
- Spring Boot 3.5.16
- MyBatis-Plus 3.5.17
- LangChain4j 1.17.2
- MySQL
- Redis
- Maven Wrapper
- springdoc-openapi / Swagger UI
- Spring RestClient

## 基础设施职责

- MySQL：保存检索任务、论文元数据、核验记录和最终可靠状态。
- Redis：保存缓存、短期任务状态、进度和具有 TTL 的临时数据。
- Qdrant：第三阶段保存论文文本块向量和检索元数据。

当前 Redis 是基础 Redis，未加载 RediSearch/Search 模块，因此不承担向量检索。第三阶段计划使用 Qdrant 作为向量数据库。

## 项目目录

~~~text
src/main/java/com/dj1012h/researchpilot
├── literature
│   ├── api
│   │   ├── LiteratureSearchController.java
│   │   └── dto
│   │       ├── SearchRequest.java
│   │       └── SearchResponse.java
│   ├── application
│   │   ├── LiteratureSearchService.java
│   │   ├── SearchAgent.java
│   │   ├── LlmQueryPlanner.java
│   │   ├── SearchPlanPromptBuilder.java
│   │   ├── SearchPlanDraft.java
│   │   ├── OpenAlexQueryFactory.java
│   │   ├── CrossrefCandidateLookupService.java
│   │   └── CrossrefLookupSummary.java
│   ├── validation
│   │   ├── JsonSyntaxValidator.java
│   │   ├── SearchPlanSchemaValidator.java
│   │   ├── SearchPlanDraftMapper.java
│   │   ├── SearchPlanBusinessValidator.java
│   │   ├── SearchPlanSecurityValidator.java
│   │   └── SearchPlanValidationPipeline.java
│   ├── agent
│   │   ├── AgentState.java
│   │   ├── AgentTransitionPolicy.java
│   │   ├── SearchActionDecider.java
│   │   └── SearchPlanRefiner.java
│   └── model
│       ├── CandidatePaper.java
│       ├── OpenAlexQuery.java
│       ├── SearchPlan.java
│       ├── SearchSort.java
│       ├── LanguageCode.java
│       ├── PaperDTO.java
│       └── VerificationResult.java
├── integration
│   └── openalex
│       ├── dto
│       ├── OpenAlexClient.java
│       ├── OpenAlexPaperMapper.java
│       ├── OpenAlexSearchAdapter.java
│       └── OpenAlexSearchPort.java
│   └── crossref
│       ├── dto
│       ├── CrossrefClient.java
│       ├── CrossrefPaperMapper.java
│       ├── CrossrefRequestGate.java
│       ├── CrossrefRetryPolicy.java
│       └── CrossrefSearchAdapter.java
├── common
│   ├── ai
│   │   └── ModelInvoker.java
│   └── response
├── controller
├── service
│   └── impl
├── mapper
├── dto
│   ├── request
│   └── response
├── config
└── exception

src/test                    # 自动测试
docs/sql                   # MySQL 初始化脚本
docs/decisions             # 技术决策记录
docs/design                # 检索契约、模块职责和类级数据流
http                       # HTTP 请求样例
scripts                    # 启动与验收脚本
~~~

新的文献检索功能采用“按业务分包、包内轻量分层”。第一阶段已有的通用聊天和系统状态代码暂时保留原结构，待第二阶段完成后再逐步迁移，避免重构干扰当前主线。

## 初始化 MySQL

使用 MySQL 管理员执行：

~~~text
docs/sql/init-database.sql
~~~

把脚本中的 `CHANGE_ME` 替换成真实密码。应用长期使用 `research_pilot` 专用账号，不使用 root。

## 启动项目

在 PowerShell 中执行：

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File "C:\javaProject\research-pilot\scripts\start-local.ps1" `
    -RedisHost "真实Redis地址" `
    -RedisPort 6379 `
    -LlmBaseUrl "真实模型Base URL" `
    -LlmModelName "真实模型名称"
~~~

启动脚本会安全询问 MySQL 密码、Redis 密码和模型 API Key。秘密不会写入仓库。

## 验证项目

应用启动后，在另一个 PowerShell 中执行：

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File "C:\javaProject\research-pilot\scripts\verify-phase1.ps1"
~~~

验证内容包括：

- Actuator
- Swagger UI
- MySQL
- Redis
- 模型配置
- 三次真实模型调用
- 空消息参数校验
- 超长消息参数校验

## 接口

- Swagger UI：<http://localhost:8080/swagger-ui.html>
- 健康检查：<http://localhost:8080/actuator/health>
- 依赖状态：<http://localhost:8080/api/system/status>
- 聊天接口：`POST /api/chat`
- 文献检索接口：`POST /api/literature/search`（需启用并配置 LLM 与 OpenAlex）

聊天请求示例：

~~~json
{
  "message": "什么是 RAG？"
}
~~~

## 文献检索契约

中文设计文档：[`docs/design/literature-search-contract.md`](docs/design/literature-search-contract.md)

第一版已冻结的关键规则：

- “近五年”包含当前年份；2026 年解析为 2022～2026。
- 显式年份和数量字段优先于自然语言推断。
- 第一版只执行一个主要 OpenAlex 检索式。
- 默认不限制论文语言。
- 预印本只进入候选池，不进入正式结果。
- 正式结果必须包含标准化 DOI，核验状态只能是 `VERIFIED`；`PARTIALLY_VERIFIED` 仅保留为诊断与统计结果。
- 搜索成功但零篇通过核验时，返回 HTTP 200、`NO_VERIFIED_RESULTS` 和空列表。
- `PaperDTO` 与 `VerificationResult` 分离，外部 API DTO 和数据库 Entity 不得替代核心契约。

当前分支自动测试共 346 个通过，另有 2 个默认关闭的真实 Crossref 冒烟测试按预期跳过，包含架构约束、
Search Agent、字段来源、受控计划调整、五层校验、OpenAlex、Crossref、候选编排、固定快照回放和真实 Spring MVC 序列化测试。

## OpenAlex 候选检索

原计划完成日期：2026-07-20

实际完成日期：2026-07-19（提前一天）

OpenAlex 集成使用 `RestClient`，通过 `OpenAlexSearchPort` 向后续
`SearchAgent` 暴露内部候选论文，不向业务层暴露 OpenAlex 外部 DTO。
`OpenAlexQueryFactory` 只接受经过校验的 `SearchPlan`，客户端负责 HTTP 和
反序列化，`OpenAlexPaperMapper` 负责生成项目内部 `CandidatePaper`。

本地启用前设置以下环境变量：

~~~powershell
$env:OPENALEX_ENABLED = "true"
$env:OPENALEX_API_KEY = "从 OpenAlex 获取的 API Key"
~~~

可选配置包括 `OPENALEX_BASE_URL`、`OPENALEX_CONNECT_TIMEOUT`、
`OPENALEX_READ_TIMEOUT` 和 `OPENALEX_DEFAULT_PAGE_SIZE`。API Key 不得写入
仓库或日志；本地 `.env` 已加入 `.gitignore`，但 Spring Boot 默认不会自动
加载 `.env`，运行应用时仍需由终端、IDE 或外部配置注入环境变量。

真实验收使用固定英文检索词 `protein structure prediction`，筛选
2021～2026 年 `article`，成功获得并映射 5 篇真实候选论文。返回数据覆盖
OpenAlex ID、DOI、标题、作者、来源、日期、摘要和开放获取地址；其中一篇
缺少摘要但未影响整批解析。真实 API 验收还确认当前降序排序语法应使用
`relevance_score:desc`、`publication_date:desc` 和
`cited_by_count:desc`。

## Search Agent 查询规划

原计划完成日期：2026-07-21

实际完成日期：2026-07-20（提前一天）

查询计划生成链路为：

~~~text
SearchRequest
→ SearchAgent
→ LlmQueryPlanner
→ String
→ JSON 语法校验
→ JSON Schema 校验
→ SearchPlanDraft 严格映射
→ 业务规则校验
→ 执行前安全校验
→ SearchPlan
~~~

核心可信边界：

- LLM 只生成原始字符串和受约束草稿，不能直接创建可信 `SearchPlan`。
- HTTP 显式参数优先于模型推断，默认值和 `candidateLimit` 只由 Java 计算。
- 结构化输出最多修正一次；安全错误和模型供应商错误不进入结构化重试。
- 任意校验失败都不会触发 OpenAlex。
- `SearchAgent` 只负责查询规划，不直接调用 OpenAlex；`LiteratureSearchService`
  负责可信计划到单次候选召回的确定性编排。
- 模型专用严格 Mapper 通过窄职责包装器与 Spring MVC Mapper 隔离，既保留
  严格 DTO 映射，也确保 `Instant` 类型能够正常写入 HTTP JSON。

当前运行时链路能够接收 `POST /api/literature/search`，生成可信计划并执行一次
OpenAlex 候选检索；候选经过统一标准化和分层精确去重后，唯一候选会在 Crossref
启用时按配置预算顺序执行 DOI 精确查询或受控书目回退。Crossref 找到记录不等于
核验通过，因此候选和 Crossref 元数据只参与内部统计，不会作为已核验正式论文
返回；成功但没有正式结果时返回 `NO_VERIFIED_RESULTS`。

受控 Agent 内部链路还可以在动作决策选择 `REFINE_PLAN` 后生成一次追加式
`SearchPlan`。该能力尚未接入完整自动循环，也不会自行执行第二轮 OpenAlex 或
Crossref；模型只能建议英文检索表达，最终计划仍必须由 Java 合并并通过完整五层校验。

## Crossref 候选元数据查询

原计划调整日期：2026-07-21

Crossref 默认关闭。启用时必须通过外部环境配置 `CROSSREF_MAILTO` 与
`CROSSREF_USER_AGENT`；可选的 `CROSSREF_PLUS_TOKEN` 不会写入日志或仓库。
客户端使用 DOI 精确查询、URI Builder、受控状态映射、公平并发限制、本地速率
限制，以及 `Retry-After` 优先的有限指数退避。429、5xx、超时和传输错误才会
重试；404 作为未找到继续处理下一个 DOI；非预期 3xx 不跟随并归类为非法响应。

~~~powershell
$env:CROSSREF_ENABLED = "true"
$env:CROSSREF_MAILTO = "your-email@example.com"
$env:CROSSREF_USER_AGENT = "ResearchPilot/0.1"
$env:LITERATURE_MAX_CROSSREF_LOOKUPS = "5"
~~~

一次检索最多执行 5 次 Crossref 候选查询，DOI 精确查询和书目回退共享预算。
OpenAlex 映射、候选去重、Crossref 请求和 Crossref 响应共用同一 DOI 规范化入口；
非法请求在 HTTP 门控前拒绝，非法响应不会进入内部元数据。来源不可用时停止后续
Crossref 查询并保留已有元数据。当前对缺失有效 DOI 的唯一候选执行受控标题书目
回退，并通过字段比较、最终核验策略和正式准入 Gate 只向 `papers` 写入具有规范化
DOI 的 `VERIFIED` 论文。外部响应中的
URL 目前不映射到内部模型，待有明确展示或跳转需求时再以兼容性设计扩展。

## Crossref 字段核验评测数据集

评测资产仅位于 `eval/crossref-verification-v1` 分支，不进入 `main`。该分支提供离线、可复现的数据集目录，用于后续验证
`CandidatePaper + CrossrefWorkMetadata -> VerificationResult` 的字段匹配逻辑；其中包含固定案例 Schema、来源 provenance、父子 lineage、DOI 规范化变异和元数据扰动清单，并由结构测试校验引用路径、SHA-256 与状态约束。

该分支复用既有、已人工审核的 Crossref 快照，不为任务日期重新抓取或改写原始响应。快照 Captured date 为 2026-07-22，Integrated/reused date 为 2026-07-24；Purpose 为 DTO deserialization、Mapper replay 和离线回归测试。无法确认审核日期时不填写 Reviewed date。数据集不包含 benchmark runner、字段核验器或在线 Crossref 调用，也不评测 HTTP、重试、预算和编排行为。

## 第一阶段验收记录

提前完成日期：2026-07-17

- [x] Java 21 和 Maven Wrapper 可用
- [x] Spring Boot 项目正常启动
- [x] 22 个自动测试全部通过
- [x] Actuator 返回 UP
- [x] Swagger UI 返回 HTTP 200
- [x] MySQL 状态为 UP
- [x] Redis 状态为 UP
- [x] 模型配置状态为 true
- [x] 连续三次真实模型调用成功
- [x] 空消息返回 HTTP 400
- [x] 超长消息返回 HTTP 400
- [x] 敏感信息通过环境变量提供
- [x] 基础 Redis 不具备 RediSearch 的结论已经记录
- [x] 后续使用 Qdrant 的决策已经记录

## 当前范围

当前尚未实现：

- 将内部受控 Agent 执行循环接入公共检索 API
- 更大规模的阈值校准、多源交叉核验和正式结果相关性排序
- 检索任务、论文和核验记录入库
- Embedding
- Qdrant 接入
- 向量检索
- RAG 问答
- PDF 全文解析

这些功能将在后续阶段实现。

## 2026-08-02｜受门槛保护的摘要级综述准备

- 新增仅供内部使用的 `literature.review` 链路：权威输入只来自受控工作流完成后的 `AgentRunResult.finalState().verifiedPapers()`；候选、去重结果和非 `VERIFIED` 核验结果均不能构造综述证据。
- `CitationId` 与正式论文原始顺序一一对应；缺少摘要的正式论文不进入证据包，但不会改变其他论文的编号。每个 `EvidencePaper` 仅含规范化 DOI、最小书目信息和 OpenAlex 重建摘要。
- 模型调用前同时执行双门槛：`ceil(requestedCount * 0.60)` 篇正式 `VERIFIED` 论文，且至少 3 篇具有非空摘要。任一门槛不足返回明确内部状态并保证零模型调用。
- Prompt 使用固定安全指令和 JSON 序列化的 `EVIDENCE DATA (UNTRUSTED)` 边界；摘要中的指令、URL、角色声明或格式覆盖要求均不是系统指令。模型输出只表示未验证的内部草稿，不写入日志、持久化层或公共 API。
- 本阶段未实现 `ReviewDraft` 映射、CitationGuard、引用解析/修正、降级或公开响应组装，也未修改现有搜索预算、核验规则、Controller 或 `SearchResponse`。
