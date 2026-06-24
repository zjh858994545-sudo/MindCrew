# MindCrew 简历项目描述

## 推荐版本

**MindCrew 企业知识服务台 Agentic RAG 系统**  
技术栈：Java 17、Spring Boot、Spring AI、MyBatis-Plus、MySQL、Redis、Milvus、MinIO、Vue3、TypeScript、Element Plus

- 设计并实现面向企业知识服务台的 Agentic RAG 系统，覆盖工单接入、AI 答复草稿、人工审核、Golden Pair 沉淀、RAG Eval 评测和 Agent Trace 可观测闭环。
- 构建文档入库数据工程链路，支持清洗质量报告、噪音行/重复行/超长段落统计、文档摘要、关键词和可回答问题生成，并将语义元数据注入 embedding 前缀提升召回。
- 构建 MindCrewAgent + QueryPlanner 检索规划链路，支持意图识别、Query Variants、HyDE、低置信度二次检索、向量召回、BM25 召回、RRF 融合、父子/邻接 chunk 召回、Rerank 和引用溯源。
- 实现企业服务台闭环模块，支持 HR、IT、财务、安全合规、销售商务等知识域工单；采纳答案自动同步 Golden Pair，并 upsert 为 RAG Eval 动态评测 Case；驳回答案自动生成知识缺口任务。
- 建设 RAG Eval 评测体系，内置 30 条 Golden QA，支持 Vector Only、BM25、Hybrid、Hybrid + Rerank 多策略对比，输出 Recall@K、MRR、Hit@K、CitationHit 等指标和 JSON 报告。
- 实现 Agent Trace 可观测能力，为每次问答生成 traceId，记录 QueryPlanner、检索、融合、重排、上下文构造、LLM 生成和 Safety Guard 等 Span，支持从服务台 Trace ID 一键跳转排障。
- 补齐 MCP 权限治理和安全防护，支持 client/tool policy、rate limit、audit log，并在输入、检索内容、工具调用和最终答案阶段拦截 Prompt Injection、密钥泄露和越权调用。
- 完成前后端页面、SQL 初始化脚本、Docker Compose 本地依赖、单元测试和交付文档，支持本地完整演示与后续云端部署。

## 简短版本

**MindCrew 企业知识服务台 Agentic RAG 系统**：基于 Spring Boot、Spring AI、Vue3、Milvus、MySQL 构建企业级知识服务台 Copilot，支持文档清洗质量报告、语义元数据增强、工单接入、QueryPlanner、HyDE、向量/BM25 混合召回、父子/邻接 chunk 召回、RRF 融合、Rerank、SSE 流式问答、人工审核、Golden Pair、RAG Eval 和 Agent Trace。实现采纳答案自动转评测用例、驳回答案自动生成知识缺口、Trace ID 一键追踪 Agent 链路、MCP 工具权限治理和 Prompt Injection 防护，提升系统可评测性、可观测性和持续优化能力。

## 一句话版本

MindCrew 是一个企业知识服务台 Agentic RAG 项目，围绕“工单 -> Agent 检索生成 -> 人工审核 -> Golden Pair -> RAG Eval -> Trace 排障”构建了可运行、可评测、可追踪、可持续优化的 AI 应用闭环。

## 面试可强调关键词

- 企业知识服务台，不是普通文档问答。
- 数据清洗质量报告和语义元数据增强。
- Agentic RAG：QueryPlanner、HyDE、低置信度二次检索。
- 混合检索：Milvus 向量召回 + BM25 + 父子/邻接 chunk + RRF + Rerank。
- 质量闭环：HITL、Golden Pair、RAG Eval、Knowledge Gap。
- 可观测：Trace ID、Span、检索/重排/生成链路定位。
- 安全治理：Prompt Injection 防护、MCP policy、rate limit、audit log。

## 不建议写法

不要只写：

> 做了一个知识库问答系统，支持上传文档和智能问答。

这个说法太普通，无法体现项目真实亮点。建议始终围绕“企业服务台业务闭环 + AI 工程化能力”来写。
