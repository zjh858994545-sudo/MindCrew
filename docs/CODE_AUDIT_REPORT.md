# MindCrew 源码审计报告

审计时间：2026-06-20  
审计范围：`F:\mindcrew-xianyu-v1.0\MindCrew\MindCrew`

## 1. 审计结论

当前项目不是空壳，已经具备 Java 后端、Vue 前端、RAG 检索链路、Tool Calling、MCP Server、SSE 流式问答、反馈审核和 Golden Pair 基础能力。

但项目还不能直接作为公开投递版本，主要风险是：

- 项目根目录当前不是 Git 仓库，无法按模块提交 commit。
- 项目内存在 `.env`，其中包含多类真实或疑似真实密钥变量。
- 对外命名仍混有 `DocMind`、`MediRag`、`Med*` 历史痕迹。
- 根目录没有 `README.md`，现有说明文档与代码能力存在不一致。
- `mvn -DskipTests package` 可通过，但 `mvn test` 当前失败，原因是 SpringBoot 上下文测试依赖真实环境变量 `BAILIAN_API_KEY`。
- 前端 `npm run build` 在真实环境可通过，但在当前沙箱内因 `spawn EPERM` 失败。
- 目前未发现完整 RAG Eval、Agent Trace、Prompt Injection 防护、安全审计页面，这些需要新增。

优先优化顺序建议：

1. 仓库清理、MindCrew 命名统一、密钥清理、`.env.example` 和 test profile。
2. RAG Eval 评测体系。
3. Agent Trace 可观测性。
4. Prompt Injection 防护。
5. 反馈闭环增强，并接入 Golden QA / RAG Eval。
6. README、演示文档、学习文档和面试文档。

## 2. 后端技术栈和版本

来源：`pom.xml`

| 技术 | 版本 / 说明 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.3.5 |
| Spring AI | 1.1.4 |
| MyBatis-Plus | 3.5.7 |
| MySQL Driver | `mysql-connector-j` |
| Redis | `spring-boot-starter-data-redis` |
| Spring Security | `spring-boot-starter-security` |
| JWT | 0.12.5 |
| MinIO SDK | 8.5.7 |
| 阿里云 OSS SDK | 3.17.4 |
| Milvus Java SDK | 2.3.4 |
| Lucene SmartCN | 8.11.3 |
| FastJSON2 | 2.0.47 |
| PDFBox | 3.0.1 |
| Apache POI | 5.2.5 |
| OpenCSV | 5.9 |
| Jsoup | 1.17.2 |
| Spring AI MCP Server WebMVC | 已引入 |

实际版本与部分文档中的“Spring Boot 3.4”说法不一致，应以 `pom.xml` 为准。

## 3. 前端技术栈和版本

来源：`MindCrew-frontend/package.json`

| 技术 | 版本 / 说明 |
| --- | --- |
| Vue | 3.5.29 |
| Vite | 7.3.1，实际安装输出为 7.3.5 |
| TypeScript | 5.9.3 |
| vue-tsc | 3.2.5 |
| Vue Router | 5.0.3 |
| Pinia | 3.0.4 |
| Element Plus | 2.13.5 |
| ECharts | 6.0.0 |
| Axios | 1.13.6 |
| Marked | 17.0.4 |
| Highlight.js | 11.11.1 |

## 4. 项目目录结构

```text
MindCrew/
  docker/                         Docker 相关配置
  docs/                           技术文档、八股文、知识库种子文档
  MindCrew-frontend/              Vue3 前端
  sql/                            数据库初始化与迁移脚本
  src/
    main/
      java/com/simon/MindCrew/
        agent/                    Agent 主链路、QueryRouter、工具上下文、自反思
        common/                   通用结果、异常、审计、加密工具
        config/                   Spring AI、MCP、Milvus、MinIO、Redis、安全配置
        controller/               REST / SSE / WebSocket 控制器
        crew/                     Multi-Agent 调研相关实验模块
        entity/                   MyBatis-Plus 实体
        mapper/                   Mapper 接口
        mcp/                      MCP / Spring AI Tool 工具
        service/                  业务服务
        service/rag/              旧版 RAG Pipeline、检索、RRF、rerank
      resources/
        application.yml           主配置
        prompts/                  Prompt 模板
    test/                         单元测试与 SpringBoot 上下文测试
```

## 5. 当前 RAG 主链路入口

