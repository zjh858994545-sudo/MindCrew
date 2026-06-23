# MindCrew 面试笔记

当前最新面试讲稿见：[INTERVIEW_SCRIPT.md](INTERVIEW_SCRIPT.md)。

简历项目描述见：[RESUME_PROJECT_DESCRIPTION.md](RESUME_PROJECT_DESCRIPTION.md)。

演示验收清单见：[DEMO_ACCEPTANCE_CHECKLIST.md](DEMO_ACCEPTANCE_CHECKLIST.md)。

## 核心定位

MindCrew 是一个企业知识服务台 Agentic RAG 系统，而不是普通文档问答。

一句话：

> 我做的是一个面向企业服务台的 Agent Copilot，把工单、RAG、人工审核、Golden Pair、RAG Eval 和 Agent Trace 串成了可运行、可评测、可追踪、可持续优化的闭环。

## 必讲亮点

- 服务台业务闭环：工单接入、AI 草稿、人工采纳、驳回补知识。
- QueryPlanner：intent、query variants、HyDE、低置信度 retry。
- 混合检索：Milvus 向量召回、BM25、RRF、Rerank。
- RAG Eval：内置 Case + 服务台动态 Case，多策略评测。
- Agent Trace：Trace ID 跳转 Span 时间线。
- MCP 治理：tool policy、rate limit、audit log。
- Safety Guard：Prompt Injection、防密钥泄露、防越权工具调用。

## 最容易被问的问题

### 为什么要做服务台场景？

因为真实企业知识库不是“问一句答一句”，而是有业务入口、制度依据、人工审核、质量评测和知识运营。服务台能体现真实业务闭环。

### 为什么要做 RAG Eval？

RAG 不能只凭主观体验调参。Recall@K、MRR、Hit@K、CitationHit 可以量化检索和引用效果，避免改动后效果退化。

### 为什么需要 Agent Trace？

Agentic RAG 链路长，答案错可能来自 query rewrite、召回、rerank、prompt 或模型生成。Trace 可以把一次请求拆成 Span，定位问题阶段。

### Golden Pair 和 RAG Eval 是什么关系？

Golden Pair 是人工确认过的高质量问答样本。MindCrew 会把服务台采纳样本同步成 Golden Pair，并自动写入 RAG Eval 动态 Case，让线上反馈反哺评测集。

### 没有真实公司数据怎么办？

项目使用合成但合理的企业数据，覆盖 HR、IT、财务、安全合规、销售商务等真实知识域。这样避免隐私风险，同时保留真实业务结构。

### 现在离生产还差什么？

作为实习项目已经是准生产原型；真正生产还需要企业 SSO、多租户权限、CI/CD、压测、监控告警、灰度发布、备份恢复和真实业务数据接入。
