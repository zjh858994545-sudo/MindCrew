# MindCrew 演示指南

这份文档用于面试时快速演示。完整逐项验收请看 [演示验收清单](DEMO_ACCEPTANCE_CHECKLIST.md)。

## 演示目标

在 8 到 12 分钟内让面试官看到：MindCrew 不是普通知识库问答，而是一个企业知识服务台 Agentic RAG 闭环。

核心链路：

```text
服务台工单 -> MindCrewAgent -> QueryPlanner -> RAG -> AI 草稿
-> 人工审核 -> Golden Pair -> RAG Eval -> Agent Trace -> 知识持续优化
```

## 演示前准备

```powershell
docker compose up -d mysql redis minio minio-init etcd milvus
mysql -u root -p docmind < sql/docmind-init.sql
mysql -u root -p docmind < sql/rag-eval-schema.sql
mysql -u root -p docmind < sql/agent-trace-safety-schema.sql
mysql -u root -p docmind < sql/mcp-governance-schema.sql
mysql -u root -p docmind < sql/service-desk-loop-schema.sql
mvn spring-boot:run
cd MindCrew-frontend
npm run dev
```

验证命令：

```powershell
mvn test
cd MindCrew-frontend
npm run build
```

## 演示顺序

### 1. 服务台闭环

打开 `/service-desk`，选择安全合规工单，例如“客户要求导出全量原始日志”。

重点展示：

- 工单有业务属性：部门、申请人、优先级、知识域。
- 点击“生成答复”后，系统输出 AI 草稿、引用来源、置信度、Trace ID。
- 低置信度会进入人工复核，不让 AI 直接冒充确定答案。

讲法：

> 我把知识库问答抽象成企业服务台，因为真实业务里问题不是问完就结束，而是要经过审核、沉淀、评测和知识运营。

### 2. Trace 跳转

点击服务台草稿上的 Trace ID，跳到 `/agent-trace?traceId=...`。

重点展示：

- QueryPlanner Span。
- Vector/BM25 Retrieval Span。
- RRF/Rerank Span。
- LLM Generation Span。
- Safety Event。

讲法：

> Agent 系统如果没有 Trace，很难定位问题。我这里把一次请求拆成多个 Span，可以判断问题出在改写、召回、重排还是生成。

### 3. 采纳沉淀

回到服务台，点击“采纳沉淀”。

重点展示：

- 工单状态变为已采纳。
- 事件时间线出现 Golden Pair 同步。
- 同步成功后自动写入 RAG Eval Case。
- 如果真实模型 Key 或 Milvus 暂不可用，会保留为 Golden Pair 候选，后续可重试。

讲法：

> 采纳样本不会只存在工单里，而是变成 Golden Pair，并进入 RAG Eval 动态评测集。这就是持续优化闭环。

### 4. RAG Eval

打开 `/rag-eval`，运行默认评测。

重点展示：

- 30 条内置 Golden QA。
- 服务台采纳样本动态进入评测 Case。
- Vector Only、BM25、Hybrid、Hybrid + Rerank 多策略对比。
- Recall@K、MRR、Hit@K、CitationHit 指标。

讲法：

> RAG 调优不能只凭感觉。固定评测集和指标能证明策略调整到底是提升还是退化。

### 5. 驳回补知识

回到服务台，选择另一个工单，点击“驳回补知识”。

重点展示：

- 工单进入已驳回。
- 生成知识缺口。
- 指标里知识缺口数量变化。

讲法：

> 失败样本也有价值。驳回后进入知识缺口池，后续补 SOP、重建向量，再回到评测体系。

### 6. MCP 治理

打开 `/mcp`。

重点展示：

- client/tool policy。
- rate limit。
- audit log。

讲法：

> Agent 能调用工具后必须有治理，否则会有越权和滥用风险。我这里做了工具权限、限流和审计。

## 结束语

> MindCrew 的核心价值不是“能聊天”，而是把企业知识问答做成可控闭环：有业务入口、有检索规划、有人工质量闸门、有评测指标、有 Trace 排障、有安全和工具治理。
