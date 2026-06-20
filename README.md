# MindCrew

MindCrew 是一个面向企业知识库问答的 Agentic RAG 项目，核心能力包括多路召回、RRF 融合、重排序、SSE 流式问答、MCP 工具调用、RAG 评测、Agent Trace 可观测性、Prompt Injection 安全防护和 HITL 反馈闭环。

## 项目亮点

- Agentic RAG：支持查询改写、向量召回、BM25 召回、RRF 融合、Rerank、上下文压缩和引用溯源。
- RAG Eval：内置 30 条 Golden QA 和 4 种检索策略评测，输出 Recall@K、MRR、CitationHit、拒答准确率和 JSON 报告。
- Agent Trace：记录 Query Analysis、Tool Call、Vector/BM25 Retrieval、RRF、Rerank、Context Build、LLM Generation 等链路。
- Safety Guard：对用户输入、检索内容、工具调用、最终答案做 Prompt Injection、系统提示词泄露、密钥泄露和越权调用防护。
- HITL Feedback：用户可提交差评原因、纠正答案和纠正引用源，管理员审核后沉淀为 Golden Pair。
- 工程化交付：密钥从仓库移除，提供 `.env.example`、测试 Profile、SQL 迁移脚本、架构文档和演示脚本。

## 技术栈

- 后端：Java 17、Spring Boot 3.3、Spring AI、MyBatis-Plus、MySQL、Redis、Milvus、MinIO。
- 前端：Vue 3、TypeScript、Vite、Element Plus、ECharts。
- 评测与可观测：RAG Eval、Agent Trace、Safety Event Log。

## 本地启动

1. 准备环境变量：

```powershell
Copy-Item .env.example .env
```

按需填写 `.env` 中的 `BAILIAN_API_KEY`、数据库、Redis、MinIO、Milvus 等配置。不要把 `.env` 提交到 Git。

2. 初始化数据库脚本：

当前 SQL dump 的历史库名仍为 `docmind`，这是兼容原始 schema 的数据库名；项目外显名称和应用名统一为 MindCrew。

```powershell
mysql -u root -p docmind < sql/rag-eval-schema.sql
mysql -u root -p docmind < sql/agent-trace-safety-schema.sql
mysql -u root -p docmind < sql/feedback-loop-migration.sql
```

3. 启动后端：

```powershell
mvn spring-boot:run
```

4. 启动前端：

```powershell
cd MindCrew-frontend
npm install
npm run dev
```

## 验证命令

```powershell
mvn test
mvn -DskipTests package
cd MindCrew-frontend
npm run build
```

也可以直接运行：

```powershell
.\scripts\verify-local.ps1
```

如果本机 PowerShell 允许 Vite/esbuild 子进程，也可以运行完整前端生产构建：

```powershell
.\scripts\verify-local.ps1 -IncludeFrontendBuild
```

当前已验证：后端 `33` 个测试通过，前端生产构建通过。

## 核心页面

- `/chat`：知识库问答、流式响应、引用源、用户反馈。
- `/rag-eval`：RAG 评测配置、运行结果、策略对比、报告导出。
- `/agent-trace`：Agent 执行链路、Span 明细、安全事件。
- `/feedback-review`：反馈审核与 Golden Pair 沉淀。
- `/golden-pairs`：高质量问答样本管理。

## 关键接口

- `POST /api/v2/chat/stream`：MindCrew 主问答入口。
- `POST /api/v2/rag-eval/runs`：运行 RAG 评测。
- `GET /api/v2/rag-eval/report`：获取最近一次评测报告。
- `GET /api/v2/agent-traces`：获取 Trace 列表。
- `GET /api/v2/agent-traces/{traceId}`：获取 Trace 详情。
- `GET /api/v2/agent-traces/safety-events`：获取安全事件。
- `POST /api/feedback`：提交用户反馈。

## 文档导航

- [代码审计报告](docs/CODE_AUDIT_REPORT.md)
- [架构设计](docs/ARCHITECTURE.md)
- [RAG Eval 设计](docs/RAG_EVAL_DESIGN.md)
- [Agent Trace 设计](docs/AGENT_TRACE_DESIGN.md)
- [Safety Guard 设计](docs/SAFETY_GUARD_DESIGN.md)
- [Feedback Loop 设计](docs/FEEDBACK_LOOP_DESIGN.md)
- [演示指南](docs/DEMO_GUIDE.md)
- [面试讲稿](docs/INTERVIEW_NOTES.md)
- [踩坑记录](docs/PITFALLS.md)
- [优化变更日志](docs/CHANGELOG_OPTIMIZATION.md)
