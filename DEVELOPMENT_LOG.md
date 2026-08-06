## 2026-07-30｜提前完成计划中的 2026-08-06 Redis 外部 API 缓存里程碑

### 实际完成内容

- 在 `OpenAlexSearchPort` 与 `CrossrefSearchPort` 的 Port/Adapter 边界加入默认关闭的
  Redis Cache-Aside 装饰器，支持 OpenAlex 查询、Crossref DOI 精确查询和 Crossref
  书目回退查询的跨请求复用；`LiteratureSearchService`、受控 Agent、Review 和公共 DTO
  均不直接依赖 Redis。
- 缓存键采用 `research-pilot:literature:v1:{provider}:{operation}:{sha256}`。摘要覆盖所有
  实际请求字段，Redis 键不暴露 query、DOI、标题、作者、mailto、Base URL 或凭据。
- 缓存值仅包含 Mapper 后的 `OpenAlexSearchResult`、`CrossrefLookupResult` 和
  `CrossrefBibliographicLookupResult`，Envelope 显式校验 schema、provider、operation、
  result kind、Payload 大小和内部 Record 不变量。
- OpenAlex 成功结果（含合法空结果）使用 15 分钟 TTL；Crossref `FOUND`、
  `FOUND_SINGLE`、`FOUND_MULTIPLE` 使用 24 小时 TTL；仅明确 `NOT_FOUND` 使用 5 分钟
  负缓存。认证错误、限流、5xx、超时、传输错误、空响应和非法响应均不缓存。
- Redis 读取、写入和损坏数据均 fail-open。首次访问失败后启动 30 秒、可注入 Clock
  驱动的绕过期；损坏值只删除当前精确键。缓存命中仍进入既有 Crossref 字段核验、
  `VerificationPolicy` 和 `EligiblePaperFilter`，不会直接产生 `VERIFIED`。

### 数据边界

- MySQL 继续持久化检索任务、可信计划尝试、正式论文元数据、候选级与字段级核验证据、
  Agent 步骤审计、任务到正式论文结果、终态计数与时间戳，是可审计事实来源。
- Redis 只保存带 TTL 的 OpenAlex/Crossref 内部查询结果，以及明确 Crossref
  `NOT_FOUND` 的短期负缓存；不保存任务事实、AgentState、Prompt、模型输出、综述、
  VerificationResult、SearchResponse 或完整执行轨迹。

### 验收证据

- 聚焦命令
  `.\mvnw.cmd "-Dtest=*Cache*,*Redis*,ArchitectureConstraintsTest,PaperVerificationServiceTest,AgentExecutionLoopTest,LiteraturePersistenceFacadeIntegrationTest" test`
  运行 50 项：0 failures、0 errors、2 项默认关闭的真实 Redis smoke 跳过。
- `.\mvnw.cmd clean verify` 从空 `target` 编译 241 个生产源文件和 83 个测试源文件，
  运行 454 项：0 failures、0 errors、4 项明确 opt-in 联网测试跳过；Spring Boot
  可执行 JAR 打包成功。
- 经用户授权的真实 Redis smoke 单独运行 2 项：0 failures、0 errors、0 skipped。
  测试使用运行时随机专用前缀，验证 miss→hit、TTL 大于 0 且不超过配置值、只精确删除
  本次键，以及不可达 Redis 地址下直连 Adapter。输出未记录真实键、query、DOI、
  私有主机或凭据。

### 里程碑说明

- 以上工作实际完成和验收日期为 2026-07-30，属于提前完成计划中的 2026-08-06
  里程碑，不表示测试在 8 月 6 日当天执行。

## 2026-07-29｜受控 Agent 工作流接入（提前完成 2026-08-01 阶段）

### 实际完成内容

- `LiteratureSearchService` 现在只负责创建一次 `taskId`、记录统一开始/完成时间、取得可信初始计划上下文、初始化并运行 `LiteratureResearchAgent`，再由最终 `AgentState` 组装既有 `SearchResponse`。Service 中没有新增 Agent `while`/`switch` 或直接外部工具调用。
- `AgentRunResult.finalState()` 明确提供唯一的响应事实来源；Trace 仍仅用于内部诊断，不会暴露给 Controller 或公共 JSON 契约。
- 响应状态按正式 `VERIFIED` 论文数映射为 `COMPLETED`、`PARTIAL_SUCCESS` 或 `NO_VERIFIED_RESULTS`。多轮 `candidateCount` 从搜索 Observation 汇总，`deduplicatedCount` 使用跨轮唯一候选数，核验统计继续由 `SearchResponse` 契约强制守恒。
- 离线定向测试覆盖 Service 委派、两轮原始候选累计、部分结果与用户消息映射；已有 `AgentExecutionLoopTest` 覆盖首次满足、一次重规划满足、预算限制部分结果、零 VERIFIED、截止前零工具调用和非法计划上下文零工具调用。

### 验证结果

- 定向命令 `./mvnw.cmd -Dtest=LiteratureSearchServiceTest,LiteratureSearchFlowIntegrationTest,AgentExecutionLoopTest test`：13 项通过，0 失败、0 错误、0 跳过。
- 最终命令 `./mvnw.cmd clean verify`：365 项通过，0 失败、0 错误、2 项显式 opt-in Crossref smoke test 跳过；Spring Boot 可执行 JAR 打包成功。

### 真实联网验收

- 开发者手动启动已安全配置外部服务的应用后，以同一固定中文检索请求完成两次真实
  LLM → OpenAlex → Crossref 端到端调用；健康检查均为 `UP`，业务接口均返回 HTTP 200
  和 `COMPLETED`。
- 第一次：15 个候选、15 个全局唯一候选、5 篇正式 `VERIFIED` 论文，服务器耗时
  17,395 ms，客户端端到端耗时 17,471 ms。
- 第二次：15 个候选、15 个全局唯一候选、5 篇正式 `VERIFIED` 论文，服务器耗时
  9,244 ms，客户端端到端耗时 9,260 ms；另有 10 个未核验候选，没有部分核验或拒绝项。
- 两次结果在候选数、去重数和正式论文数上保持一致；耗时差异属于真实外部模型与数据源
  调用的运行波动，不作为固定性能承诺。日志只记录聚合结果，不记录密钥、Prompt、
  provider 原始响应或完整请求内容。

## 2026-07-28 - early completion of the 2026-07-29 action whitelist, model proposal, and deterministic fallback milestone

### Actual progress

- Added `AgentTransitionPolicy`, one-field `SearchActionDraft`, trusted `SearchActionDecision`, an immutable action context, and a fixed five-stage validation pipeline backed by `schemas/search-action-draft-v1.json`.
- Added an optional LangChain4j AI Services adapter behind the existing `ModelInvoker` boundary. It returns raw text for validation and does not register or invoke tools.
- Added `SearchActionDecider`, which only performs structural and read-only budget filtering. It does not mutate `AgentState`, consume budget, start an action, or issue an execution permit.
- A sole executable action is selected by Java with zero model calls. Unconfigured/unavailable models and invalid JSON, schema, DTO, business, or security output use a deterministic allowed action.
- Added offline policy, validation, decider, and architectural tests. The focused run passed 14 tests with 0 failures, errors, or skips; `mvn clean verify` passed 332 tests with 0 failures/errors and 2 expected opt-in smoke-test skips.

### Scope boundaries

- No second action enum was introduced. `CREATE_INITIAL_PLAN` and `TERMINATE` are absent from model choices; `TERMINATE` remains Java-owned.
- Invalid model actions are rejected before execution. `SearchActionDecider` has no OpenAlex/Crossref port or client dependency, and the architecture test prohibits `@Tool` in the action-decision package.
- This work intentionally does not implement `SearchPlanRefiner`, actual plan refinement, a second live search loop, complete Crossref agent orchestration, persistence, cache, RAG, Qdrant, PDF, or frontend features.

### Delivery marker

- The 2026-07-29 acceptance scope was completed one day early on 2026-07-28. Milestone tag `milestone/2026-07-29-agent-action-decisions` points to the accepted delivery commit after the final remote synchronization check.

# ResearchPilot 开发日志

> 日志覆盖时间：2026-07-13 ～ 2026-07-25
> 第一阶段状态：已完成，较原计划 2026-07-18 提前一天
> 第二阶段进展：已提前完成原计划 2026-07-19 的数据流与接口契约
> OpenAlex 进展：已提前完成原计划 2026-07-20 的候选论文检索模块
> Search Agent 进展：已提前完成原计划 2026-07-21 的查询规划与可信校验链路
> Crossref 进展：2026-07-27 已完成候选级查询关联、字段核验证据解释、最终核验策略和 VERIFIED 正式结果准入，并通过真实 Swagger 端到端验收
> 里程碑版本：`v0.1.0-phase1`
> 第一阶段提交：`3c84ef4a6dd6043320c95bc9655701a52d52ce3b`
> 远程仓库：<https://github.com/DJ1012-H/research-pilot>

## 1. 记录原则与证据说明

本文档记录 ResearchPilot 从初始化、第一阶段验收到 Search Agent 可信查询规划链路落地的实际开发过程。

为避免把计划误写成事实，日志使用以下证据来源：

- **Git 证据**：提交、标签、远程分支和工作区状态。
- **文件证据**：源文件的最后修改时间，用于还原两个提交之间的实际开发顺序。
- **测试证据**：Maven Surefire 报告和验收脚本输出。
- **人工确认**：开发者确认的运行环境、模型服务、Redis 能力和实际操作结果。

注意：第一阶段的大部分代码在 2026-07-17 集中提交，因此 7 月 13～16 日的逐日记录主要依据文件时间、测试痕迹和实际排障过程还原；后续阶段应坚持每日小步提交，以提高时间线的可追溯性。

本文档不记录 MySQL 密码、Redis 密码、模型 API Key、服务器公网地址等敏感信息。

## 2. 项目目标

ResearchPilot 的最终目标是完成一个可由其他用户从 GitHub 下载、按照 README 配置并运行的科研文献检索 Agent，形成以下可演示闭环：

1. 将自然语言研究问题转换为结构化检索计划。
2. 使用真实文献数据源检索候选论文。
3. 交叉核验 DOI、标题、作者、年份和期刊信息。
4. 将任务、论文和核验记录保存到 MySQL。
5. 使用 Redis 管理缓存、短期任务状态和进度。
6. 使用 Qdrant 保存向量及检索元数据。
7. 仅基于通过核验的论文执行 RAG 问答。
8. 在答案中返回真实、可追溯的论文标题和 DOI。

第一阶段聚焦“工程闭环”，不提前实现文献搜索、Embedding、Qdrant 和 RAG。

## 3. 实际开发环境

| 类别 | 实际配置 |
|---|---|
| 开发系统 | Windows 本地开发 |
| Java | Java 21；验收环境为 21.0.10 |
| 构建工具 | Maven Wrapper |
| 应用框架 | Spring Boot 3.5.16 |
| 持久化 | MyBatis-Plus 3.5.17 + MySQL |
| MySQL | Windows 本地运行，使用 `research_pilot` 专用账号 |
| Redis | 远程 CentOS 上的基础 Redis |
| Redis 能力 | 基础连接、认证、数据结构和 TTL 可用；无 RediSearch/Search 模块 |
| 模型服务 | DeepSeek OpenAI-compatible API |
| 模型地址 | `https://api.deepseek.com` |
| 模型名称 | `deepseek-v4-flash` |
| 向量数据库决策 | 第三阶段引入 Qdrant |
| 当前远程仓库协议 | HTTPS |

后续开发继续采用“Windows 应用与 MySQL + 远程 CentOS Redis”的环境组合。最终交付不能依赖开发者本机的绝对路径或私有环境，README 必须能够指导其他用户完成安装、配置、启动和验证。

## 4. 阶段总览

| 日期 | 主要进展 | 当日结果 |
|---|---|---|
| 2026-07-13 | 初始化 Git 仓库和 Spring Boot 工程骨架 | 仓库建立，基础入口、OpenAPI、SQL、HTTP 样例和 Maven Wrapper 落盘 |
| 2026-07-14 | 完成分层重构、MyBatis-Plus 和基础设施状态检查 | Controller–Service–Mapper 结构形成，MySQL/Redis/LLM 状态接口具备实现基础 |
| 2026-07-15 | 进行真实模型调用烟雾测试 | DeepSeek 接口返回真实响应，确认 OpenAI-compatible 调用路径可行 |
| 2026-07-16 | 强化模型异常处理、参数校验、测试和验收工具 | 统一错误结构形成；解决 Maven Wrapper、环境变量和验收脚本问题 |
| 2026-07-17 | 完成自动测试、真实环境验收、文档、Git 里程碑和远程同步 | 第一阶段正式完成并发布 `v0.1.0-phase1` |
| 2026-07-17（原计划 07-19） | 确定文献检索数据流、接口契约、模块职责和冻结规则 | 第二阶段查询契约落地，完整测试增至 31 个 |
| 2026-07-19（原计划 07-20） | 实现 OpenAlex 候选论文检索、映射、异常治理和真实 API 验收 | 候选召回模块提前完成，完整测试增至 67 个 |
| 2026-07-20（原计划 07-21） | 实现 Search Agent、五层可信校验和单 OpenAlex 运行时链路 | 查询规划提前完成，完整测试增至 164 个 |
| 2026-07-25 | 实现统一字段标准化、分层精确去重和 Crossref 调用前预算保护 | 保守去重链路完成，字段核验证据结构就绪，未提前实现相似度或最终业务判定 |

## 5. 每日开发记录

## 2026-07-13｜仓库初始化与工程骨架

### 当日目标

