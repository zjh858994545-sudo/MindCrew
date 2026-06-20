# MindCrew 面试讲稿

## 30 秒版本

MindCrew 是我做的企业知识库 Agent 项目。它基于 Spring Boot、Vue、Milvus、MySQL 和 Spring AI，实现了多路召回、RRF 融合、Rerank、SSE 流式问答、MCP 工具调用。我重点补了三类工程化能力：RAG Eval 评测体系、Agent Trace 可观测性、Prompt Injection Safety Guard，再加上 HITL 反馈到 Golden Pair 的闭环。

## 2 分钟版本

这个项目的核心链路是用户提问后进入 MindCrewAgent，先做安全检查，再做 query rewrite，然后并行走向量召回和 BM25 召回，之后用 RRF 融合、Rerank 排序、上下文压缩，最后组装 Prompt 调用 LLM 流式生成。

我觉得普通 RAG 项目的问题是“能跑但不好证明效果”，所以我加了 RAG Eval：内置 30 条 Golden QA，对比 vector、BM25、hybrid、hybrid+rerank 四种策略，用 Recall@K、MRR、引用命中率和拒答准确率评价。

另一个问题是 Agent 太黑盒，所以我加了 Agent Trace。每次请求会生成 traceId，query rewrite、工具调用、向量召回、BM25、RRF、Rerank、Prompt 构造、LLM 生成都会记录成 span，方便排查召回差、引用错或耗时高。

安全方面，我在输入、检索内容、工具调用、最终答案四个点加了 Safety Guard，能拦截 Prompt Injection、系统提示词泄露、密钥泄露和越权工具调用，并写入安全事件日志。

最后我做了 HITL Feedback，用户点踩可以结构化提交失败原因、纠正答案和引用源，管理员审核后进入 Golden Pair，让系统持续变好。

## 面试官可能追问

### 为什么要做 RAG Eval？

因为 RAG 调优不能只看主观体验。不同 chunk size、topK、rerank 策略都会影响效果，需要固定 Golden QA 和指标，才能知道改动是提升还是退化。

### 为什么 Agent Trace 重要？

Agentic RAG 有多个步骤，答案差可能是 query rewrite 错、召回没命中、rerank 排错、prompt 太长或模型生成幻觉。Trace 可以把一次问答拆成 span，定位问题阶段。

### 如何防 Prompt Injection？

我做了四层：输入拦截、检索内容净化、工具调用授权检查、最终答案脱敏。这样可以防直接攻击，也能防知识库文档里夹带的间接注入。

### 反馈闭环怎么产生价值？

用户点踩后不仅记录差评，还记录失败原因、纠正答案和正确引用源。审核后成为 Golden Pair，既能用于线上快路径，也能沉淀成后续评测样本。

## 简历项目描述

MindCrew 企业知识库 Agent 平台：基于 Spring Boot、Spring AI、Vue3、Milvus、MySQL 构建企业级 Agentic RAG 系统，支持 Query Rewrite、向量/BM25 多路召回、RRF 融合、Rerank、SSE 流式问答、MCP 工具调用与引用溯源。主导补齐 RAG Eval、Agent Trace、Prompt Injection Safety Guard、HITL Feedback 闭环：实现 30 条 Golden QA、4 类检索策略对比、Recall@K/MRR/CitationHit/拒答准确率评测；将 Agent 执行链路拆分为 Query Analysis、Tool Call、Retrieval、RRF、Rerank、LLM Generation 等 Span；在输入、检索内容、工具调用、输出阶段拦截 Prompt Injection 与密钥泄露；支持用户差评原因、纠正答案、引用源审核沉淀为 Golden Pair，提升系统可评测性、可观测性与持续优化能力。
