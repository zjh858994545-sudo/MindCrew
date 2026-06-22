# Agent Trace 设计

## 目标

Agent Trace 用来解决 Agentic RAG 的黑盒问题：一个答案为什么这么生成，经过了哪些工具，检索到了什么，在哪一步耗时，哪里失败。

## Span 类型

- `SAFETY_CHECK`：用户输入安全检查。
- `QUERY_ANALYSIS`：查询改写和意图分析。
- `TOOL_CALL`：工具调用与 Golden Pair 命中。
- `VECTOR_RETRIEVAL`：向量召回结果。
- `BM25_RETRIEVAL`：关键词召回结果。
- `RRF_FUSION`：多路结果融合。
- `RERANK`：重排序结果。
- `CONTEXT_BUILD`：Prompt 上下文构造。
- `LLM_GENERATION`：模型生成或缓存回放。

## 数据落库

- `agent_trace`：一次用户请求级 Trace。
- `agent_trace_span`：Trace 下的执行步骤。
- 数据库可用时自动写入；写入失败时使用内存记录，不阻断问答。

## 前端页面

`/agent-trace` 页面展示：

- Trace 列表。
- Trace 状态、耗时、模型、问题和答案摘要。
- Span 时间线。
- Safety Event 列表。

## 安全处理

Trace 中的输入、输出、错误信息会脱敏：

- `api_key`
- `authorization`
- `cookie`
- `password`
- `secret`
- `Bearer token`

## 面试讲法

> Agent 不是一次简单的 LLM 调用，我把每次问答抽象成 trace，把 query rewrite、检索、融合、rerank、prompt build、LLM generation 都记录成 span。这样线上出现回答差、召回慢、引用错时，可以直接定位到具体阶段。
