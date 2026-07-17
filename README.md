# ResearchPilot

ResearchPilot 是一个基于 Java、Spring Boot、LangChain4j 与 RAG 的学术文献检索 Agent。

第一阶段已经完成工程骨架、MySQL、Redis、真实模型调用、统一异常处理、Swagger 和自动测试。

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
http                       # HTTP 请求样例
scripts                    # 启动与验收脚本
~~~

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

聊天请求示例：

~~~json
{
  "message": "什么是 RAG？"
}
~~~

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

第一阶段暂不实现：

- 文献搜索与核验
- Embedding
- Qdrant 接入
- 向量检索
- RAG 问答
- PDF 全文解析

这些功能将在后续阶段实现。