- 建立正式 Git 仓库。
- 生成能够启动的 Spring Boot 工程骨架。
- 为 MySQL、Redis、模型调用和接口文档预留基础结构。

### 实际进展

- 14:17 创建首个提交：
  - 提交：`11f6bff55a01236767d0ab81e13f8d22cf2c7f97`
  - 消息：`chore: initialize research-pilot repository`
- 创建 Spring Boot 应用入口 `ResearchPilotApplication`。
- 引入 Java 21、Spring Boot、LangChain4j、MySQL、Redis、Actuator 和 springdoc-openapi。
- 创建 OpenAPI 配置，规划 Swagger UI。
- 创建 MySQL 初始化脚本：
  - 数据库：`research_pilot`
  - 应用账号：`research_pilot`
  - 长期运行不使用 root。
- 创建 Maven Wrapper、HTTP 请求样例和基础 Spring 上下文测试。
- 开始采用环境变量管理数据库、Redis 和模型配置。

### 验证与结果

- 仓库主分支建立。
- 工程目录、构建入口和最小测试结构存在。
- 数据库初始化与接口烟雾测试已有可执行入口。

### 学习与复盘

- 项目骨架的首要目标是建立可验证的最小闭环，而不是提前堆叠 Agent、RAG 和向量库模块。
- 密码和 API Key 必须来自环境变量或运行时安全输入，不能进入 Git。
- 应尽早建立小步提交习惯；本阶段代码最终集中在 7 月 17 日提交，降低了中间过程的 Git 可追溯性。

### 后续任务

- 引入 MyBatis-Plus。
- 建立 Controller–Service–Mapper 分层。
- 实现 MySQL、Redis 和模型配置状态检查。

## 2026-07-14｜分层重构与基础设施探针

### 当日目标

- 将项目整理为清晰的横向分层结构。
- 接入 MyBatis-Plus。
- 实现应用对 MySQL、Redis 和模型配置的显式检查。

### 实际进展

- `pom.xml` 增加 MyBatis-Plus 3.5.17 BOM 和 Spring Boot 3 Starter。
- 建立以下包结构：
  - `controller`
  - `service`
  - `service.impl`
  - `mapper`
  - `dto.request`
  - `dto.response`
  - `config`
  - `exception`
  - `common.response`
- 创建 `DatabaseProbeMapper`，通过 MyBatis 执行 `SELECT 1`。
- 创建 `SystemStatusService` 和实现类，分别检查：
  - 应用状态
  - MySQL
  - Redis
  - ChatModel Bean 是否存在
- Redis 检查使用 `PING`，预期返回 `PONG`。
- 创建 ChatController、SystemStatusController 和对应 DTO。
- 创建 `AiProperties` 与 `AiConfiguration`，仅在模型启用时创建 ChatModel Bean。
- 建立 MyBatis-Plus 和 OpenAPI 配置。

### 设计结果

- Controller 只处理 HTTP 和 DTO。
- Service 负责业务规则与流程编排。
- Mapper 只负责数据库访问。
- 数据库 Entity 不直接作为 API 响应。
- `/actuator/health` 用于判断应用进程是否启动。
- `/api/system/status` 用于显式验证 MySQL、Redis 和模型配置。

### 学习与复盘

- “应用存活”与“外部依赖全部可用”是两个不同概念。
- MySQL 状态探针使用 MyBatis Mapper 而不是直接 JDBC，可以同时验证数据源、MyBatis-Plus 自动配置、Mapper 扫描和数据库连接。
- 模型未配置时不应阻止应用启动，应在业务接口中返回可读错误。

### 后续任务

- 验证真实模型调用。
- 补充输入校验和统一异常映射。
- 为模型故障类型建立测试。

## 2026-07-15｜DeepSeek 真实调用烟雾测试

### 当日目标

- 使用真实 OpenAI-compatible 模型服务验证 `/api/chat` 调用链。
- 确认模型响应不是本地占位结果。

### 实际进展

- 使用 DeepSeek API 完成真实模型调用。
- 实际配置：
  - Base URL：`https://api.deepseek.com`
  - 模型：`deepseek-v4-flash`
- 生成本地响应文件 `response.json` 作为临时验证结果。
- `response.json` 被加入忽略规则，不作为项目源代码提交。

### 验证与结果

- 模型返回非空真实回答。
- ChatController → ChatService → ChatModel 的调用链可用。
- 证明 LangChain4j 的 OpenAI-compatible 配置能够连接当前模型服务。

### 安全与范围控制

- 日志只记录模型服务商和模型名称。
- API Key 不写入日志、README、`application.yml` 或 Git。
- 模型输出只能证明模型调用可用，不能替代后续 OpenAlex/Crossref 的真实文献检索与核验。

### 学习与复盘

- LLM 适合生成检索策略、摘要和基于上下文的回答，不应直接生成“真实论文列表”。
- 临时响应、运行日志和调试输出不应混入源代码提交。

### 后续任务

- 区分认证失败、超时、限流、模型不存在和服务不可用等异常。
- 增加参数校验与错误响应测试。

## 2026-07-16｜错误治理、测试与验收工具

### 当日目标

- 完成可读、可测试且不泄露敏感信息的错误处理。
- 建立可复现的本地启动与验收流程。
- 明确 Redis 的真实能力与后续向量方案。

### 实际进展

- `ChatRequest` 增加：
  - 非空校验
  - 最大 4000 字符校验
- 建立 `ModelFailureType`，区分：
  - `AUTHENTICATION`
  - `TIMEOUT`
  - `RATE_LIMITED`
  - `MODEL_NOT_FOUND`
  - `INVALID_PROVIDER_REQUEST`
  - `UNAVAILABLE`
  - `PROVIDER_ERROR`
- `ChatServiceImpl` 对 LangChain4j 异常进行分类，并记录不包含消息正文和密钥的结构化日志。
- `GlobalExceptionHandler` 将模型错误映射为明确的 HTTP 状态和业务错误码。
- 增加并扩展以下自动测试：
  - ChatServiceImpl
  - GlobalExceptionHandler
  - SystemStatusServiceImpl
  - Spring 上下文
- 修复 Windows PowerShell 5.1 下 Maven Wrapper 的兼容性问题。
- 创建并迭代：
  - `scripts/start-local.ps1`
  - `scripts/verify-phase1.ps1`
- `.env.example` 增加 MySQL、Redis、模型以及第三阶段 Qdrant 的示例变量。
- 验证基础 Redis 可连接，但 `MODULE LIST` 不包含 RediSearch/Search。
- 决定第三阶段使用 Qdrant 作为向量数据库，Redis 继续承担缓存和任务状态职责。

### 当日主要故障

1. Maven Wrapper 在 Windows PowerShell 5.1 中出现空数组索引错误。
2. 应用启动时未继承正确环境变量，状态接口显示：
   - MySQL：`DOWN / SQLException`
   - Redis：`DOWN / UnknownHostException`
   - LLM：`false`
3. 验收脚本最初没有真正保存为 `.ps1` 文件，而是逐行粘贴执行，导致前面失败后仍能执行最后的“通过”输出。
4. 关闭终端后环境变量丢失，应用也随终端结束。
5. 项目代码曾在辅助工作目录与正式仓库之间产生路径认知混乱，最终确认正式仓库为 `C:\javaProject\research-pilot`。

### 修复结果

- Maven Wrapper 改为先读取 `$MAVEN_M2_ITEM`，再安全判断 `Target` 是否为空。
- `start-local.ps1` 在同一进程中：
  - 验证 MySQL 和 Redis 端口
  - 安全询问密码和 API Key
  - 设置当前进程环境变量
  - 运行自动测试
  - 启动 Spring Boot
- `verify-phase1.ps1` 作为完整文件执行，任一检查失败即返回非零退出码。
- Redis Host 明确要求填写真实 IP 或主机名，不能填写 URL 或占位文字。
- 明确 PowerShell 环境变量是进程级配置；关闭终端后需要重新运行启动脚本。

### 学习与复盘

- 手工逐条执行验收命令容易造成“局部失败、最终仍打印成功”的假象，应使用可返回退出码的完整脚本。
- `/actuator/health=UP` 只代表应用存活，不能证明 MySQL、Redis 和模型均可用。
- 基础 Redis 连通与 RediSearch 向量能力可用是两件不同的事。
- 启动脚本必须基于 `$PSScriptRoot` 定位仓库，避免依赖调用者当前目录。

### 后续任务

- 在全新终端完成真实环境验收。
- 修复文档、提交、标签和远程同步。

## 2026-07-17｜第一阶段验收与里程碑发布

### 当日目标

- 在真实环境中完成第一阶段验收。
- 完善 README、技术决策和忽略规则。
- 创建正确的 Git 提交、里程碑标签并同步 GitHub。

### 实际进展

- 10:21 完成 `scripts/start-local.ps1` 的最终整理。
- 10:23 完成自动测试：

| 测试类 | 数量 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `GlobalExceptionHandlerTest` | 12 | 0 | 0 | 0 |
| `ChatServiceImplTest` | 7 | 0 | 0 | 0 |
| `SystemStatusServiceImplTest` | 2 | 0 | 0 | 0 |
| `ResearchPilotApplicationTests` | 1 | 0 | 0 | 0 |
| **合计** | **22** | **0** | **0** | **0** |

- 完成接口验收：
  - Actuator：`UP`
  - Swagger UI：HTTP 200
  - MySQL：`UP`
  - Redis：`UP`
  - LLM configured：`true`
  - 连续三次真实模型调用成功
  - 空消息：HTTP 400
  - 4001 字符消息：HTTP 400
- 完善 `.gitignore`：
  - `.idea/`
  - `target/`
  - `.claude/`
  - `response.json`
  - `scripts/*.broken`
  - `qdrant-storage/`
- 完成 Qdrant 技术决策文档。
- 修复 README 和 ADR 的编码与 Markdown 格式。
- 11:06 创建第一阶段提交：
  - `3c84ef4a6dd6043320c95bc9655701a52d52ce3b`
  - `feat: complete phase-one engineering loop`
- 11:07 创建 annotated tag：
  - `v0.1.0-phase1`
  - 标签最终正确指向 `3c84ef4`
- 将 Git 远程地址从 SSH 切换为 HTTPS，完成浏览器认证。
- 成功推送：
  - `main`
  - `v0.1.0-phase1`
- 最终状态：
  - `main` 与 `origin/main` 同步
  - Git 工作区干净

### 当日主要故障

1. README 因 Windows PowerShell 5.1 默认编码被写成乱码，部分清单行合并。
2. ADR 初稿将 Markdown 标题和列表写成 `\#`、`\-`。
3. README 内容嵌入 PowerShell here-string 时，Markdown 三反引号与外层代码块冲突，复制内容被截断。
4. 第一次创建 `v0.1.0-phase1` 时，代码尚未提交，标签错误指向初始化提交 `11f6bff`。
5. GitHub SSH 推送失败：
   - `github.com` 被代理解析到 `198.18.0.156`
   - SSH 22 端口连接被关闭
   - 本地分支显示 `[ahead 1]`

### 修复结果

- 使用 UTF-8 无 BOM 重新生成 README 和 ADR。
- README 内部代码围栏改用 `~~~`，避免嵌套代码块截断。
- 删除错误标签，先提交代码，再重新创建标签。
- 使用 `git rev-parse HEAD` 与 `git rev-list -n 1 v0.1.0-phase1` 验证标签与 HEAD 一致。
- 将远程地址切换为：
  - `https://github.com/DJ1012-H/research-pilot.git`
- 通过浏览器认证后成功推送主分支和标签。

### 验收结论

第一阶段于 2026-07-17 提前完成，不需要在 2026-07-18 为日期形式重复验收。

完成标准全部满足：

- [x] 应用启动成功
- [x] MySQL 状态为 UP
- [x] Redis 状态为 UP
- [x] `/api/chat` 返回真实模型结果
- [x] 参数错误返回统一结构
- [x] 22 个自动测试全部通过
- [x] Swagger UI 和 Actuator 可访问
- [x] Git 工作区干净
- [x] 敏感信息未提交
- [x] 里程碑提交和标签已推送 GitHub

## 2026-07-17｜提前完成文献检索数据流与接口契约（原计划 07-19）

### 当日目标

- 画出“查询规划 → 检索 → 去重 → 核验 → 入库 → 返回”的类级数据流。
- 定义 `SearchRequest`、`SearchPlan`、`PaperDTO`、`VerificationResult` 和 `SearchResponse`。
- 设计 `POST /api/literature/search` 请求与响应契约。
- 明确 OpenAlex 与 Crossref 的职责边界，并冻结后续开发依赖的核心结构。

### 实际进展

- 新增 `literature` 业务包，采用“按业务分包、包内轻量分层”。
- 实现五个核心契约：
  - `SearchRequest`：自然语言主题和显式年份、数量过滤。
  - `SearchPlan`：已校验、可执行的单一 OpenAlex 检索计划。
  - `PaperDTO`：统一候选论文元数据，包含作者、ISSN、摘要、语言和关键词。
  - `VerificationResult`：整体状态、字段级结果和可解释证据。
  - `SearchResponse`：任务状态、统计和正式论文结果。
- 将 `PaperDTO` 与 `VerificationResult` 分离，由 `SearchResponse.PaperResult` 在最终响应中组合。
- 为最终结果建立数据结构级门禁：
  - DOI 必须存在。
  - 核验状态只能是 `VERIFIED` 或达到门槛的 `PARTIALLY_VERIFIED`。
- 完成中文设计文档 `docs/design/literature-search-contract.md`，记录：
  - 五个契约的字段、空值含义和边界。
  - 已构建与待构建类的完整数据流。
  - 包结构、模块职责和依赖规则。
  - 07-20～07-25 的类级实现计划。
  - 契约冻结与破坏性变更 ADR 规则。

