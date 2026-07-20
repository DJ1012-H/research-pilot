# ResearchPilot

ResearchPilot 是一个基于 Java、Spring Boot、LangChain4j 与 RAG 的学术文献检索 Agent。

第一阶段已经完成工程骨架、MySQL、Redis、真实模型调用、统一异常处理、Swagger 和自动测试。

第二阶段已完成文献检索数据流、接口契约和五个核心数据结构的冻结。原计划
2026-07-20 完成的 OpenAlex 候选论文检索模块已于 2026-07-19 提前完成；
原计划 2026-07-21 完成的 Search Agent 查询规划已于 2026-07-20 提前一天
完成。本次同时落地公共模型调用器、五层可信校验管线、OpenAlex 语言与排序
增强以及单数据源运行时编排；Crossref、去重、核验和持久化仍按后续计划开发。

## 当前进度

- [x] 第一阶段工程闭环和真实环境验收
- [x] 文献检索类级数据流与模块职责
- [x] `SearchRequest`、`SearchPlan`、`PaperDTO`、`VerificationResult`、`SearchResponse`
- [x] `POST /api/literature/search` 请求、响应契约和单 OpenAlex 运行时编排
- [x] OpenAlex 候选论文检索模块
- [x] Search Agent 查询规划与最多一次结构化输出修正
- [x] JSON 语法、Schema、DTO、业务规则和安全规则五层校验
- [ ] Crossref 元数据核验
- [ ] DOI/标题去重与可信度评分
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
│   │   └── OpenAlexQueryFactory.java
│   ├── validation
│   │   ├── JsonSyntaxValidator.java
│   │   ├── SearchPlanSchemaValidator.java
│   │   ├── SearchPlanDraftMapper.java
│   │   ├── SearchPlanBusinessValidator.java
│   │   ├── SearchPlanSecurityValidator.java
│   │   └── SearchPlanValidationPipeline.java
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
- 正式结果必须包含标准化 DOI，核验状态只能是 `VERIFIED` 或符合门槛的 `PARTIALLY_VERIFIED`。
- 搜索成功但零篇通过核验时，返回 HTTP 200、`NO_VERIFIED_RESULTS` 和空列表。
- `PaperDTO` 与 `VerificationResult` 分离，外部 API DTO 和数据库 Entity 不得替代核心契约。

当前自动测试共 164 个，包含架构约束、Search Agent、五层校验、OpenAlex 和真实 Spring MVC 序列化测试。

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
OpenAlex 候选检索。Crossref 核验尚未实现，因此候选只参与统计，不会作为已核验
正式论文返回；成功但没有正式结果时返回 `NO_VERIFIED_RESULTS`。

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

- Crossref 外部调用
- 候选标准化去重、元数据核验和最终可信排序
- 检索任务、论文和核验记录入库
- Embedding
- Qdrant 接入
- 向量检索
- RAG 问答
- PDF 全文解析

这些功能将在后续阶段实现。