### 5.1 当前主入口

主对话入口在：

- `src/main/java/com/simon/MindCrew/controller/MindCrewChatController.java`
  - `GET /api/v2/chat/stream`
  - 使用 `SseEmitter`
  - 调用 `MindCrewAgent.execute(...)`

核心 Agent 在：

- `src/main/java/com/simon/MindCrew/agent/MindCrewAgent.java`
  - 负责 SSE 输出、会话保存、检索调用、工具调用、引用来源、MCP 调用记录和自反思记录。

### 5.2 旧版 RAG Pipeline

还存在一条旧版 RAG Pipeline：

- `src/main/java/com/simon/MindCrew/controller/ChatController.java`
- `src/main/java/com/simon/MindCrew/service/rag/RagPipeline.java`

该链路使用 `MedConversation`、`MedMessage`、`MedKnowledgeBase` 旧实体，并双写部分 `qa_message`。后续优化应以 `MindCrewChatController` + `MindCrewAgent` 为主，避免继续强化旧 `Med*` 链路。

## 6. 向量检索、BM25、RRF、rerank 位置

| 能力 | 代码位置 | 审计结论 |
| --- | --- | --- |
| 向量检索 | `src/main/java/com/simon/MindCrew/service/rag/VectorRetriever.java` | 真实存在，基于 embedding + Milvus |
| BM25 检索 | `src/main/java/com/simon/MindCrew/service/rag/BM25Retriever.java` | 真实存在，结合 MySQL FULLTEXT 候选和 Lucene SmartCN 分词打分 |
| 中文分词 | `src/main/java/com/simon/MindCrew/service/rag/ChineseTextTokenizer.java` | 真实存在 |
| RRF 融合 | `src/main/java/com/simon/MindCrew/service/rag/RRFFusion.java` | 真实存在 |
| Cross-Encoder rerank | `src/main/java/com/simon/MindCrew/service/rag/CrossEncoderReranker.java` | 真实存在，调用 DashScope gte-rerank |
| Prompt 组装 | `src/main/java/com/simon/MindCrew/service/rag/PromptAssembler.java` | 真实存在 |

注意：`VectorRetriever` 在 embedding 失败时存在随机向量 fallback 行为，后续 RAG Eval 时必须标记为降级结果，不能把这种结果写成真实高质量召回。

## 7. Tool Calling / Function Calling 位置

| 能力 | 代码位置 | 审计结论 |
| --- | --- | --- |
| Spring AI Tool 注册 | `src/main/java/com/simon/MindCrew/config/McpToolsConfig.java` | 真实存在，注册多个 Tool Bean |
| 文档语义检索工具 | `src/main/java/com/simon/MindCrew/mcp/DocSearchTool.java` | 真实存在，`@Tool` |
| 关键词检索工具 | `src/main/java/com/simon/MindCrew/mcp/KeywordSearchTool.java` | 真实存在，`@Tool` |
| 联网搜索工具 | `src/main/java/com/simon/MindCrew/mcp/WebSearchTool.java` | 真实存在，`@Tool` |
| 记忆工具 | `src/main/java/com/simon/MindCrew/mcp/MemoryTool.java` | 真实存在，包含 recall/store |
| 工具上下文 | `src/main/java/com/simon/MindCrew/agent/AgentToolContext.java` | 真实存在，使用 ThreadLocal 传递用户、知识库范围和工具结果 |
| Agent 工具调用 | `src/main/java/com/simon/MindCrew/agent/MindCrewAgent.java` | 真实存在，使用 `ChatClient.defaultTools(toolCallbackProvider)` |

## 8. SSE 流式问答位置

| 入口 | 代码位置 | 审计结论 |
| --- | --- | --- |
| 新版 SSE 问答 | `MindCrewChatController.stream` | 真实存在，`/api/v2/chat/stream` |
| Agent SSE 输出 | `MindCrewAgent.execute` | 真实存在 |
| 旧版 SSE 问答 | `ChatController` + `RagPipeline` | 真实存在，但属于旧链路 |
| Multi-Agent SSE | `CrewController` + `CrewOrchestrator` | 真实存在，用于调研任务流式输出 |

## 9. MCP 相关代码是否真实存在

真实存在。

主要位置：

- `pom.xml`
  - 已引入 `spring-ai-starter-mcp-server-webmvc`
- `src/main/java/com/simon/MindCrew/config/McpToolsConfig.java`
  - 注册 MCP 工具