### 已确认业务策略

- “近五年”包含当前年份；2026 年解析为 2022～2026。
- 显式结构化字段优先于自然语言推断。
- 第一版只执行一个主要 OpenAlex 检索式。
- 默认不限制论文语言。
- 预印本只进入候选池，第一版不进入正式结果。
- `PARTIALLY_VERIFIED` 进入正式结果必须满足 DOI 一致、标题高度一致、至少第一作者一致；只允许年份可解释差异或期刊字段缺失。
- 搜索成功但零篇通过核验时返回 HTTP 200、`NO_VERIFIED_RESULTS` 和空列表。

### 验证结果

执行 `mvnw.cmd test`：

| 测试类 | 数量 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `GlobalExceptionHandlerTest` | 12 | 0 | 0 | 0 |
| `LiteratureContractTest` | 9 | 0 | 0 | 0 |
| `ResearchPilotApplicationTests` | 1 | 0 | 0 | 0 |
| `ChatServiceImplTest` | 7 | 0 | 0 | 0 |
| `SystemStatusServiceImplTest` | 2 | 0 | 0 | 0 |
| **合计** | **31** | **0** | **0** | **0** |

- 编译：成功。
- Spring 上下文：加载成功。
- 敏感信息：只允许环境变量名、占位说明和公开服务地址；提交前执行独立扫描。
- `POST /api/literature/search`：当前只完成契约，尚未声明为可调用接口。

### 技术决策与学习

- `SearchAgent` 只负责把自然语言转换为查询计划，不承担完整工作流编排。
- `LiteratureSearchService` 后续负责确定性流程编排；外部调用、去重、核验和持久化分别由独立模块承担。
- LLM 原始输出使用内部 `SearchPlanDraft`，必须经过 Java `SearchPlanValidator` 才能生成可执行 `SearchPlan`。
- OpenAlex 负责发现候选，Crossref 负责提供 DOI 和出版元数据核验证据。
- 外部 DTO、数据库 Entity 和核心契约必须分离，避免外部结构变化反向污染业务层。

### 下一步

- 07-20 实现 `OpenAlexClient`、外部响应 DTO 和 `OpenAlexPaperMapper`。
- 保持五个核心契约字段和语义不变；确需破坏性变更时先写 ADR。

## 2026-07-19｜提前完成 OpenAlex 候选论文检索模块（原计划 07-20）

### 当日目标

- 将可信 `SearchPlan` 转换为受限、可执行的 OpenAlex `/works` 查询。
- 使用 Spring `RestClient` 完成单次候选召回，不引入 WebFlux。
- 将 OpenAlex 外部响应转换为项目内部 `CandidatePaper`。
- 使用 Mock HTTP 完成自动测试，并以固定英文检索词执行一次真实数据验收。

### 实际进展

- 新增 `OpenAlexQueryFactory`，负责日期、文献类型、排序和候选数量映射。
- 新增 `OpenAlexSearchPort` 与 `OpenAlexSearchAdapter`，使业务层不依赖
  `OpenAlexClient` 或外部 DTO。
- 新增 `OpenAlexClient`，只负责 HTTP 请求和 JSON 反序列化。
- 新增带 Jackson snake_case 映射和未知字段容错的 OpenAlex 外部 DTO。
- 新增 `OpenAlexPaperMapper`，完成：
  - OpenAlex ID 和 DOI 标准化。
  - 标题、作者名和来源名称空白归一化。
  - `abstract_inverted_index` 摘要还原。
  - `best_oa_location` 到 `primary_location` 的地址回退。
  - DOI、作者、来源、摘要和开放获取地址缺失时的空值容错。
- 新增 `CandidatePaper`、`OpenAlexQuery` 和 `OpenAlexSearchResult`，
  分别表示内部候选论文、受限查询和召回统计。
- 将 OpenAlex 配置和异常映射接入现有 Spring 配置与
  `GlobalExceptionHandler`。

### 真实 API 验收

- 固定英文检索词：`protein structure prediction`。
- 过滤条件：2021～2026 年、`article`。
- OpenAlex 返回总匹配数：368,715。
- 单次召回并成功解析 5 篇真实候选论文，包括：
  - *Highly accurate protein structure prediction with AlphaFold*
  - *Highly accurate protein structure prediction for the human proteome*
  - *Accurate prediction of protein structures and interactions using a three-track neural network*
  - *Evolutionary-scale prediction of atomic-level protein structure with a language model*
  - *The trRosetta server for fast and accurate protein structure prediction*
- 真实数据中存在缺失摘要的论文，映射器按预期保留论文并将摘要置空，
  未导致整批失败。

### 故障与修复

1. 初次真实请求使用 `-relevance_score` 时，OpenAlex 返回 HTTP 400，
   提示该字段无效。
2. 分别验证基础查询、`search`、`filter`、`sort` 和 `select` 后，定位到
   当前 API 的降序排序格式为 `field:desc`。
3. 最终映射改为：
   - `RELEVANCE` → `relevance_score:desc`
   - `NEWEST` → `publication_date:desc`
   - `MOST_CITED` → `cited_by_count:desc`
4. 三种排序均通过真实 API 验证，并同步更新 Mock HTTP 测试。

### 配置与密钥治理

- `OpenAlexProperties` 中的公开 Base URL、连接超时、读取超时和默认分页大小
  不属于敏感信息，`apiKey` 字段没有代码默认值。
- 排查发现本地曾误将真实 Key 写入 `application.yml` 的环境变量默认值。
- 已将真实 Key 移至被 `.gitignore` 排除的本地 `.env`。
- `application.yml` 只保留 `${OPENALEX_API_KEY:}`，`.env.example` 只保留
  `CHANGE_ME`。
- 安全扫描确认真实 Key 不存在于当前受版本控制文件和 Git 历史。
- 客户端和异常消息不记录包含 `api_key` 的完整请求 URL。

### 验证结果

| 验证 | 通过 | 失败 | 错误 |
|---|---:|---:|---:|
| OpenAlex 定向自动测试 | 17 | 0 | 0 |
| Maven 全量自动测试 | 67 | 0 | 0 |
| 真实 OpenAlex 冒烟测试 | 1 | 0 | 0 |

- Maven 最终状态：`BUILD SUCCESS`。
- 自动测试不调用真实 OpenAlex；真实测试使用一次性测试入口，执行后已删除。
- `git diff --check` 通过。
- 未发现临时联网测试文件、重复模型或无关代码修改。

### 范围边界

- 本次只完成 OpenAlex 候选论文召回。
- 尚未实现 `SearchAgent`、`LlmQueryPlanner`、`SearchPlanDraft`、
  `SearchPlanValidator` 和文献检索 Controller 的运行时编排。
- 尚未实现 Crossref 核验、去重、最终排名、持久化、缓存和前端。

### 下一步

- 实现 LLM 查询草稿与 Java 规则校验，形成可信 `SearchPlan`。
- 由 `SearchAgent` 通过 `OpenAlexSearchPort` 编排候选召回。
- 保持 OpenAlex 外部 DTO 不进入 Controller 或后续业务层。

## 2026-07-20｜提前完成 Search Agent 可信查询规划（原计划 07-21）

### 当日目标

- 将原始模型输出限制在明确的 JSON 契约内。
- 建立 LLM 草稿到可信 `SearchPlan` 的 Java 校验边界。
- 实现 `SearchAgent` 与最多一次结构化输出修正。
- 将可信计划接入已有 OpenAlex 候选检索能力。

### 实际进展

按照修订后的八阶段方案依次完成，未跨阶段提前引入 Crossref、持久化、缓存、
向量存储或前端：

1. 重构 `ModelInvoker`，统一聊天和检索规划的模型调用、供应商异常分类与脱敏日志。
2. 新增 `SearchPlanDraft`、`SearchSort`、`LanguageCode`，扩展可信 `SearchPlan`。
3. 新增版本化 Prompt、JSON Schema 和模型结构化输出专用严格 Mapper。
4. 固定实现 JSON 语法、Schema、DTO 映射、业务规则、安全规则五层校验顺序。
5. 实现 `LlmQueryPlanner` 与 `SearchAgent`，结构化输出最多修正一次。
6. 将语言和排序意图映射到 OpenAlex filter、sort、DTO 与内部候选论文。
7. 实现 `LiteratureSearchService`、`LiteratureSearchController` 和受控异常响应。
8. 增加单元测试、端到端阻断测试、架构约束和真实 Spring MVC 序列化测试。

### Search Agent 可信边界

- 模型实际返回值始终是原始 `String`。
- 原始字符串依次通过 JSON 语法、JSON Schema 和 DTO 严格映射后，才能成为
  `SearchPlanDraft`。
- `SearchPlanDraft` 再通过业务规则和执行前安全校验，才能生成 `SearchPlan`。
- `originalQuery` 只来自 `SearchRequest`；`candidateLimit` 只由 Java 按配置计算。
- HTTP 显式年份和数量优先于模型推断，模型推断优先于 Java 默认值。
- 安全失败不可重试；模型认证、超时、限流等供应商错误沿用 `ModelInvoker`
  分类，不进入结构化输出修正。
- `SearchAgent` 不依赖 OpenAlex；`LiteratureSearchService` 负责可信计划到一次
  OpenAlex 候选召回的确定性编排。

### OpenAlex 与接口增强

- `SearchPlan.sort` 确定性映射为相关性、最新或高被引排序。
- `LanguageCode.EN/ZH` 映射为 `language:en`、`language:zh` 或
  `language:en|zh`。
- OpenAlex 外部响应增加 `language` 字段，允许数据源返回空值。
- `POST /api/literature/search` 已具备运行时入口。
- 当前未接入 Crossref 核验，因此召回候选只进入统计，正式论文列表仍为空。

### JSON 时间序列化故障与修复

#### 故障现象

浏览器调用 `POST /api/literature/search` 时返回 Spring Boot 基础 HTTP 500。
日志分别可能指向 `SearchResponse.completedAt` 或
`ApiErrorResponse.timestamp` 无法序列化。

#### 根因

`jackson-datatype-jsr310` 已由 `spring-boot-starter-json` 传递引入，并非依赖
缺失。真正原因是模型专用严格 `ObjectMapper` 被注册为 Spring Bean，触发
Spring Boot 的条件装配回退，使它成为 MVC 使用的全局 Mapper；该严格 Mapper
没有注册 Java Time 模块。

#### 解决方案

- 保留严格 `ObjectMapper` 工厂方法和既有公开构造入口。
- 新增不继承 `ObjectMapper` 的 `StructuredOutputMapper` 窄职责包装器。
- 容器只注册包装器，使 Spring Boot 恢复创建带 Java Time 支持的
  `jacksonObjectMapper`。
- 新增真实 Spring 上下文测试，同时覆盖成功响应 `completedAt` 和错误响应
  `timestamp`。

### 验证结果

| 验证 | 通过 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| Mapper 与五层校验聚焦回归 | 33 | 0 | 0 | 0 |
| Maven 全量自动测试 | 164 | 0 | 0 | 0 |
| Maven `clean verify` | 164 | 0 | 0 | 0 |
| 真实 Spring MVC 时间序列化场景 | 3 | 0 | 0 | 0 |

- `mvn test`：`BUILD SUCCESS`。
- `mvn clean verify`：从空 `target` 重新编译、测试并成功生成可运行 JAR。
- 运行时安全冒烟：在显式关闭 LLM/OpenAlex 时，文献接口返回受控
  HTTP 503 `MODEL_NOT_CONFIGURED`，ISO-8601 `timestamp` 正常序列化，不再返回
  基础 500。
- 成功响应 `SearchResponse.completedAt` 由真实 Spring MVC 上下文和模拟业务
  服务验证。
- 架构测试确认 Controller 和 `SearchAgent` 不直接依赖 `OpenAlexClient`。
- `git diff --check`、尾随空格和敏感值模式扫描均通过。

### 范围边界

- 已完成的是原计划 07-21 的 Search Agent 查询规划，以及支撑该链路的单
  OpenAlex 运行时编排。
- 未使用当前环境中的真实 LLM 凭据执行外部成功链路；需重启开发者 IDE 中的
  8080 服务后，在其外部配置环境下再次执行 Swagger 验收。
- 尚未实现 Crossref、去重、核验、持久化、缓存、异步任务、前端和 RAG。

### 下一步

- 使用 Flyway 创建检索任务、论文和核验记录表。
- 接入 Crossref，并实现确定性的 DOI、标题、作者、年份和来源核验。
- 保持“小步提交、每天推送、阶段打标签”的交付节奏。

## 6. 故障台账

