# ResearchPilot

ResearchPilot 是一个基于 Java、Spring Boot、LangChain4j 与 RAG 的学术文献检索 Agent。

第一阶段已经完成工程骨架、MySQL、Redis、真实模型调用、统一异常处理、Swagger 和自动测试。

第二阶段已完成文献检索数据流、接口契约和五个核心数据结构的冻结；OpenAlex、Search Agent、Crossref、去重和持久化实现仍按后续计划开发。

## 当前进度

- [x] 第一阶段工程闭环和真实环境验收
- [x] 文献检索类级数据流与模块职责
- [x] `SearchRequest`、`SearchPlan`、`PaperDTO`、`VerificationResult`、`SearchResponse`
- [x] `POST /api/literature/search` 请求与响应契约
- [ ] OpenAlex 真实文献检索
- [ ] Search Agent 查询规划
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
│   │   └── dto
│   │       ├── SearchRequest.java
│   │       └── SearchResponse.java
│   └── model
│       ├── SearchPlan.java
│       ├── PaperDTO.java
│       └── VerificationResult.java
├── controller
├── service
│   └── impl
├── mapper
├── dto
│   ├── request
│   └── response
├── config
├── exception
└── common
    └── response

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
- 文献检索接口：`POST /api/literature/search`（仅完成契约，当前尚不可调用）

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

当前自动测试共 31 个，包含 9 个文献检索契约测试。

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

- 文献检索接口的运行时编排
- OpenAlex/Crossref 外部调用
- Search Agent、标准化、去重、核验和排序
- 检索任务、论文和核验记录入库
- Embedding
- Qdrant 接入
- 向量检索
- RAG 问答
- PDF 全文解析

这些功能将在后续阶段实现。