- `src/main/java/com/simon/MindCrew/mcp/*.java`
  - `DocSearchTool`
  - `KeywordSearchTool`
  - `WebSearchTool`
  - `MemoryTool`
- `src/main/java/com/simon/MindCrew/controller/McpConsoleController.java`
  - MCP 控制台接口
- `src/main/java/com/simon/MindCrew/entity/McpToolRegistry.java`
  - MCP 工具注册实体
- `sql/docmind-init.sql`
  - 包含 `mcp_tool_registry` 表

当前只确认 MCP 工具注册和控制台能力存在；完整复杂的 MCP 工具网关、Client 级鉴权、限流和审计本次不作为第一版重点。

## 10. 当前数据库表结构

根据 `sql/*.sql` 扫描，当前已发现表：

| SQL 文件 | 表 |
| --- | --- |
| `agent-crew-schema.sql` | `agent_task`, `agent_step` |
| `api-key-schema.sql` | `api_key`, `api_call_log` |
| `audit-pii-schema.sql` | `audit_log`, `pii_config` |
| `coach-schema.sql` | `coach_session`, `coach_question`, `coach_answer` |
| `dept-position-acl-schema.sql` | `sys_department`, `sys_position`, `kb_acl` |
| `docmind-init.sql` | `kb_chunk`, `kb_knowledge_base`, `mcp_tool_registry`, `qa_conversation`, `qa_message`, `sys_ai_config`, `sys_user` |
| `feedback-golden-schema.sql` | `qa_feedback`, `qa_golden_pair` |
| `kb-category-schema.sql` | `kb_category` |
| `llm-provider-schema.sql` | `llm_provider` |
| `persona-schema.sql` | `system_persona` |
| `usage-stats-schema.sql` | `model_pricing`, `usage_daily` |
| `voice-persona-schema.sql` | `voice_persona` |

未发现以下第一版目标表：

- `rag_eval_dataset`
- `rag_eval_case`
- `rag_eval_run`
- `rag_eval_result`
- `agent_trace`
- `agent_trace_span`
- `safety_event_log`

这些需要新增 SQL 脚本和对应实体 / Mapper / Service。

## 11. README 与文档一致性

当前项目根目录未发现 `README.md`。

已有文档包括：

- `项目介绍.md`
- `快速部署.md`
- `DEPLOY.md`
- `docs/interview-questions.md`
- `docs/kb-tech-agentic-rag-and-mcp.md`
- `docs/kb-tech-architecture.md`
- `docs/kb-tech-ingestion-and-retrieval.md`
- 其他八股文和部署文档

与代码基本一致的能力：

- Spring Boot + Spring AI 后端。
- Vue3 前端。
- SSE 流式问答。
- Milvus 向量检索。
- BM25 检索。
- RRF 融合。
- DashScope rerank。
- Tool Calling。
- MCP 工具注册。
- 反馈审核和 Golden Pair 基础功能。
- MinIO / OSS 文件存储配置。

可能夸大或需要修正的能力：

- 如果文档写 Spring Boot 3.4，与 `pom.xml` 不一致，应改为 3.3.5。
- 如果文档写完整 RAG Eval，目前未发现。
- 如果文档写完整 Agent Trace，目前未发现独立 trace/span 表和页面。
- 如果文档写完整 Prompt Injection 防护，目前未发现；只发现旧 `SafetyGuard` 做急救关键词、低置信兜底和答案自评。
- 如果文档写 SelfReflection 自动二次检索和重生成，需要谨慎。当前 `MindCrewAgent` 中自反思更偏记录与校验，不应写成完整自动纠错闭环。
- 如果文档写企业级 MCP 网关、Client 级限流、授权审计，目前未发现完整实现。

## 12. 密钥、`.env` 与硬编码风险

### 12.1 `.env`

项目内存在：

- `.env`
- `.env.docker.example`

`.env` 中发现以下敏感变量名，不在报告中记录具体值：

- `BAILIAN_API_KEY`
- `TAVILY_API_KEY`
- `MYSQL_ROOT_PASSWORD`
- `REDIS_PASSWORD`
- `MINIO_ROOT_PASSWORD`
- `OSS_ACCESS_KEY`
- `OSS_SECRET_KEY`
- `CRYPTO_MASTER_KEY`
- `BSS_ACCESS_KEY`
- `BSS_SECRET_KEY`