| 编号 | 日期 | 故障现象 | 根因 | 解决方案 | 验证与预防 |
|---|---|---|---|---|---|
| F-001 | 07-16 | 正式代码目录不明确 | 辅助工作目录与正式 Git 仓库并存 | 确认 `C:\javaProject\research-pilot` 为唯一正式仓库 | 后续所有脚本通过 `$PSScriptRoot` 定位仓库 |
| F-002 | 07-16 | `mvnw.cmd` 报 `Cannot index into a null array` | PowerShell 5.1 对空 `Target[0]` 的处理不同 | 先保存 `Get-Item` 结果，再判断 `Target` 是否为空 | `mvnw.cmd -version` 和测试成功 |
| F-003 | 07-16 | MySQL `DOWN / SQLException` | 应用进程没有获得正确数据库配置或密码 | 同一启动脚本安全读取密码并设置环境变量；使用专用账号执行 `SELECT 1` | `/api/system/status` 返回 MySQL `UP` |
| F-004 | 07-16 | Redis `DOWN / UnknownHostException` | `REDIS_HOST` 未设置、使用占位文字或填写格式错误 | 使用真实 IP/主机名；启动前检查 TCP 端口 | Redis `PING` 返回 `PONG`，状态为 `UP` |
| F-005 | 07-16 | `llmConfigured=false`，聊天返回 503 | `LLM_ENABLED` 和模型变量未在启动进程中生效 | 启动脚本在启动 JVM 前设置模型变量 | 模型状态为 `true`，连续三次调用成功 |
| F-006 | 07-16 | Actuator 为 UP，但依赖为 DOWN | Actuator 数据库和 Redis健康检查默认关闭；应用存活不等于依赖可用 | 使用 `/api/system/status` 做显式依赖验收 | 验收脚本同时检查两个端点 |
| F-007 | 07-16 | 验收脚本不存在或失败后仍打印通过 | 脚本内容被逐行粘贴，没有作为完整 `.ps1` 文件执行 | 保存 `verify-phase1.ps1`，使用统一 try/catch 和退出码 | `$LASTEXITCODE=0` 才算通过 |
| F-008 | 07-16 | 关闭终端后配置和应用消失 | PowerShell 环境变量和前台应用均属于当前进程 | 重新运行 `start-local.ps1` | README 明确说明双终端流程 |
| F-009 | 07-16 | Redis 可连接但无法使用 `FT.CREATE` | 当前是基础 Redis，没有 RediSearch/Search 模块 | Redis 只承担缓存和状态；第三阶段引入 Qdrant | ADR-001 已记录 |
| F-010 | 07-17 | README 中文乱码和行合并 | PowerShell 5.1 默认编码错误读取 UTF-8 文档后重新写入 | 使用 UTF-8 无 BOM 显式写入 | `Get-Content -Encoding UTF8` 显示正常 |
| F-011 | 07-17 | PowerShell/Markdown 混合脚本复制被截断 | README 内三反引号关闭了外层代码块 | README 内围栏改用 `~~~` | 文档结构和 Git diff 正常 |
| F-012 | 07-17 | 里程碑标签指向旧提交 | 在阶段代码提交前创建了标签 | 删除旧标签，先提交，再重新打标签 | HEAD 与 tag peeled commit 均为 `3c84ef4` |
| F-013 | 07-17 | GitHub SSH 22 端口连接关闭 | 代理/TUN 将 GitHub 解析到 `198.18.0.0/16` 虚拟地址，未转发 SSH 22 | 远程地址改为 HTTPS，使用浏览器认证 | `main` 和 tag 均成功推送 |
| F-014 | 07-19 | OpenAlex 真实 Key 被误写入 `application.yml` 默认值 | 将本地秘密与可提交配置混用 | Key 迁移到被忽略的 `.env`，YAML 只保留环境变量引用 | 当前受控文件和 Git 历史均未发现真实 Key |
| F-015 | 07-19 | OpenAlex 排序请求返回 HTTP 400 | 当前 API 不接受 `-relevance_score` 降序格式 | 改用 `field:desc` 并逐项执行真实请求 | 三种排序和最终候选映射均验证成功 |
| F-016 | 07-20 | 文献接口返回基础 HTTP 500，`Instant` 无法序列化 | 模型专用严格 `ObjectMapper` 抑制了 Boot MVC Mapper 自动装配 | 将严格 Mapper 封装为非 `ObjectMapper` Bean，恢复 Boot `jacksonObjectMapper` | 成功与错误响应集成测试通过，运行时返回受控 503 |

## 7. 关键技术决策

### D-001｜正式仓库与版本策略

- 正式本地仓库：`C:\javaProject\research-pilot`
- GitHub：<https://github.com/DJ1012-H/research-pilot>
- 第一阶段提交：`3c84ef4`
- 第一阶段标签：`v0.1.0-phase1`
- 标签作为不可移动的可复现里程碑；后续开发继续在新提交上进行。

### D-002｜配置与密钥管理

- MySQL、Redis、模型和 OpenAlex 配置通过环境变量或外部配置提供。
- 密码和 API Key 在 PowerShell 中使用安全输入读取。
- 日志不记录消息正文、密码或 API Key。
- `.env.example` 只保存占位配置。
- 本地 `.env` 必须被 Git 忽略；Spring Boot 默认不自动加载该文件，启动时
  仍需由终端、IDE 或外部配置注入变量。

### D-003｜存储职责

- MySQL：最终可靠业务数据。
- Redis：缓存、短期任务状态、进度、限流与 TTL 数据。
- Qdrant：第三阶段的论文块向量和检索元数据。

### D-004｜为什么不在当前 Redis 上实现向量检索

- 当前 Redis 没有 RediSearch/Search 模块。
- 基础 Redis 连接成功不能证明 `FT.CREATE`、`FT.SEARCH` 或 KNN 可用。
- 为避免替换已稳定运行的 Redis，采用独立 Qdrant。
- MySQL 保持事实来源，Qdrant 索引必须能够重建。

### D-005｜健康检查分层

- `/actuator/health`：判断 Spring Boot 进程是否存活。
- `/api/system/status`：判断 MySQL、Redis 和模型配置是否可用。
- 自动验收同时检查二者，避免产生“应用 UP 即全系统可用”的错误结论。

### D-006｜模型错误分类

- 认证失败：502
- 超时：504
- 服务不可用：503
- 限流：503
- 模型不存在：502
- 请求被模型服务拒绝：502
- 其他模型服务错误：502
- 模型未配置：503
- 用户输入不合法：400

该分类让调用方可以区分业务参数错误、配置错误和外部服务故障。

### D-007｜Search Agent 是查询规划器，不是完整工作流

- `LlmQueryPlanner` 只返回模型原始字符串。
- `SearchAgent` 只创建上下文、调用五层校验、控制一次修正重试并返回可信
  `SearchPlan`。
- OpenAlex 调用、候选统计和响应组装由 `LiteratureSearchService` 负责。
- 去重、Crossref 核验、持久化和 RAG 保持为后续独立职责。

### D-008｜模型 JSON 与 MVC JSON 使用隔离配置

- 模型结构化输出需要拒绝未知字段、类型宽松转换、单值转数组和数字转枚举。
- HTTP JSON 继续使用 Spring Boot 自动配置的 MVC Mapper 及其 Java Time 模块。
- 严格 Mapper 只通过 `StructuredOutputMapper` 暴露读取和映射能力，不作为
  `ObjectMapper` Bean 注册。
- 不通过重复添加 `jackson-datatype-jsr310` 掩盖 Bean 自动装配冲突。

## 8. 第一阶段测试与验收证据

### 自动测试

- 总数：22
- 失败：0
- 错误：0
- 跳过：0
- 测试报告时间：2026-07-17 10:23

### 手工与脚本验收

| 检查项 | 预期 | 实际结果 |
|---|---|---|
| Actuator | `UP` | 通过 |
| Swagger UI | HTTP 200 | 通过 |
| MySQL | `UP` | 通过 |
| Redis | `UP` | 通过 |
| LLM configured | `true` | 通过 |
| 真实模型调用 | 连续三次非空 | 通过 |
| 空消息 | HTTP 400 | 通过 |
| 4001 字符消息 | HTTP 400 | 通过 |
| Git 工作区 | 无未提交修改 | 通过 |
| GitHub 主分支 | 与本地同步 | 通过 |
| 里程碑标签 | 正确指向阶段提交 | 通过 |

## 9. 当前进展与未完成范围

### 已完成

- Spring Boot 工程闭环
- Controller–Service–Mapper 分层
- MyBatis-Plus 基础配置
- MySQL 专用数据库账号和状态探针
- 远程 Redis 状态探针
- DeepSeek 真实模型调用
- 模型错误分类
- 统一异常响应
- Swagger UI
- Actuator
- 164 个自动测试（其中第一阶段验收测试 22 个）
- 本地启动脚本
- 第一阶段验收脚本
- Qdrant 技术决策
- Git 提交、标签和 GitHub 同步
- 文献检索类级数据流和模块职责
- 五个文献检索核心契约及冻结规则
- OpenAlex 候选论文检索、响应映射和异常治理
- 固定英文检索词的真实 OpenAlex 数据验收
- 公共 `ModelInvoker` 与统一模型供应商错误映射
- Search Agent、版本化 Prompt 和最多一次结构化输出修正
- JSON 语法、Schema、DTO、业务和安全五层校验管线
- OpenAlex 语言过滤和可信排序映射
- 文献检索 Controller、单 OpenAlex 运行时编排和受控异常响应
- Spring MVC `Instant` 成功与错误响应序列化回归

### 尚未开始或尚未完成

- Crossref 元数据核验
- DOI/标题去重
- 可信度评分
- 论文、检索任务和核验记录表
- Flyway 数据库迁移
- Embedding
- Qdrant 部署与接入
- 向量检索
- 可信 RAG
- 异步工作流
- 前端
- Docker 化和完整部署

## 10. 风险与技术债

### 10.1 GitHub 用户可复现性

当前脚本主要面向 Windows PowerShell，README 示例仍包含开发者本机绝对路径。为了实现“其他用户从 GitHub 下载后即可按 README 使用”，后续必须：

- 将 README 命令改为仓库相对路径。
- 增加 Windows、Linux/macOS 启动说明。
- 增加 Java、MySQL、Redis 和 Qdrant 的版本要求。
- 提供无秘密的完整 `.env.example`。
- 提供 Docker Compose 作为推荐的一键依赖方案。
- 增加首次运行检查和故障排查章节。
- 在干净环境中按 README 重新验证。

### 10.2 提交粒度

第一阶段只有初始化提交和阶段完成提交，中间演进主要依靠文件时间还原。后续应做到：

- 每天至少一个职责单一的提交。
- 功能、测试、文档尽量在同一小提交中闭环。
- 不把数天修改集中成一个超大提交。

### 10.3 外部服务依赖

- DeepSeek API 可能出现限流、超时和服务异常。
- OpenAlex、Crossref 后续同样需要超时、限流、重试和可测试的 Mock。
- Redis 位于远程 CentOS，网络中断时应用需要可读的降级信息。

### 10.4 数据一致性

第三阶段接入 Qdrant 后：

- MySQL 是事实来源。
- Qdrant 写入必须幂等。
- `paperId`、`chunkId`、`pointId` 必须稳定。
- 更换 Embedding 模型或维度后需要新建 Collection。
- 索引必须支持从 MySQL 重建。

### 10.5 自动化

当前没有记录 GitHub Actions 等持续集成流程。后续应至少自动执行：

- Maven 测试
- 编译
- 敏感信息检查
- Markdown/配置基本校验

## 11. 下一阶段计划

第二阶段目标是完成“真实文献检索与可信核验”：

1. [x] 定义 SearchRequest、SearchPlan、PaperDTO、VerificationResult 和 SearchResponse。
2. [x] 设计 `POST /api/literature/search`。
3. [x] 接入 OpenAlex，模型只生成检索策略，不生成论文列表。
4. [x] 实现 Search Agent、五层可信校验和单 OpenAlex 运行时编排。
5. [ ] 使用 Flyway 创建检索任务、论文和核验记录表。
6. [ ] 接入 Crossref 核验 DOI 和元数据。
7. [ ] 实现 DOI/标题去重。
8. [ ] 建立可解释的可信度评分。
9. [ ] 使用固定研究主题建立回归测试。

第二阶段完成标准：

- 输入中文研究主题后能够返回真实论文。
- 每篇论文包含 DOI、来源、可信度分数和核验状态。
- 结果写入 MySQL。
- 外部 API 失败时具有明确任务状态和错误信息。

## 12. 后续每日追加模板

```markdown
## YYYY-MM-DD｜当日主题

### 当日目标

-

### 实际进展

-

### 代码与配置变更

-

### 验证结果

- 测试：
- 接口：
- 数据：
- Git：

### 故障记录

#### 故障现象

-

#### 根因

-

#### 解决方案

-

#### 验证与预防

-

### 技术决策与学习

-

### 风险与技术债

-

### 下一步

-
```

## 13. 里程碑索引

| 里程碑 | 日期 | Commit | Tag | 状态 |
|---|---|---|---|---|
| 仓库初始化 | 2026-07-13 | `11f6bff` | — | 完成 |
| 第一阶段工程闭环 | 2026-07-17 | `3c84ef4` | `v0.1.0-phase1` | 完成并已推送 |

## 2026-07-21｜Crossref Client 基础与访问治理

### 实际进展

- 增加默认关闭的 Crossref 配置、`RestClient`、最小响应 DTO、失败分类和受控异常响应。
- 实现公平并发闸门、本地速率控制、`Retry-After` 优先的有限指数退避，以及可注入的时钟/休眠器测试缝。
- 将 OpenAlex 候选的非空 DOI 稳定去重后按预算顺序交给 Crossref；404 继续，来源不可用时停止并保留已有元数据。
- 将 Crossref 摘要接入既有检索日志和响应消息；不把外部元数据或任何正式论文写入 `SearchResponse.papers`。
- `.env.example` 只增加占位邮箱与空 Token；真实冒烟测试仅在 `CROSSREF_SMOKE_ENABLED=true` 时运行。

### 范围边界

- 尚未实现 DOI 规范化、标题回退、字段比较、验证状态、正式结果、论文去重、持久化、缓存、RAG 或异步任务。
- 自动测试不访问真实 Crossref；Swagger 真实验收需要本地配置真实的 `CROSSREF_MAILTO`、LLM 与 OpenAlex 后手工执行。

### 验证结果

