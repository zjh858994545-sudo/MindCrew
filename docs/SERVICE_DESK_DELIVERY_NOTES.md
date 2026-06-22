# MindCrew 服务台闭环增强交付说明

## 本次增强目标

把“服务台确定性草稿生成”升级成真实闭环：

1. 工单生成草稿走 `MindCrewAgent + QueryPlanner + RAG`。
2. 人工采纳后，系统尝试写入 `qa_golden_pair` 并同步 Golden Pair 向量库。
3. Golden Pair 同步成功后，自动写入 `rag_eval_case`，把人工确认答案转成可评测用例。
4. 服务台 Trace ID 可点击跳转到 Agent Trace，直接查看 Agent 执行链路、Span 和安全事件。
5. 如果真实模型 Key 或 Milvus 暂不可用，工单仍然采纳成功，并保留为 `golden_pair_candidate`。
6. 服务台种子知识支持一键重建向量，演示时可以证明知识不是只存在 MySQL 里。

## 新增能力

- `service_ticket.golden_pair_id`：记录采纳答案同步后的 `qa_golden_pair.id`。
- `POST /api/service-desk/knowledge/reindex`：重建 `service_desk` 知识库切片向量。
- 服务台页面新增“重建服务台知识向量”按钮。
- 服务台页面新增“重试 GP”按钮，支持真实 Key 配好后重试失败的 Golden Pair 同步。
- 服务台页面的 Trace ID 可点击跳转到 `Agent Trace`，通过 `traceId` 查询对应链路详情。
- 驳回服务台草稿会自动生成 `service_knowledge_gap` 任务，形成知识缺口池。
- `MilvusService.search()` 已实现真实向量 TopK 检索，主 RAG 的 `VectorRetriever` 也修正为兼容 VarChar 主键。
- Golden Pair 同步成功后会自动 upsert 到 `rag_eval_case`，数据集固定为 `MindCrew Service Desk Golden Pairs`。
- RAG Eval 运行时会合并内置评测 Case 和数据库动态 Case，服务台采纳样本会参与策略对比。
- 服务台页面采纳后会区分两种状态：
  - 同步成功：显示 `Golden Pair #id`。
  - 同步失败：提示已保留为 Golden Pair 候选，等真实模型 Key 配好后再同步。
- 单测覆盖：
  - Agent + RAG 草稿生成。
  - Agent 失败时本地兜底。
  - 采纳成功并同步 Golden Pair。
  - Golden Pair 同步失败时不阻断工单采纳。
  - Golden Pair 候选样本可重试同步。
  - Golden Pair 自动转 RAG Eval Case。
  - 驳回草稿自动创建知识缺口任务。
  - 服务台知识切片生成 embedding 并写入 Milvus。

## 演示步骤

1. 启动 MySQL、Redis、MinIO、Milvus。
2. 执行 `sql/service-desk-loop-schema.sql`，确保服务台工单、知识库、切片、`golden_pair_id` 字段都存在。
3. 启动后端和前端，进入“服务台闭环”页面。
4. 点击右上角“重建服务台知识向量”按钮。
5. 选择安全合规类工单，点击“生成答复”。
6. 展示草稿中的引用来源、置信度、Trace 和事件时间线。
7. 点击 Trace ID，跳转到 Agent Trace 页面，展示同一个 `traceId` 下的执行 Span。
8. 点击“采纳沉淀”：
   - 如果已配置真实模型 Key 且 Milvus 正常，会同步到 Golden Pair。
   - 同步成功后会自动生成 RAG Eval 用例，后续进入“RAG 评测”可参与评测。
   - 如果仍是 dummy key，会保留为候选样本，这正好展示系统的失败兜底能力。
9. 配好真实模型 Key 后，点击“重试 GP”，候选样本会再次尝试写入 Golden Pair。
10. 对低质量草稿点击“驳回补知识”，系统会生成知识缺口任务，并在指标里体现。

## 面试讲法

这个模块不是单纯聊天问答，而是企业知识服务台闭环：

- 工单是业务入口。
- QueryPlanner 负责意图识别、查询改写、HyDE 和 retry policy。
- RAG 负责从服务台制度知识中检索证据。
- 人工采纳/驳回负责质量控制。
- Golden Pair、RAG Eval 和知识缺口负责持续优化。
- 向量重建入口证明数据能从 MySQL 切片进入 Milvus 检索层。
- 知识缺口池证明系统能把失败样本转成知识运营任务。
- Trace 跳转证明系统不是黑盒问答，可以从业务工单追到 Agent 执行链路。

真实模型 Key 未配置时，系统不会假装成功，而是保留候选状态并记录事件。这一点可以主动讲成“工程可靠性设计”：AI 链路失败不影响业务工单闭环。