处理要求：

- 删除仓库内 `.env`。
- 新增 `.env.example`，只保留占位符。
- 已经出现过的真实 Key 必须在云平台轮换。

### 12.2 `application.yml`

发现硬编码或默认密钥风险：

- `crypto.master-key` 默认值为 `please-change-me-in-production-32chars-min`
- `jwt.secret` 当前为固定字符串
- `mindcrew.web-search.api-key` 当前带默认 Tavily Key
- `minio.access-key` / `minio.secret-key` 使用默认 `minioadmin`
- `oss.access-key` / `oss.secret-key` 使用 TODO 占位符

处理要求：

- 默认生产密钥全部改成环境变量占位。
- Tavily 默认 Key 应移除，改成空值或 `${TAVILY_API_KEY:}`。
- 测试 profile 提供 dummy 配置，不能依赖真实 Key。

## 13. 构建与测试基线

### 13.1 后端 package

命令：

```bash
mvn -DskipTests package
```

结果：通过。

说明：

- 生成 `target/MindCrew-0.0.1-SNAPSHOT.jar`
- 构建耗时约 25 秒

### 13.2 后端 test

命令：

```bash
mvn test
```

结果：失败。

已通过的测试包括：

- `ExplicitMemoryExtractorTest`
- `QueryRouterToolSelectionTest`
- `MindCrewChatControllerTest`
- `MemoryToolTest`
- `WebSearchToolTest`
- 多个 service/rag 单测

失败测试：

- `com.simon.MindCrew.MediRagApplicationTests.contextLoads`

失败原因：

```text
Could not resolve placeholder 'BAILIAN_API_KEY' in value "${BAILIAN_API_KEY}"
```

结论：

- 单测基础不差。
- SpringBoot 上下文测试当前依赖真实环境变量，需要新增 `application-test.yml` 和 test profile。

### 13.3 前端 build

命令：

```bash
npm run build
```

结果：

- 在当前沙箱内首次运行失败：`ERROR: spawn EPERM`
- 非沙箱真实环境运行通过

说明：

- Vite 构建成功。
- 存在大 chunk 警告，主要来自 Element Plus / ECharts，不影响第一版交付。

## 14. 当前最适合优先优化的模块

### P0：仓库清理与测试 profile

原因：

- 真实 `.env` 和默认 Key 是公开仓库最大风险。
- `mvn test` 当前失败，会影响项目可信度。
- 当前不是 Git 仓库，无法满足“每模块 commit”要求。

### P0：RAG Eval

原因：

- 当前已有检索、rerank、Golden Pair 基础，但缺少评测体系。
- 这是最容易体现工程深度的模块。

### P0：Agent Trace

原因：

- 当前 Agent 链路较长，面试时不展示 Trace 很难讲清楚。
- Trace 也能承载安全事件和 RAG Eval 的排查证据。

### P1：Prompt Injection 防护

原因：

- 当前旧 `SafetyGuard` 不覆盖提示词注入、凭据窃取、工具滥用等场景。
- 这部分实现范围可控，展示价值高。

### P1：反馈闭环增强

原因：

- 项目已有 `qa_feedback`、`qa_golden_pair`、前端反馈审核和 Golden Pair 页面。
- 需要补失败原因、来源修正、转 RAG Eval Case 的链路。

## 15. 不能直接写进简历的点

在完成后续代码前，以下内容不能写进简历：

- 已实现完整 RAG Eval 体系。
- 已实现 Agent Trace 时间线页面。
- 已实现 Prompt Injection 全链路防护。
- 已实现安全事件审计。
- 已实现反馈自动进入 RAG Eval。
- 已实现企业级 MCP 工具网关、Client 级权限、限流和审计。
- 已经取得某个准确率、QPS、DAU 等指标。

## 16. 下一步执行计划

下一步进入模块 1：

1. 初始化 Git 仓库或在用户提供的新 GitHub 仓库工作目录中操作。
2. 新增项目内 `.gitignore`。
3. 删除或清理 `.env`，新增 `.env.example`。
4. 清理 `application.yml` 默认密钥。
5. 新增 `application-test.yml`。
6. 修复 `MediRagApplicationTests` 使用 test profile。
7. 统一 README、前端标题、接口文档中的 MindCrew 对外名称。
8. 重新运行 `mvn -DskipTests package`、`mvn test`、`npm run build`。