- Crossref 与检索链定向回归：通过。
- `mvn test`：184 通过、0 失败、0 错误、1 跳过（默认关闭的真实 Crossref 冒烟测试）。
- `mvn clean verify`：184 通过、0 失败、0 错误、1 跳过，并成功重新打包 JAR。

## 2026-07-22｜共享 DOI 规范化与 Crossref 精确查询收敛

### 当日目标

- 提取共享 `DoiNormalizer`，让 OpenAlex 映射、Crossref 请求和 Crossref 响应采用同一规范化入口。
- 在不扩展到标题回退或字段级核验的前提下，收紧非法 DOI 与非预期 HTTP 响应边界。

### 实际实现

- 新增共享 DOI 规范化组件：支持原始 DOI、`doi:`、`doi.org`、`dx.doi.org`、HTTP/HTTPS、大小写和外层空白。
- 对明确的外层中文句号、逗号、分号、引号与未匹配右括号进行有限清理；保留 DOI 后缀内部合法标点，不使用粗暴的尾部标点删除。
- OpenAlex 每个 Work 只计算一次规范化 DOI，`CandidatePaper.doi` 与 DOI 回退 Landing Page 使用同一结果；缺失或非法 DOI 保持为 `null`。
- Crossref 保持配置错误优先级，在请求门控、重试与 HTTP 调用前规范化并校验 DOI；重试循环只使用规范化值，路径继续由 URI Builder 安全编码。
- Crossref 专用 HTTP Client 禁止自动跟随重定向，非预期 3xx 复用 `INVALID_RESPONSE`，不会伪装成 404 或触发无边界重试。
- Crossref 响应 DOI 在 Adapter 边界通过同一 Normalizer；无效响应 DOI 转换为 `INVALID_RESPONSE`，不进入 `CrossrefWorkMetadata`。
- 建立 `eval/crossref-verification-v1` 离线评测数据集骨架，固定案例 Schema、来源 provenance、父子 lineage、DOI 规范化变异和元数据扰动清单。
- 种子集保持为空，等待人工批准且可追踪的候选与 Crossref 快照；不以虚构书目数据填充 ground truth。

### 主要代码与测试变更

- 新增 `DoiNormalizer` 与参数化测试，覆盖常见包装形式、非法输入、中文外层标点、未匹配右括号和平衡括号保留。
- 更新 `OpenAlexPaperMapper`、`CrossrefClient`、`CrossrefConfig`、`CrossrefSearchAdapter` 的构造器注入和行为测试。
- 修正旧测试中的 `10/example`、`10/missing` 为具备基本 DOI 结构的 `10.1000/example`、`10.1000/missing`。
- 增加请求前拦截、门控/重试零交互、3xx、空体、非法结构、响应 DOI 规范化与受控异常不伪装为未找到的回归测试。
- 新增评测数据集结构测试，校验 JSON Schema、fixture 引用、SHA-256、评审状态与变异谱系约束。

### 验证结果

- DOI、Crossref 与评测结构定向测试：50 通过、0 失败、0 错误、0 跳过。
- Crossref 与检索链定向回归：31 通过、0 失败、0 错误、1 跳过。
- `mvn test`：218 通过、0 失败、0 错误、1 跳过。
- `mvn clean verify` 最终：218 通过、0 失败、0 错误、1 跳过，并成功重新打包 JAR。
- 真实 Crossref 冒烟测试未执行；环境开关未启用，测试按设计跳过。

### 范围边界与技术决策

- DOI 语法规范化只证明输入可安全识别，不证明 DOI 已注册或论文已通过核验。
- Crossref lookup `FOUND` 不等于 `VerificationResult.VERIFIED`，本次修改不向 `SearchResponse.papers` 写入正式论文。
- 评测目录只是离线数据结构骨架，尚未实现 benchmark runner、字段核验器或在线数据采集，不覆盖 HTTP、重试、预算和编排行为。
- 今日未实现标题回退、歧义候选表示、字段级核验、正式论文准入、候选去重、可信度评分、持久化、缓存、Agent 状态机或 RAG。
- 复用既有 `INVALID_RESPONSE`，避免为 3xx 或非法响应 DOI 增加不必要的失败类型；仅修改 Crossref 专用重定向策略，不影响其他集成。

## 2026-07-23｜Crossref 受控书目回退查询与候选歧义表示

### 实际进展

- 为缺少有效 DOI 的 OpenAlex 候选增加标题资格守卫：仅接受非空、长度受限且具备足够词元信息的标题；不合格输入不会发起 Crossref 请求。
- 增加 Crossref `query.bibliographic` 查询模型、HTTP 客户端解析和适配器映射；请求仍复用既有速率、重试、超时与故障分类边界。
- 在查询编排中保持 DOI 优先：有效 DOI 只走精确 DOI 查询，DOI 未找到时不降级为标题；仅无有效 DOI 的候选才可走书目回退。
- 为书目查询结果显式表示 `NOT_FOUND`、`FOUND_SINGLE` 与 `FOUND_MULTIPLE`，保留候选列表与歧义信息，不自动挑选或写入正式论文。
- 新增资格守卫、客户端、适配器与查询编排回归测试，覆盖无效标题、查询参数、歧义候选、预算、不可用来源及 DOI/标题分流。

### 验证结果

- Crossref 书目回退定向验收：46 项通过。
- `mvn test`：249 项通过，0 失败，0 错误，1 项按显式开关控制的真实 Crossref 冒烟测试跳过。
- `mvn clean verify`：同上，并成功完成打包校验。

### 范围边界

- `FOUND` 仍不等于 `VERIFIED`；`SearchResponse.papers` 继续保持为空，未引入自动选择、字段级核验或正式论文准入。
- 未实现持久化、缓存、Agent 状态机、RAG 或在线测评采集。
- 本次代码提交不包含 `eval/` 下的测评数据集变更；相关文件继续仅保留在本地工作区。

## 2026-07-24｜Crossref 固定回放与应用/评测分支分离

### 实际进展

- 在 `main` 增加 `CrossrefPaperMapper`，将 Crossref 外部 DTO 映射到既有 `CrossrefWorkMetadata`；出版日期按 print、online、issued、created 的顺序回退。
- 保持 `CrossrefWorkMetadata` 的全部字段和 public record 构造器不变；外部 DTO 可以接收 URL，但 Mapper 不映射 URL，也不创建仅保存 URL 的重复内部模型。
- 普通测试复用一份经过人工审核的真实 Crossref 响应快照，覆盖 DTO 反序列化与 Mapper 回放。原始响应未重新抓取或改写：Captured date 为 2026-07-22，Integrated/reused date 为 2026-07-24；审核日期无法确认，未填写。
- 将应用代码、普通测试和两份计划文档归入 `main`；将 `eval/crossref-verification-v1/**` 与其结构测试归入同名评测分支，避免评测数据资产混入主线。
- 为原先混合的开发历史创建只读保留标签 `archive/crossref-mixed-20260724-retain-until-20261022`，保留至 2026-10-22；不执行 Git 垃圾回收或历史改写。

### 验证结果

- `main`：`mvn clean verify` 成功，250 通过、0 失败、0 错误、2 跳过（默认关闭的真实 Crossref smoke test），并完成 JAR 打包。
- `eval/crossref-verification-v1`：`mvn clean test` 成功，255 通过、0 失败、0 错误、2 跳过。
- 固定快照用于 DTO deserialization、Mapper replay 和离线回归；本次常规测试未访问真实 Crossref，live smoke 结果不会覆盖 Fixture。

### 范围边界

- `FOUND` 仍不等于 `VERIFIED`，不会向 `SearchResponse.papers` 写入正式论文。
- 本阶段未实现字段级核验、去重、可信度评分、持久化、缓存、Agent 状态机、RAG 或在线 benchmark runner。
- URL 暂未进入内部契约；只有出现明确展示或跳转需求时，才通过兼容性设计扩展。

## 2026-07-25｜统一字段标准化与 Crossref 调用前去重

### 当日目标

- 在 OpenAlex 候选映射之后、Crossref 外部调用之前完成确定性的本地标准化和保守去重。
- 让重复候选不再重复消耗 DOI 查询或书目回退预算，同时保留原始候选和可解释的去重证据。
- 只建立后续字段相似度所需的证据结构，不提前实现阈值校准或最终核验状态。

### 实际进展

- 新增标题、第一作者、来源、OpenAlex Work ID 标准化器，并继续复用既有共享 `DoiNormalizer`；标准化值与原始 `CandidatePaper` 分离保存。
- 新增 `CandidateNormalizationService`，生成稳定候选标识、标准化 DOI/OpenAlex ID/标题/第一作者/年份/来源及输入顺序。
- 新增 `CandidateDeduplicationService`，按 `DOI > OpenAlex ID > 精确书目键（标题 + 第一作者 + 年份）` 选择单一身份键。书目三要素不全的候选不合并。
- 去重结果同时返回唯一候选、重复分组、去重原因、原始候选总数和移除数量；组内择优和最终顺序均具有确定性。
- 将去重接入 `LiteratureSearchService` 与 `CrossrefCandidateLookupService` 之间。Crossref 只处理唯一候选，重复项不消耗共享查询预算。
- 扩展 Crossref 查询摘要，记录原始候选数、去重后候选数、移除数和重复组数，但不改变 `FOUND` 与 `VERIFIED` 的业务边界。
- 新增 `VerificationEvidence`、字段证据和字段匹配状态模型，供后续相似度计算使用；本次没有产生最终 `VerificationResult`。

### 验证结果

- 标准化器参数化测试覆盖 Unicode、空白、大小写、破折号、作者缩写保守边界、来源符号和 OpenAlex URL/ID 形式。
- 去重测试覆盖相同 DOI、相同 OpenAlex ID、精确书目键、字段缺失、不同 DOI、预印本/正式版保留、确定性与幂等性。
- 生产调用链测试可复现验证：5 个原始候选去重为 2 个唯一候选，Crossref 实际调用 2 次，重复证据仍保留全部 5 个原始候选。
- 最终执行 `.\mvnw.cmd clean test`：279 项通过、0 失败、0 错误、2 项按显式开关控制的真实 Crossref smoke test 跳过。

### 范围边界与技术决策

- 标准化只负责稳定表示；相似度只负责字段比较；论文身份和正式准入必须由后续业务判定层完成，三者不互相替代。
- 只按标题合并会误伤同名论文；DOI、OpenAlex ID 和完整精确书目键按可靠性分层，缺字段时优先保留候选。
- 作者名不在字符串标准化层推断同一性；`John Smith`、`Smith, John` 和 `J. Smith` 默认保持不同。
- 预印本与正式出版版本可能具有不同 DOI 或论文类型，当前不会仅凭标题接近自动合并。
- 近期不计划加入多源核验，因此跨不同键类型建立连通分组的能力暂不纳入范围。
- `FOUND` 继续不等于 `VERIFIED`；`SearchResponse.papers` 仍保持为空。

### 后续阶段改进项

以下项目安排在标题/作者/年份/来源相似度与字段核验阶段处理，不阻塞 7 月 25 日验收：

- 统一并文档化“第一作者”契约，使 Crossref 书目查询参数与候选身份标准化复用同一条抽取/归一化路径。
- 将 `openAccess` 从元数据完整度评分中移除或单独建模，避免把可访问性误当成字段完整性。
- 区分候选出现标识与来源 OpenAlex ID，避免非法来源 ID 削弱 `candidateId` 的语义。
- 在相似度与最终业务判定层评估 venue、work type 等冲突信号；不把它们提前塞入字符串标准化或模糊去重层。
- 暂不安排跨键去重；只有未来恢复多源候选融合并出现真实样本时再重新评估。

## 2026-07-27｜可信论文核验闭环与正式结果准入

### 今日目标

- 建立去重候选与 Crossref 查询结果的显式关联，禁止通过列表下标配对跨源记录。
- 将既有字段级证据转换为最终 `VerificationResult`，并为正式论文建立只允许 `VERIFIED` 的准入 Gate。
- 将核验闭环接入 `LiteratureSearchService`，使 `POST /api/literature/search` 可以返回真实、经 Crossref 核验且具有规范化 DOI 的论文。

### 实际进展

- 新增 `CandidateLookupResult`，为每个去重候选保留查询路由、状态、Crossref references 和安全 reason；覆盖禁用、未找到、来源不可用、失败、预算跳过和本地不合格状态。
- 改造 `CrossrefCandidateLookupService` 与 `CrossrefLookupSummary`，保持稳定候选顺序和原有统计，同时确保候选级结果数等于去重候选数。
- 新增 `VerificationPolicy`：有 DOI 路径以 DOI 精确一致为主证据，并由标题、作者和年份阻止明显错误匹配；无 DOI 路径只允许唯一强匹配且取得 Crossref DOI 时进入 `VERIFIED`。
- 新增 `PaperVerificationService`，只对 `FOUND` references 生成字段证据，保留每个候选的最终状态与唯一选中 reference。
- 新增 `EligiblePaperFilter`，只接纳 `VERIFIED` 且 DOI 可规范化的论文；保持 OpenAlex 元数据优先、按 DOI 全局去重，并使用与核验分分离的 rank-derived 展示分。
- 修正 `SearchResponse`：`PaperResult` 不再允许 `PARTIALLY_VERIFIED`；`NO_VERIFIED_RESULTS` 允许存在部分核验统计，但要求正式论文为空且 `verifiedCount=0`。
- 将正式核验统计与结果准入接入 `LiteratureSearchService`，候选数、去重数、核验分类总数和正式输出数量保持可解释且一致。

### 验证结果

