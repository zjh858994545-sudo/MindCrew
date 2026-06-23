# MindCrew 完整演示验收清单

这份清单用于面试前自测，目标是保证 8 到 12 分钟内能稳定展示项目核心价值。

## 演示前准备

- [ ] Docker 中间件已启动：MySQL、Redis、MinIO、etcd、Milvus。
- [ ] MySQL 已执行核心 SQL：
  - [ ] `sql/docmind-init.sql`
  - [ ] `sql/rag-eval-schema.sql`
  - [ ] `sql/agent-trace-safety-schema.sql`
  - [ ] `sql/mcp-governance-schema.sql`
  - [ ] `sql/service-desk-loop-schema.sql`
- [ ] 后端已启动，无数据库连接错误。
- [ ] 前端已启动，能访问登录页和主页面。
- [ ] `.env` 或系统环境变量中已配置真实 `BAILIAN_API_KEY`。
- [ ] 如果暂时没有真实 Key，确认能讲清楚 dummy key 下的候选样本兜底逻辑。
- [ ] 浏览器提前打开：服务台闭环、RAG 评测、Agent Trace、MCP 控制台。

## 演示步骤

### 1. 服务台业务入口

- [ ] 进入“服务台闭环”页面。
- [ ] 展示工单队列、知识域筛选、状态、优先级、申请人。
- [ ] 说明这是企业内部 HR/IT/财务/安全/销售的真实知识服务台场景。

讲法：

> 我没有把项目停留在普通聊天问答，而是抽象成企业服务台工单。这样能体现真实业务入口、人工审核和知识运营闭环。

### 2. 生成 AI 答复草稿

- [ ] 选择安全合规类工单，例如“客户要求导出全量原始日志”。
- [ ] 点击“生成答复”。
- [ ] 展示答复草稿、引用来源、置信度、事件时间线。

讲法：

> 这里不是固定模板，后端会调用 MindCrewAgent。Agent 内部经过 QueryPlanner、向量/BM25 混合召回、RRF、Rerank 和 LLM 生成。

### 3. Trace ID 跳转排障

- [ ] 点击 AI 草稿区域的 Trace ID。
- [ ] 跳转到 Agent Trace 页面。
- [ ] 展示 Span 时间线：QueryPlanner、检索、融合、重排、上下文构造、LLM 生成、安全检查。

讲法：

> Agent 系统容易黑盒，所以我把一次请求拆成 Span。答案错了可以定位是改写问题、召回问题、重排问题还是模型生成问题。

### 4. 采纳沉淀 Golden Pair

- [ ] 回到服务台页面。
- [ ] 点击“采纳沉淀”。
- [ ] 展示状态变为已采纳。
- [ ] 展示 Golden Pair ID 或 Golden Pair 候选状态。
- [ ] 展示事件时间线中出现 `GOLDEN_PAIR_SYNCED` 和 `RAG_EVAL_CASE_SYNCED`。

讲法：

> 人工采纳后，答案不会只停留在工单里，而是同步成 Golden Pair，并自动变成 RAG Eval 动态评测用例。

### 5. RAG Eval 质量评测

- [ ] 进入“RAG 评测”页面。
- [ ] 运行默认评测。
- [ ] 展示策略对比：Vector Only、BM25、Hybrid、Hybrid + Rerank。
- [ ] 讲清楚 Recall@K、MRR、Hit@K、CitationHit 的意义。

讲法：

> RAG 调优不能只看主观体验，所以我用固定评测集和指标对比检索策略，服务台采纳样本也会进入动态评测集。

### 6. 驳回补知识

- [ ] 选择另一个工单。
- [ ] 点击“驳回补知识”。
- [ ] 展示知识缺口指标增加或事件出现 `KNOWLEDGE_GAP_CREATED`。

讲法：

> 答案不满意不是简单丢弃，而是转成知识缺口，让知识运营人员后续补 SOP、重建向量，再进入评测闭环。

### 7. MCP 治理

- [ ] 打开“MCP 控制台”。
- [ ] 展示工具策略、限流、审计日志。

讲法：

> Agent 能调工具后必须做治理，否则容易越权调用。我这里做了 client/tool policy、rate limit 和 audit log。

## 验收命令

```powershell
mvn test
cd MindCrew-frontend
npm run build
```

通过标准：

- [ ] 后端测试通过。
- [ ] 前端构建通过。
- [ ] 服务台页面可访问。
- [ ] 生成草稿不会报错。
- [ ] Trace ID 可跳转 Agent Trace。
- [ ] 采纳后能同步 Golden Pair 或进入候选兜底状态。
- [ ] RAG Eval 页面可运行。

## 面试风险预案

### 真实模型 Key 失效

说法：

> 真实 Key 失效时，系统不会假装成功，而是保留 Golden Pair 候选并记录事件。这是 AI 链路失败不阻塞业务工单的可靠性设计。

### Milvus 没启动

说法：

> Milvus 是向量检索层，如果演示环境没启动，BM25 和本地兜底逻辑仍能保留业务流程。生产环境会通过健康检查和 Docker Compose 保证依赖启动。

### 回答质量不稳定

说法：

> 这也是为什么我设计了人工审核、RAG Eval 和知识缺口。大模型输出不是一次性可信，而是要进入评测和反馈闭环。
