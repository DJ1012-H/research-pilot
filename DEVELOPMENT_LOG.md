# ResearchPilot 开发日志

> 日志覆盖时间：2026-07-13 ～ 2026-07-17
> 第一阶段状态：已完成，较原计划 2026-07-18 提前一天
> 第二阶段进展：已提前完成原计划 2026-07-19 的数据流与接口契约
> 里程碑版本：`v0.1.0-phase1`
> 第一阶段提交：`3c84ef4a6dd6043320c95bc9655701a52d52ce3b`
> 远程仓库：<https://github.com/DJ1012-H/research-pilot>

## 1. 记录原则与证据说明

本文档记录 ResearchPilot 从初始化、第一阶段验收到第二阶段检索契约冻结的实际开发过程。

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

## 7. 关键技术决策

### D-001｜正式仓库与版本策略

- 正式本地仓库：`C:\javaProject\research-pilot`
- GitHub：<https://github.com/DJ1012-H/research-pilot>
- 第一阶段提交：`3c84ef4`
- 第一阶段标签：`v0.1.0-phase1`
- 标签作为不可移动的可复现里程碑；后续开发继续在新提交上进行。

### D-002｜配置与密钥管理

- MySQL、Redis 和模型配置通过环境变量提供。
- 密码和 API Key 在 PowerShell 中使用安全输入读取。
- 日志不记录消息正文、密码或 API Key。
- `.env.example` 只保存占位配置。

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
- 31 个自动测试（其中第一阶段验收测试 22 个）
- 本地启动脚本
- 第一阶段验收脚本
- Qdrant 技术决策
- Git 提交、标签和 GitHub 同步
- 文献检索类级数据流和模块职责
- 五个文献检索核心契约及冻结规则

### 尚未开始或尚未完成

- Search Agent
- OpenAlex 文献检索
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
3. [ ] 接入 OpenAlex，模型只生成检索策略，不生成论文列表。
4. [ ] 使用 Flyway 创建检索任务、论文和核验记录表。
5. [ ] 接入 Crossref 核验 DOI 和元数据。
6. [ ] 实现 DOI/标题去重。
7. [ ] 建立可解释的可信度评分。
8. [ ] 使用固定研究主题建立回归测试。

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