- 聚焦测试：`VerificationPolicyTest`、`PaperVerificationServiceTest`、`EligiblePaperFilterTest` 共 8 项通过，0 失败、0 错误。
- `.\mvnw.cmd test`：308 项测试通过，0 失败、0 错误，2 项显式开关控制的真实 Crossref smoke test 按预期跳过。
- `.\mvnw.cmd clean verify`：从空 `target` 重新编译成功，308 项测试通过并完成 JAR 打包。
- `git diff --check` 通过；提交范围未包含 `eval/**` 或 `src/test/java/com/dj1012h/researchpilot/eval/**`。
- 真实 Swagger 请求返回 HTTP 200 与 `COMPLETED`：15 个 OpenAlex 候选、15 个去重候选；Crossref 尝试 5 次、找到 5 次、失败 0 次；正式返回 5 篇 `VERIFIED` 论文。
- 5 篇正式论文的 `paper.doi` 均已规范化，并与各自 `verification.referenceDoi` 一致。

### 范围边界

- 未修改或合并评测数据集资产；`eval/crossref-verification-v1` 继续独立维护。
- 未加入多轮 Agent、ReAct、持久化、缓存、RAG、Qdrant、PDF、前端或新的外部数据源。
- Crossref 来源不可用只表示外部核验暂时不可执行，不解释为论文造假或字段冲突。
- `evidenceScore` 保持工程证据分语义，不作为统计概率或查询相关性分数。

## 2026-07-28｜受控 Agent 状态、动作与全局执行预算

### 当日目标

- 在既有可信检索链路外建立 `LiteratureResearchAgent` 工作流骨架，保持 `SearchAgent` 仅负责可信查询规划。
- 用不可变状态、有限动作和统一预算门禁表达首轮检索与核验，不实现自动 Agent 循环、模型动作决策或重规划。
- 证明预算拒绝会在外部端口调用之前发生，并保留既有可信检索、Crossref 核验和正式准入规则。

### 实际进展

- 新增 `AgentState`、`AgentStage`、`AgentAction`、`AgentActionType`、`AgentObservation`、`TerminationReason`、`ActionCost`、`ActionExecutionPermit`、`BudgetCheckResult` 与 `ActionPreparation`。
- `AgentState` 以不可变副本保存原始请求、当前/历史可信 `SearchPlan`、候选与去重结果、核验结果、正式论文、跨轮计数器、轻量 Observation、开始/截止/终止时间与结构化终止原因；终止后拒绝继续动作。
- 新增 `AgentBudgetProperties` 和 `AgentBudgetPolicy`，统一在动作前检查固定预算：2 轮搜索、1 次计划调整、8 步、45 个全局唯一候选、45 次 Crossref 调用和 90 秒截止。`now >= deadline` 固定为截止超限。
- 将既有候选身份优先级提取为 `CandidateDeduplicationKey.from`，使现有去重服务和 Agent 跨轮累计共用 DOI、OpenAlex ID、精确书目键规则；稳定重复候选不重复计数。
- 新增 `LiteratureResearchAgent.executeOpenAlexSearch` 作为受控端口边界：只有 `AgentBudgetPolicy` 发出许可后才调用 `OpenAlexSearchPort`；预算失败时端口零调用，成功时记录有界候选和 Observation。
- 既有 `SearchAgent`、`LiteratureSearchService`、Crossref Client、核验策略与正式论文准入逻辑均未修改；本次没有引入动作模型、`SearchActionDecider`、计划细化器、循环执行、持久化、缓存或 RAG。

### 验证结果

- 新增测试：`AgentStateTest`（4）、`AgentBudgetPolicyTest`（3）、`LiteratureResearchAgentTest`（2），共 9 项通过、0 失败、0 错误；不依赖真实网络。
- 邻近回归：`SearchAgentTest`、`PaperVerificationServiceTest`、`CandidateDeduplicationServiceTest`、`ArchitectureConstraintsTest` 通过。
- `./mvnw.cmd test`：317 项通过、0 失败、0 错误、2 项默认关闭的真实 Crossref smoke test 按预期跳过。
- 已执行 `git diff --check`；待提交前仅暂存本日实现、测试和本文档，不包含评测资产、凭据或真实服务响应。

### 范围边界与技术决策

- 预算是单个研究任务的服务器固定硬上限；用户 `limit` 和模型输出不能提高预算。
- 预算检查只由 `AgentBudgetPolicy` 决定，状态更新要求匹配该许可，避免多个组件重复扣减同一次调用。
- 当前阶段受控接入 OpenAlex 端口以验证门禁；完整 Crossref 执行编排、状态迁移策略和自动循环留待后续明确阶段。
- Observation 只保存短诊断摘要、计数、阶段、耗时与失败码；不保存 Prompt、模型完整输出、外部原始 JSON、API Key 或 Token。

## 2026-07-30｜受控检索计划调整与真实约束来源

### 当日目标

- 在首轮可信 `SearchPlan` 校验时记录最终字段实际采用的来源，不根据事后值反推 provenance。
- 在 `REFINE_PLAN` 已被选定后，只允许追加英文同义词、缩写和概念组合，并将合并草稿重新送入完整五层校验链。
- 保持 `SearchRequest`、Controller、Swagger、外部响应契约和现有动作决策职责不变，不执行第二轮 OpenAlex 或 Crossref。

### 实际进展

- 新增 `ConstraintOrigin`、`SearchConstraintField`、`SearchConstraintOrigins` 与 `SearchPlanValidationResult`；缺少任一字段来源时直接失败。
- `SearchPlanBusinessValidator` 使用内部 `ResolvedValue<T>` 在选择 request、draft、`recentYears` 或系统默认分支时同步产生 provenance。语言和文献类型按当前真实路径标记为 `MODEL_DERIVED`，服务端预算标记为 `SYSTEM_FIXED`。
- `SearchPlanValidationPipeline.validateWithOrigins` 返回可信计划及来源；原有 `validate` 保持兼容。`SearchAgent.createPlanContext` 额外保留原始生成上下文，原有 `createPlan` 返回契约不变。
- 新增 `SearchPlanRefiner`、严格 refinement schema、模型生成边界、最小 Prompt、拒绝原因、差异与结果对象。模型草稿不包含年份、语言、类型、排序或数量字段。
- Refiner 最多执行一次，按稳定顺序清洗和追加最多 5 个新表达；空建议、仅重复建议、超量、超长和非法结构全部拒绝。
- 合并后的 `SearchPlanDraft` 从当前可信计划复制所有冻结字段，只派生追加式 `englishKeywords` 与受控 `searchQuery`，随后重新经过 JSON 语法、JSON Schema、DTO、业务和安全五层校验。
- 二次校验再次比较所有冻结字段，并沿用首轮已记录的来源，避免把系统默认值或首轮模型值误判成新的来源。

### 验证结果

- 定向测试：47 项通过，0 失败、0 错误、0 跳过。
- `.\mvnw.cmd clean verify`：从空 `target` 重新编译并打包成功；346 项通过，0 失败、0 错误，2 项默认关闭的真实 Crossref smoke test 按预期跳过。
- 来源测试覆盖 request year、draft year、`recentYears`、默认年份、request limit、draft resultLimit 和默认 resultLimit 七条分支。
- Refiner 测试覆盖稳定追加、冻结字段、一次上限、空/重复/超量/超长建议、未知字段、完整复验调用、差异解释和校验失败时外部工具零调用。
- `SearchActionDecider` 与架构约束测试继续通过；`git diff --check` 通过。
- 7 月 30 日验收清单逐项复核通过，README 已同步真实来源规则、严格调整边界、测试数量和后续范围。

### 范围边界

- 未实现完整 Agent while 循环、第二轮 OpenAlex、第二轮 Crossref、综合生成、MySQL、Redis、RAG、Qdrant、PDF 或前端。
- 未修改 `SearchRequest`、Controller、Swagger、外部 API 响应或 Crossref 评测数据集。
- 交付保持为单一功能分支提交；未合并 `main`，未创建 PR。

## 2026-07-31｜有限、可解释、受预算控制的 Agent 执行循环

### 当日目标

- 在不修改 Controller、Swagger 或外部 API 契约的前提下，把现有状态机、预算门禁、
  动作决策、计划调整、OpenAlex、去重和 Crossref 核验组合为有限内部执行循环。
- 第一轮保持 Java 确定性路径；只有结果不足时才允许一次受控 refinement 和第二轮检索。
- 为每次内部运行提供不参与决策的最小内存 Trace，并对截止、预算、外部服务失败和非法状态
  进行 fail-closed 终止。

### 实际进展

- 新增 `AgentExecutionContext`，在 `AgentState` 旁保留 `ValidatedSearchPlanContext`、
  最近一次 `SearchPlanRefinementResult` 和本轮去重结果；第二轮使用重新校验后的可信上下文，
  provenance 不进入 `AgentState` 或外部 API。
- 新增 `SearchActionExecutor`，每次只执行一个已经通过状态白名单和预算门禁的动作：
  OpenAlex 查询经 `OpenAlexQueryFactory.createBounded` 按剩余候选预算收敛；去重复用
  `CandidateDeduplicationService` 与全局稳定键；核验复用
  `CrossrefCandidateLookupService → PaperVerificationService → EligiblePaperFilter`。
- `LiteratureResearchAgent.execute` 使用有限 `for` 边界编排动作。单一合法动作由 Java 直接选择；
  多动作只允许出现在 `EVALUATING_RESULTS`，并限定为 `REFINE_PLAN` 与 `COMPLETE`。
  `TERMINATE` 始终由 Java 控制。
- `AgentState.recordRefinedPlan` 要求有效 `REFINE_PLAN` permit、保持 `originalQuery`、
  追加 `planHistory`、更新 `currentPlan` 并返回 `PLAN_READY`；调整计数仍只在动作开始时增加一次。
- 第二轮仅把相对 `globalCandidateKeys` 新增的稳定候选送入 Crossref；正式论文继续由既有 Gate
  限制为 `VERIFIED` 且具有规范化 DOI，并按 DOI 全局去重、保持稳定顺序。
- 将默认业务步骤上限从 8 修正为 10：初始计划登记 1 步、两轮各 4 步、一次 refinement 1 步。
  搜索仍最多 2 轮、计划仍最多调整 1 次，其余候选、Crossref 和 deadline 上限不变。
- 新增 `ExecutionTraceEntry`、`BudgetUsageSnapshot`、`ExecutionTraceRecorder` 与并发安全的
  `InMemoryExecutionTraceRecorder`。Recorder 分配连续 stepIndex、检查阶段连续和预算单调，
  返回不可修改快照；摘要限制为 500 字符，排除 Prompt、凭据和原始 provider payload。

### 验收结果

- 定向测试覆盖首轮达标、refinement 后达标、两轮部分结果、零可信结果、主动 COMPLETE、
  deadline、非法上下文、OpenAlex/Crossref 不可用、五类预算阻断、跨轮去重、Trace 隔离/
  并发/连续性/单调性及架构依赖边界。
- 执行循环、执行器、Trace、refinement 和架构边界定向测试：23 项通过，0 失败、0 错误。
- 最终 `.\mvnw.cmd clean verify`：365 项测试通过，0 失败、0 错误，2 项默认关闭的真实
  Crossref smoke test 按预期跳过，并成功完成 JAR 打包。
- `git diff --check` 通过；敏感模式检查只命中安全说明或测试字段名，未发现真实凭据。

### 范围边界与遗留风险

- 本次入口仅供 application/agent 内部使用，尚未接入现有 `LiteratureSearchController`
  或 `LiteratureSearchService` 的公共请求链路，因此不改变现有外部行为。
- 无去重候选时沿用既有 `AgentTransitionPolicy`，直接进入结果评估，不调用 Crossref。
- Trace 仅保存在当前进程内存中，不接 MySQL、Redis、外部日志系统或 API。
- 未修改核验算法、相似度阈值、Controller、Swagger、请求/响应契约、评测资产、
  MySQL、Redis、RAG、Qdrant、PDF、前端或异步任务。

## 2026-08-02｜受门槛保护的摘要级综述输入与 Prompt

### 实际进展

- 新增内部 `literature.review` 包：`ReviewInputFactory` 只接收 `AgentRunResult.finalState()` 或 `AgentState` 的正式 `verifiedPapers`，构建不可变的 `CitationId`、`EvidencePaper` 和 `ReviewInput`；不接受候选列表、去重列表或非 `VERIFIED` 的核验诊断。
- `CitationId` 固定映射到正式论文的一基序号。缺少摘要时仅跳过该论文的证据投影，因此例如正式顺序 A/B/C/D 会稳定产生 `P1/P3/P4`，而非重新编号。
- 生成前门槛严格为 `ceil(requestedCount * 0.60)` 篇正式论文和至少 3 篇可用摘要；分别返回 `INSUFFICIENT_VERIFIED_PAPERS` 或 `INSUFFICIENT_ABSTRACTS`，并由 `ReviewGenerationService` 保证不调用生成器。
- `EvidenceReviewPromptBuilder` 复用隔离的 `StructuredOutputMapper` 序列化最小证据投影，固定指令明确摘要是未信任外部数据，禁止执行其中的指令、命令、URL、角色声明或格式覆盖。`LlmEvidenceReviewGenerator` 仅复用 `ModelInvoker` 的 `evidence_review` 操作；不注册 Tool、不记录 Prompt/摘要/原始模型输出。
- 模型返回值类型为 `UntrustedReviewDraft`，仅保留在内部 `ReviewGenerationAttempt` 中，未接入 `LiteratureSearchService`、`LiteratureResearchAgent`、Controller、`SearchResponse`、日志或持久化。

### 验证结果

