# MindCrew 面试讲稿

## 30 秒版本

MindCrew 是我做的一个企业知识服务台 Agentic RAG 系统。它不是普通知识库问答，而是把文档清洗、语义元数据增强、业务工单、MindCrewAgent、QueryPlanner、RAG 检索、人工审核、Golden Pair、RAG Eval 和 Agent Trace 串成闭环。用户提交工单后，系统会基于企业制度知识生成可引用的 AI 草稿；人工采纳后沉淀为 Golden Pair，并自动进入 RAG Eval 评测集；如果答案质量不足，会进入知识缺口池。这个项目重点体现 AI 应用工程化能力：可运行、可评测、可追踪、可持续优化。

## 2 分钟版本

这个项目的业务场景是企业内部知识服务台，比如 HR、IT、财务、安全合规、销售商务等部门每天会处理大量制度类、流程类和权限类问题。传统 FAQ 很难覆盖复杂问题，所以我把它设计成 Agentic RAG Copilot。

文档入库阶段，我会先做清洗质量分析，统计噪音行、重复行、超长段落、清洗前后字符数和质量分；同时生成摘要、关键词和可回答问题，这些元数据会作为 embedding 前缀增强语义召回，也会展示在知识库详情页。

用户提交工单后，后端会调用 MindCrewAgent。Agent 先经过 Safety Guard 做输入安全检查，然后进入 QueryPlanner，识别意图，生成 query variants 和 HyDE 查询，并根据召回质量决定是否二次检索。检索层同时走 Milvus 向量召回和 MySQL BM25 关键词召回，再用 RRF 融合、父子/邻接 chunk 扩展、Rerank 重排、上下文压缩，最后组装 Prompt 调用大模型生成答复草稿。

我重点做的不是“让模型回答一下”，而是补齐企业落地必需的闭环。服务台页面支持人工审核：采纳的答案会同步成 Golden Pair，同时自动写入 RAG Eval Case；驳回的答案会生成知识缺口，后续补 SOP 后可以重建向量。RAG Eval 支持多策略对比，比如 Vector Only、BM25、Hybrid、Hybrid + Rerank，用 Recall@K、MRR、Hit@K、CitationHit 等指标评价效果。

另外我加了 Agent Trace。每次问答都有 traceId，可以从服务台页面一键跳到 Trace 页面，看到 QueryPlanner、检索、融合、重排、上下文构造、LLM 生成、安全事件等 Span。这能解决 Agent 黑盒问题，方便定位是问题改写错、召回没命中、重排错，还是模型生成偏了。

安全和治理上，我补了 MCP 权限治理，包括 client/tool policy、rate limit 和 audit log；Safety Guard 会在输入、检索内容、工具调用和最终输出阶段防 Prompt Injection、密钥泄露和越权调用。

所以 MindCrew 的定位不是一个 Demo，而是一个准生产级 AI 应用原型：有真实业务入口，有 RAG/Agent 技术链路，有人工质量闸门，有评测体系，有可观测性，也有失败样本持续优化机制。

## 5 分钟深讲结构

### 1. 为什么选企业知识服务台

企业知识库问答是 RAG 最常见的落地场景，但真实企业里不是用户随便问一句就结束。真实链路里会有工单入口、知识域、审批规则、风险红线、人工审核、质检指标和知识运营。服务台场景能把这些都串起来，比普通“文档问答”更能体现工程价值。

### 2. 核心链路

`工单 -> Safety Guard -> QueryPlanner -> Vector/BM25 -> RRF -> Rerank -> LLM Draft -> HITL -> Golden Pair/RAG Eval/Knowledge Gap`

其中 QueryPlanner 负责把用户问题变成更适合检索的问题，RAG 检索负责找到证据，人工审核负责把质量控制权交回业务专家，RAG Eval 负责让优化有指标。

### 3. 我解决了哪些普通 RAG 项目的问题

- 普通 RAG 只看主观效果，MindCrew 用 RAG Eval 做量化评测。
- 普通 Agent 黑盒难排障，MindCrew 用 Trace Span 拆开链路。
- 普通系统答错后只记录差评，MindCrew 把差评转成知识缺口或 Golden Pair。
- 普通 RAG 只命中单个 chunk，MindCrew 会补回同文档相邻上下文，降低“搜到了但答不出来”的概率。
- 普通工具调用缺治理，MindCrew 做了 MCP policy、rate limit、audit log。
- 普通项目没有业务闭环，MindCrew 用服务台工单把场景跑完整。

### 4. 可以怎么扩展

后续可以接企业 SSO、组织权限、多租户、Flyway SQL 迁移、CI/CD、线上监控告警、RAG Eval 定时任务、知识缺口处理页和 RAG 质量趋势图。

## 高频追问准备

### 为什么要同时做向量召回和 BM25？

向量召回更擅长语义相似，BM25 更擅长精确关键词、制度编号、专有名词。企业知识里经常有权限名、流程名、系统名、合同编号，只靠向量可能漏掉；混合召回再用 RRF 融合可以提高稳定性。

### HyDE 有什么作用？

HyDE 会先生成一个“假设性答案/文档”，再用它去检索。对于用户问题很短或表达不完整的情况，它能补充语义上下文，提高召回命中率。但它也可能引入偏差，所以我把它放在 QueryPlanner 里，并配合低置信度 retry policy 使用。

### RAG Eval 指标怎么看？

Recall@K 看期望证据是否被召回；MRR 看正确证据排得靠不靠前；Hit@K 看 TopK 里有没有命中；CitationHit 看生成答案引用是否对齐证据。它们分别对应召回率、排序质量和答案可信度。

### 为什么要做清洗质量报告和元数据增强？

真实企业文档里会有页面 ID、编辑历史、重复标题、纯数字行、导出噪音等内容。如果不清洗，这些噪音会进入向量库影响召回。元数据增强里的摘要、关键词和可回答问题，则能缩小用户问法和文档表达之间的语义差距。

### 父子/邻接 chunk 召回解决什么问题？

有时向量检索命中了摘要或相邻段落，但真正答案在同一文档的下一个 chunk。MindCrew 会根据 kbId 和 chunkIndex 回 MySQL 拉取同文档相邻切片，小文档直接补全文档上下文，避免 TopK 截断导致模型明明搜到了却答不出来。

### Agent Trace 解决什么问题？

它把一次问答拆成多个 Span。比如答案错了，可以看 QueryPlanner 是否误判意图，检索是否没命中，Rerank 是否把正确片段排后面，Prompt 是否上下文不足，模型是否生成幻觉。没有 Trace，只能猜。

### 没有真实企业数据怎么办？

我构造的是合成但符合真实业务结构的数据，包括部门、角色、工单优先级、审批链路、安全红线和 SOP 片段。这样既避免隐私问题，又能完整展示企业 RAG 系统的业务闭环。

### 这个项目离生产还差什么？

作为实习项目，它已经具备准生产原型能力；真正生产还需要企业 SSO、多租户权限、CI/CD、线上监控告警、压测报告、灰度发布、数据备份和真实业务反馈。
