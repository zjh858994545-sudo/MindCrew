# 优化变更日志

## 2026-06-20

### 仓库治理

- 初始化 Git 仓库。
- 删除敏感 `.env`。
- 增加 `.env.example` 和 `.gitignore`。
- 增加 `docs/CODE_AUDIT_REPORT.md`。
- 修复测试 profile，避免单元测试依赖真实外部密钥。

提交：`fd4f7e5 chore: clean repository secrets and add code audit`

### RAG Eval

- 增加 RAG Eval 实体、Mapper、Service、Controller。
- 增加 30 条内置 Golden QA。
- 支持 `VECTOR_ONLY`、`BM25_ONLY`、`HYBRID`、`HYBRID_RERANK`。
- 输出 Recall@K、Hit@K、MRR、CitationHit、RefusalAccuracy、AvgLatency。
- 增加 `/rag-eval` 前端页面。
- 增加 SQL 和单元测试。

提交：`ed2e464 feat(eval): add RAG evaluation workflow`

### Agent Trace 和 Safety Guard

- 增加 `agent_trace`、`agent_trace_span`、`safety_event_log`。
- 在主问答链路接入 Trace ID 和 Span 记录。
- 前端增加 `/agent-trace` 页面。
- 增加 Prompt Injection、安全脱敏、工具越权拦截、安全事件审计。
- Trace/Safety 落库失败时自动降级，不阻断主问答。
- 增加 Trace 与 Safety 单元测试。

提交：`5fb0614 feat(trace): add agent trace and safety guard`

### HITL Feedback

- 反馈增加 `failure_reason`。
- 反馈增加 `correction_sources`。
- 点踩时弹出结构化纠错对话框。
- 后端支持差评原因、纠正答案、纠正引用源入库。
- 增加反馈迁移 SQL。

提交：`00c4b40 feat(feedback): capture structured correction feedback`

### 交付硬化

- 增加 GitHub Actions CI，自动运行后端测试和前端构建。
- 增加 `scripts/verify-local.ps1`，用于本地验收后端测试、后端打包和前端类型检查。
- 增加 `scripts/quick-start-dev.ps1`，用于启动本地 Redis、MinIO、Milvus 等开发基础设施。
- 修正 README 和演示文档中的数据库初始化说明，明确 `docmind` 是兼容历史 schema 的数据库名。
- 将 Web Search 环境变量统一为 `MINDCREW_WEB_SEARCH_ENABLED`。
- 清理 Docker 环境模板中的旧变量名和疑似真实 Key 前缀。

## 当前验证

- `mvn test`：33 个测试通过。
- `npm run build`：前端生产构建通过。
- `.\scripts\verify-local.ps1`：默认本地验收通过。
