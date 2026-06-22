# RAG Eval 设计

## 目标

RAG Eval 用来回答一个面试官常问的问题：你的知识库问答效果怎么证明？MindCrew 不只展示“能回答”，还展示“能评测、能对比、能改进”。

## 评测对象

当前内置 30 条 Golden QA，覆盖：

- 召回命中类问题。
- 引用准确性问题。
- 多策略对比问题。
- 安全拒答问题。
- 企业知识库常见业务问题。

## 策略对比

- `VECTOR_ONLY`：只看向量召回效果。
- `BM25_ONLY`：只看关键词召回效果。
- `HYBRID`：向量 + BM25 混合召回。
- `HYBRID_RERANK`：混合召回后再重排序。

## 指标

- `Recall@K`：期望证据是否出现在 TopK。
- `Hit@K`：是否至少命中一个期望证据。
- `MRR`：首个相关证据排名越靠前越好。
- `CitationHit`：答案引用是否能对应证据。
- `RefusalAccuracy`：安全类问题是否正确拒答。
- `AvgLatencyMs`：策略平均耗时。

## 输出

- 前端页面：`/rag-eval`。
- 后端接口：`POST /api/v2/rag-eval/runs`。
- JSON 报告：`target/rag-eval/evaluation_report.json`。

## 面试讲法

可以这样讲：

> 我没有只做一个能聊天的知识库，而是补了一套 RAG Eval。它能在相同 Golden QA 上对比 vector、BM25、hybrid、rerank 四种策略，并输出 Recall@K、MRR、引用命中率和拒答准确率。这样调参不是靠感觉，而是靠指标迭代。

## 后续增强

- 把审核通过的 Golden Pair 自动同步为 Eval Case。
- 增加 LLM-as-Judge 对事实一致性和答案完整性评分。
- 把每次评测结果写入数据库，形成版本趋势图。