- 聚焦离线测试：`ReviewGenerationServiceTest`、`EvidenceReviewPromptBuilderTest`、`ReviewInputTest` 和 `ArchitectureConstraintsTest` 共 21 项通过，0 failures、0 errors、0 skipped。
- `./mvnw.cmd clean verify`：376 项测试通过，0 failures、0 errors、2 个明确 opt-in 的 Crossref smoke tests skipped，并成功重新打包 Spring Boot JAR。
- 覆盖刚好达标、正式论文不足、摘要不足、零正式论文、缺摘要时保留编号、间接 Prompt Injection、不可变集合、重复 DOI/编号拒绝以及 review 包的工具/外部 DTO/API 隔离。
- 本次未执行真实 OpenAlex、Crossref 或模型调用；测试全部使用固定对象和 Mockito，未将 fixture 或回放描述为真实服务结果。

### 范围边界

- 未实现 `ReviewDraft` 的结构化映射、CitationGuard、引用解析、存在性或支持性校验、最多一次引用修正、降级和公开响应组装；这些属于 2026-08-03 或后续阶段。
- 未修改阶段三 Agent 搜索预算、`SearchStatus`、正式 `VERIFIED` 准入、DOI 规范化、Crossref 核验阈值或公共 `papers` 语义；未引入 MySQL、Redis、RAG、Qdrant、PDF、异步任务、消息队列、前端或多 Agent。

## 2026-08-03｜引用校验、一次修正、可信降级与最终响应组装

### 实际进展

- 复用 8 月 2 日已有的 `ReviewInput`、`EvidencePaper`、`CitationId`、证据门槛、PromptBuilder、Generator 和 `ModelInvoker`；未建立第二套证据输入或模型调用边界。
- 新增固定 `evidence-review-draft-v1` JSON Schema 及 `ReviewDraft`/`ReviewStatement` DTO。根对象与 statement 均拒绝额外字段，statement 类型限定为 `METHOD`、`TREND`、`OBSERVATION`、`LIMITATION`，并设置 12/800/5 的数量和长度上限。
- 新增完整且固定的验证链：JSON syntax、JSON Schema、strict DTO mapping、business validation、`CitationGuard`。业务层拒绝 DOI、URL、年份、HTML、Markdown 链接、模型手写引用标记、已知标题/作者和内部控制或核验结论文本。
- `CitationIdParser` 只接受精确的 `^P[1-9][0-9]*$`；`CitationGuard` 拒绝未知、缺失和重复引用，保持 statement 与引用首次出现顺序。它只证明引用格式、存在性和本次证据归属，不证明语义支持或全文事实。
- 新增服务器控制的长度预算：证据论文 20、摘要 4,000 Unicode code points、证据 JSON 64,000、首次 Prompt 80,000、原始 Draft 16,384、修正 Prompt 96,000。配置必须为正数且不能超过 Java 硬上限；预算按正式顺序截断摘要并从尾部停止加论文，不重新编号，少于 3 篇时返回 `INPUT_BUDGET_EXCEEDED`。
- `EvidenceReviewOrchestrator` 在每次 Java 侧逻辑生成前检查 `Clock` 与 `finalState.deadline()`。首次合法固定 1 次调用；首次非法时只允许 1 次修正并重新执行完整验证链，总调用数固定不超过 2；供应商故障不触发 citation repair。
- 新增 `ReviewResponseAssembler`、`ReviewResponse`、`ReviewCitation` 和 `PublicTerminationReason`。summary 由 Java 添加 `[P1][P3]` 后缀；citations 按首次引用顺序去重，书目字段只来自 `EvidencePaper`；内部预算细节折叠为 `LIMIT_REACHED`，异常状态折叠为 `SAFELY_TERMINATED`，不公开 `terminationDetail`。
- `LiteratureSearchService` 在受控 Agent 完成后调用综述用例，再计算 `completedAt` 与 `elapsedMs`。Review 失败只产生空 summary/空 citations 的结构化降级结果，原有 papers、verificationSummary、candidate 计数和 `SearchStatus` 语义保持不变。
- 真实响应检查发现原有 `SearchResponse.PaperResult` 会通过 `PaperDTO` 公开完整 `abstractText`。现已在该内部字段上增加 `@JsonIgnore`：Java 代码仍可读取摘要并构造受控证据输入，Spring MVC 公开 JSON 不再序列化字段名或摘要内容。

### 验证结果

- 最终聚焦测试：75 项通过，0 failures、0 errors、0 skipped；覆盖严格 CitationId、Schema/业务/CitationGuard 全链、P999、缺摘要的 P2、一次修正、两次失败、非法 JSON、供应商故障、deadline、长度预算、Java DOI 映射、公开序列化、日志可观测性和架构隔离。
- `.\mvnw.cmd test`：422 项测试，0 failures、0 errors、2 项明确 opt-in 的真实 Crossref smoke tests skipped。
- 摘要泄漏修复新增一项 Spring MVC 完整响应回归；序列化、契约、ReviewInput、Review 编排和端到端服务共 36 项聚焦测试通过。
- `.\mvnw.cmd clean verify`：从空 `target` 重新编译；增加日志落盘与 Actuator 暴露面断言后共运行 424 项测试，0 failures、0 errors、2 skipped，并成功生成重新打包的 Spring Boot JAR。

### 真实联网验收与后续修复

- 2026-07-30 在开发者已启动且安全配置的实例上，按固定请求执行一次真实 `POST /api/literature/search`，未重试。响应为 HTTP 200，服务端耗时 19,753 ms；15 个候选、15 个全局唯一候选、5 篇正式 `VERIFIED` 论文，DOI 均已规范化。
- 本次 Review 状态为 `GENERATED`，包含 4 条公开引用；CitationId、正式论文位置和 Java 权威 DOI 映射一致，未出现 `P999`、内部 Draft 字段或原始模型 JSON。
- 该次请求同时发现修复前的公开响应包含 5 个 `abstractText` 字段，其中 4 个非空，因此当时的严格真实验收未完全通过。完成序列化修复后，只在用户重新授权并重启新构建后继续真实复验，没有自动重试或隐瞒首次失败。
- HTTP 响应不公开模型调用次数；首次验收时精确值因启动终端不可读而保持未测量，没有用 1 或 2 的推断值替代。
- 为以最小代价闭环后续精确计数，应用默认把安全日志同时写入已由 `.gitignore` 排除的 `logs/research-pilot.log`；可通过 `RESEARCH_PILOT_LOG_FILE` 覆盖路径。新增 `scripts/get-latest-review-model-usage.ps1`，只解析并返回任务 ID、Review 状态、模型/修正/证据/引用计数和耗时，不输出原始日志、Prompt、Draft、摘要、query、DOI 或凭据；Actuator 暴露面仍固定为 `health,info`。
- 最终真实复验在重启后的新构建上按固定请求执行一次且未重试：HTTP 200，状态 `COMPLETED/TARGET_REACHED`，15 个候选、15 个全局唯一候选、5 篇正式 `VERIFIED` 论文且 DOI 均规范化；Review 为 `GENERATED`，5 条引用与正式位置及 Java DOI 映射一致，服务端耗时 21,408 ms。
- 完成日志与响应 `taskId` 精确对应，记录 `reviewModelCallCount=1`、`reviewRepairCount=0`、5 篇证据和 5 条引用；响应不含 `P999`、摘要字段、原始 JSON、内部 Draft、`AgentState` 或 `terminationDetail`。至此 8 月 3 日严格验收通过。

### 安全与范围边界

- 日志仅增加 reviewStatus、evidence/model-call/repair/citation 计数和安全终止枚举；不记录 Prompt、修正 Prompt、原始 Draft、摘要、DOI 列表、query、provider 异常消息或 `terminationDetail`。
- 未修改 `SearchAgent`、`LiteratureResearchAgent` 状态机、阶段三搜索预算、`VERIFIED` 准入、DOI 规范化、Crossref 阈值、正式 papers 顺序或原有响应字段语义。
- 未引入 MySQL、Redis、RAG、Qdrant、PDF、全文解析、异步任务、消息队列、前端、多 Agent、新 Tool 或外部数据源。
- 引用编号存在不等于语义支持已被证明；当前是摘要级初步综述，不是全文 RAG 或全文事实核验。

## 2026-08-04 - Flyway literature persistence schema contract

- Added Spring Boot-managed `flyway-core` and `flyway-mysql`, plus test-scope
  H2. Flyway is disabled by default through `FLYWAY_ENABLED=false`, validates
  before migration when enabled, and has `clean-disabled=true`; ordinary
  startup and offline tests still do not require MySQL.
- Added immutable V1 schema migration for search tasks, normalized plan
  attempts, formal papers, verification evidence, and the minimal ordered
  field-evidence child table. Constraints cover identifiers, task/attempt and
  DOI uniqueness, foreign keys, count/score/time/version invariants, and
  `SearchPlan` result/candidate limits. Foreign keys intentionally do not
  cascade deletes.
- The migration reserves only data-minimized, Java-owned values. It excludes raw
  prompts, raw model/provider JSON, agent traces, exception stacks, credentials,
  and raw user queries. `abstract_text` is an allowed nullable formal-paper
  field; runtime retention and writing remain out of scope.
- Added offline H2/MySQL-mode migration tests for first migration, Flyway
  history, repeat migration preserving data, DOI uniqueness, foreign keys,
  per-task attempt uniqueness, and invalid count/version/score constraints.
- No entity, mapper, repository, persistence service, transaction boundary,
  retry workflow, runtime database write, cache, RAG, PDF, frontend, or public
  API change was added.
## 2026-08-05 - Literature runtime execution persistence

- Added immutable V2 schema for ordered Agent execution steps and explicit
  task-to-formal-paper result positions. V1 remains unchanged.
- `LiteratureSearchService` now shares one UUID between `SearchResponse.taskId`,
  `AgentRunResult.traceId`, and persisted step trace IDs. Runtime persistence is
  opt-in through `LITERATURE_PERSISTENCE_ENABLED`; enabled failures propagate
  instead of falling back to no-op behavior.
- The persistence facade stores audit projections only. It keeps Java ownership
  of Agent state, budgets, paper admission, DOI normalization, and review
  validation; it writes no raw query, prompt, model output, external JSON,
  exception stack, or abstract text.
- H2 MySQL-mode validates V1+V2 migration, rerun behavior, task foreign keys,
  DOI uniqueness, and Agent-step uniqueness. A disposable real MySQL 8
  configuration was not supplied, so real-MySQL validation was not executed.
- Added Spring/H2 integration coverage for successful finalization (formal
  paper, task-paper relation, verification and field evidence), repeated
  finalization, idempotent task/step writes, and independent failure terminal
  state. The test also caught and fixed a task-count conservation violation.
## 2026-08-06 - Resilient OpenAlex/Crossref external API cache

- Added an opt-in, default-disabled Redis cache-aside layer at the OpenAlex and
  Crossref port boundary. It caches only validated internal port results in a
  versioned envelope; `LiteratureSearchService`, controlled Agent state and
  budgets, verification/admission, MySQL persistence, review flow, and public
  response contracts remain unchanged.
- Cache keys use the `research-pilot:literature:v1` namespace and SHA-256
  digests of stable effective query fields. Raw queries, DOIs, titles, authors,
  provider URLs, mailto values, and credentials are absent from keys and logs.
- OpenAlex success (including an empty mapped result) and Crossref found
  results use normal TTLs. Only explicit adapter-mapped Crossref `NOT_FOUND`
  uses the short negative TTL. Provider/configuration failures, rate limits,
  timeouts, transport errors, and invalid/empty responses are not cached.
- Redis reads/writes fail open to the original adapters. Corrupt or oversized
  values are exact-key misses and are not returned. A Clock-driven cooldown
  bypasses Redis after a cache failure without sleeping or changing business
  budgets.
- Focused offline regression command completed successfully: 37 tests, 0
  failures, 0 errors, 0 skipped. It covers cache keys, hits/misses, empty
  OpenAlex results, Crossref found/not-found TTLs, corrupt entries, Redis
  cooldown fallback, architecture, verification, Agent-loop, and H2
  persistence integration. Real Redis smoke validation was not run because no
  dedicated authorized Redis configuration was supplied.
- `./mvnw.cmd clean verify` completed successfully: 441 tests, 0 failures, 0
  errors, and 2 explicit opt-in Crossref smoke tests skipped. The Spring Boot
  JAR was packaged successfully.

## 2026-08-04 - Trusted Agent normal, boundary, budget, and timing regression evidence

- Audited and reused existing coverage rather than duplicating it. Existing
  Agent-loop tests already prove first-round completion, one-refinement success,
  partial results, deadline preemption, invalid-state rejection, cross-round
  candidate deduplication, and the `VERIFIED` formal-paper gate. Existing
  service, persistence, and cache tests retain response, H2 persistence, and
  port-decorator regression coverage.
- Added the missing complete two-round zero-result path: after exactly one
  controlled refinement, two OpenAlex rounds, and no `VERIFIED` paper, the
  Agent completes with `NO_VERIFIED_RESULTS`, empty formal papers, and no third
  external round. The test uses a fixed Clock and Mockito only.
- Added the production-default per-lookup Crossref boundary: five distinct
  candidates perform five port calls, while the sixth is recorded as
  `SKIPPED_BY_LIMIT` without a sixth port call. This is intentionally distinct
  from the Agent-wide 45-call policy limit, which remains covered by
  `AgentBudgetPolicyTest` and executor pre-action checks.
- The retained budget evidence covers two permitted search rounds then denial,
  one permitted refinement then denial, ten business steps then denial, the 45
  unique-candidate and 45 Crossref-call limits, deadline equality, terminal
  state, and invalid action rejection before external tools. The complete
  two-round route remains a ten-business-step route; no budget, public API,
  verification threshold, or cache semantic changed.
- Focused offline regression command ran 59 tests with 0 failures, 0 errors,
  and 2 explicit opt-in Redis smoke skips. After adding safe timing-event
  assertions, the prompt-specified final focused command ran 52 tests with
  0 failures, 0 errors, and 2 explicit Redis smoke skips.
  `./mvnw.cmd test` and `./mvnw.cmd clean verify` each ran 460 tests with
  0 failures, 0 errors, and
  4 explicit opt-in network smoke skips; `clean verify` rebuilt the executable
  Spring Boot JAR.
- Added safe performance events without changing public DTOs or business
  semantics. `LiteratureCacheService` records provider/operation/read-write
  phase/outcome/duration without keys or values; `ModelInvoker` records the
  fixed operation, configured model name, input length, outcome and duration
  without prompt/output; persistence records only fixed operation names,
  outcome, duration and safe exception type; the in-memory Trace recorder
  records only Agent action, status and elapsed time.
- Added `scripts/get-latest-literature-performance.ps1`. For one explicitly
  serialized request it isolates the log window at the later of application
  start or the previous completed task, aggregates count/total/max/average
  duration, identifies the slowest Agent action, and lists every unmeasured
  category. It does not output raw log lines, task content, cache keys, query,
  DOI, prompt, draft, abstract, provider payload or credentials.

### Authorized real-service performance observation

- The first persistence-enabled request exposed a real environment issue:
  MySQL returned `literature_search_task` missing. The service logged
  `CREATE_RUNNING_TASK` failure and returned HTTP 500; it did not continue to
  LLM/OpenAlex/Crossref or report false success. After explicit restart with
  Flyway enabled, MySQL 8.0 migrated the empty `research_pilot` schema through
  immutable V1 and V2 in 669 ms.
- Health then reported application, MySQL (`SELECT 1`) and Redis (`PONG`) UP,
  with the LLM configured. A fixed, serial cold request returned HTTP 200,
  `PARTIAL_SUCCESS/SAFELY_TERMINATED`, 15 candidates, 15 unique candidates,
  4 `VERIFIED` formal papers with normalized DOI, and no leaked internal
  response fields. Service elapsed time was 91,534 ms. Review validation
  failed closed and returned no citations.
- Cold cache evidence was OpenAlex one miss/one successful write; Crossref had
  three misses with successful writes and two existing hits. MySQL timing was
  task create 26 ms, five step appends 39 ms total (11 ms max), and success
  finalization 111 ms. LLM planning/action/refinement took
  3,247/2,947/3,547 ms; two review attempts totaled 71,638 ms (55,164 ms max).
  The slowest Agent action was OpenAlex search at 8,008 ms.
- The immediate identical request returned the same 15/15/4 counts and HTTP
  200 in 23,449 ms; Review was `GENERATED` with four citations. OpenAlex had
  one cache hit and Crossref five hits. MySQL create/step/finalize timings were
  6/38/47 ms; LLM planning/action/refinement/review timings were
  2,739/2,324/5,024/13,273 ms. Agent OpenAlex search fell to 19 ms and Crossref
  verification to 5 ms; the slowest action was the safely failed refinement
  at 5,026 ms.
- A separate previously used Mamba query was also observed once after the
  migration: HTTP 200, 15 candidates, 15 unique candidates, 4 normalized-DOI
  `VERIFIED` papers, generated review with four citations, and 45,284 ms
  service time. It remained `PARTIAL_SUCCESS/SAFELY_TERMINATED`; no production
  threshold, budget or evidence label was changed to force a live
  `COMPLETED` result.
- All component categories requested for this opt-in observation were measured;
  none were defaulted to zero or inferred from total time. The results are
  individual public-network observations, not a development-machine SLA.
  Deterministic offline tests remain the acceptance evidence for the complete
  normal, controlled-refinement, partial and zero-result paths.
## 2026-08-05 - Failure safety and observability (completed early for the planned 2026-08-08 milestone)

- Rechecked the current `main` baseline rather than relying on the prompt
  snapshot. The workspace started clean at `d93aaa3`; the prior snapshot's
  uncommitted boundary-test changes were already incorporated in that baseline.
  Baseline `./mvnw.cmd test` passed 460 tests with 0 failures/errors and 4
  explicit opt-in network smoke skips.
- Added synchronous HTTP correlation only: `RequestCorrelationFilter` creates
  a server-owned UUID, returns it in `X-Request-Id`, sets MDC for the request,
  and removes both `requestId` and `taskId` in `finally`. Task correlation is
  bound after `LiteratureSearchService` creates the task UUID and is not used to
  pass business state to the Agent. Input validation occurs before task creation
  and therefore has a request id but no invented task id.
- Aligned `SearchPlanGenerationContext.requestId` with that request
  correlation when a request exists; non-HTTP callers use a new local UUID.
  This eliminates ambiguous dual request identifiers without changing public
  DTOs or Agent contracts.
- Hardened public/runtime failures: persistence operations are wrapped in
  `LiteraturePersistenceException` and map to HTTP 503
  `LITERATURE_PERSISTENCE_FAILED` with stable `PERSISTENCE` details. Generic
  exceptions log only stable metadata and restore an interrupted thread when
  the cause chain contains `InterruptedException`; exception message, stack,
  provider payload, SQL, URL, credentials, prompt, query, DOI, title, and
  abstract are not logged or returned. Expected validation remains info-level
  with stable codes.
- Added bounded Micrometer measurements for literature request duration, Agent
  termination, LLM, OpenAlex, Crossref, persistence, and cache events. Tags
  are constrained to provider/operation/outcome/failure type/termination
  reason/cache result. IDs, query and provider-data fields are excluded. The
  default Actuator exposure remains `health,info`; no metrics/Prometheus
  endpoint is exposed by this milestone.
- Recorded the failure matrix without changing its existing semantics:
  initial plan LLM failure rejects before a trusted plan; action LLM falls back
  only to existing allowed actions; review failure degrades review only;
  OpenAlex/Crossref preserve controlled termination and `SOURCE_UNAVAILABLE`;
  Redis fails open to its adapters; enabled persistence fails closed with 503.
  Formal papers still require `VERIFIED` plus normalized DOI.
- Offline replay uses existing Mockito, MockMvc, fixtures, fixed Clock and
  `SimpleMeterRegistry`; it does not record or contact real providers. It now
  covers secret-bearing generic/persistence failures, request-id response/log
  agreement, MDC cleanup across consecutive requests, and low-cardinality
  meter dimensions alongside existing LLM/OpenAlex/Crossref/Redis/persistence/
  review/Agent regressions. No real fault injection or live-service smoke was
  authorized or run.
- The prompt-specified focused offline suite passed 99 tests with 0 failures,
  0 errors and 2 explicit opt-in Redis smoke skips.
  Full `./mvnw.cmd test` passed 464 tests with 0 failures, 0 errors and 4
  explicit opt-in network smoke skips. Final `./mvnw.cmd clean verify` also
  passed 464 tests with 0 failures, 0 errors and 4 explicit opt-in network
  smoke skips, and rebuilt the executable Spring Boot JAR. The tracked-content
  sensitive scan and diff checks are recorded with this delivery review.

## 2026-08-05 - Reproducible entry point, demonstration material, and startup modes

- Replaced the date-stacked README entry point with a current quick-start for
  the trusted literature Agent. Historical milestone facts remain in this
  append-only log and in existing design documents; the new README does not
  present H2 compatibility tests as MySQL 8 acceptance or fixtures as live
  provider results.
- Added three explicit PowerShell modes in `scripts/start-local.ps1`:
  `OfflineBuild` performs only Maven verification; `TrustedSearch` enables the
  LLM/OpenAlex/Crossref route without persistence; `FullDemo` additionally
  enables Flyway and persistence for an authorized MySQL schema. Redis is
  checked only with `-EnableCache`; MySQL is checked only for `FullDemo`.
  The script never invokes `flyway clean`, destructive database commands, or
  standalone provider probes, and it does not print passwords, tokens, or API
  keys.
- Added current HTTP examples, a focused deterministic regression script, and
  `docs/demo/trusted-agent-demo.md`. The guide contains architecture, actual
  Agent-stage, Flyway V1/V2 ER, and trusted-data-flow diagrams plus three
  explainable scenarios. It labels external observations, fixed fixtures, and
  deterministic tests separately.
- Updated `.env.example` to describe only current feature switches and
  non-secret placeholders. Qdrant/embedding settings were removed from the
  example because those capabilities are not implemented and remain gated.
- Baseline before this documentation/script work: `./mvnw.cmd clean verify`
  passed 464 tests with 0 failures, 0 errors, and 4 explicit opt-in network
  smoke skips, and rebuilt the executable JAR. The new focused
  `verify-trusted-demo.ps1` command passed 42 tests with 0 failures, 0 errors,
  and 2 explicit opt-in Redis smoke skips. The README `OfflineBuild` command
  was then executed in a fresh PowerShell process and its final `clean verify`
  again passed 464 tests with 0 failures, 0 errors, and 4 explicit opt-in
  network smoke skips. No real LLM, OpenAlex, Crossref, MySQL, or Redis
  demonstration was run for this documentation milestone because no live
  configuration or authorization was supplied.

## 2026-08-05 - Crossref lookup capacity alignment

- Increased the configured `CrossrefCandidateLookupService` ceiling from five
  to ten new lookups per invocation. The executor supplies the trusted
  requested target as a second bound, so the effective per-round limit is
  `min(requested target, configured lookup ceiling)`. With at most two Agent
  search rounds, a five-paper request retains its prior five-lookup ceiling
  while the default 20-paper target can use at most ten plus ten lookups.
- Kept the public maximum result limit, two-round/one-refinement Agent shape,
  ten-business-step ceiling, 45 global unique-candidate and Crossref-call
  budgets, 90-second deadline, provider concurrency, five-requests-per-second
  gate, retry policy, and `VERIFIED` plus normalized-DOI admission unchanged.
  A request above the bounded lookup capacity may still return an honest
  partial result.
- Added configuration-consistency coverage tying the default result target to
  the two-round lookup capacity and retaining the larger Agent-level
  Crossref-call ceiling. Updated the production boundary regression to prove
  ten port calls are allowed and the eleventh is rejected before invocation;
  a separate regression proves a five-paper caller still blocks the sixth.
- The final focused configuration/lookup/executor/Agent/service command passed
  32 tests with 0 failures, 0 errors and 0 skips. Final
  `./mvnw.cmd clean verify` passed 465
  tests with 0 failures, 0 errors and 4 explicit opt-in network smoke skips,
  and rebuilt the executable Spring Boot JAR.
- One explicitly authorized FullDemo observation was run against the
  intermediate configured-ceiling implementation before the caller bound was
  added. Flyway validated an existing MySQL 8 V2 schema; health, MySQL, Redis
  and LLM configuration were UP. The fixed five-paper request completed
  server-side with HTTP 200 and `TARGET_REACHED`: 15 candidates, 15 unique
  candidates, 10 Crossref attempts, 10 verified outcomes, five formal papers,
  a generated review with four citations, and 40,669 ms service time.
  Persistence creation and finalization succeeded. The ten attempts for a
  five-paper request exposed the over-query and directly motivated the caller
  bound above; the final deterministic regression proves the correction.
- The PowerShell client wrapper raised `NotSupportedException` while inspecting
  the already-returned response, after the server had logged HTTP 200 and
  durable finalization. No second external search was sent. After the final
  clean build, the corrected JAR was restored with the same FullDemo
  configuration; health, MySQL, Redis and LLM configuration were UP. This is
  one public-network observation, not an SLA or a live proof of the final
  caller-bound branch.

## 2026-08-06 - Trusted demo main-plan closeout

- Restored the approved public result contract to a default of 5 and a hard
  maximum of 15 across request validation, the Java domain model, model-output
  schema, runtime defaults, tests, README, and current design documentation.
  Immutable Flyway V1 retains its historical wider storage envelope; it does
  not override Java admission.
- Retained the measured 10-business-action Agent ceiling and documented the
  correction from the historical plan's stale estimate of 8. The operational
  shape remains two search rounds and one controlled refinement, with terminal
  completion not consuming an additional business action.
- Added `scripts/replay-trusted-demo.ps1` and a fixed offline replay covering
  first-round target, target after one refinement, and insufficient evidence.
  The entry point prints only redacted statuses and counts. The two successful
  paths each produced five formal papers and three citation-validated review
  citations; the insufficient path produced no formal papers, no citations,
  and no review-model call.
- Final `./mvnw.cmd clean verify` passed 466 tests with 0 failures, 0 errors,
  and 4 explicit opt-in external smoke skips, and rebuilt the executable JAR.
- The final corrected JAR was started without a Windows reboot. Exactly one
  fixed FullDemo literature request was sent and was not retried. It returned
  HTTP 200, `COMPLETED`, 15 candidates, 15 unique candidates, 5 verified and
  formal papers, `TARGET_REACHED`, a generated review with 5 citations, and
  24,852 ms service time.
- A read-only MySQL inspection of the same redacted task confirmed one task,
  one plan attempt, five Agent steps, five task-paper links, and 15 verification
  evidence rows. The task was `COMPLETED`, the review was `GENERATED`, and the
  public termination reason was `TARGET_REACHED`.
- Added `docs/demo/v1.0.0-demo-acceptance.md` as the English release evidence.
  Qdrant, embeddings, PDF ingestion, and RAG business code remain excluded from
  this main-plan release and isolated as a post-2026-08-10 extension.
