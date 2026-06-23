# MindCrew 截图替换指南

README 当前使用 `docs/assets/screenshots/*.svg` 作为 GitHub 展示图。后续如果要换成真实浏览器截图，按下面步骤拍摄即可。

## 推荐截图

### 1. 服务台闭环

路径：`/service-desk`

截图内容：

- 左侧工单队列。
- 中间业务问题。
- 右侧或下方 AI 答复草稿。
- Trace ID、置信度、Golden Pair 状态。

建议文件名：

```text
docs/assets/screenshots/service-desk.png
```

### 2. RAG 评测

路径：`/rag-eval`

截图内容：

- 策略对比结果。
- Recall@K、MRR、Hit@K、CitationHit。
- 动态 Case 数量。

建议文件名：

```text
docs/assets/screenshots/rag-eval.png
```

### 3. Agent Trace

路径：`/agent-trace?traceId=你的traceId`

截图内容：

- Trace 列表。
- Span 时间线。
- QueryPlanner、Retrieval、Rerank、LLM Generation。

建议文件名：

```text
docs/assets/screenshots/agent-trace.png
```

### 4. MCP 治理

路径：`/mcp`

截图内容：

- 工具策略。
- 限流。
- 审计日志。

建议文件名：

```text
docs/assets/screenshots/mcp-governance.png
```

## 拍摄建议

- 浏览器宽度建议 1440px 或 1600px。
- 隐藏书签栏，避免截图看起来杂。
- 使用演示数据，不展示真实 API Key、密码、个人隐私。
- 优先截系统核心功能，不截登录页。
- 图片宽度控制在 1200px 到 1600px，文件不要太大。

## 替换 README 图片

把 README 中的：

```markdown
![服务台闭环](docs/assets/screenshots/service-desk.svg)
```

替换为：

```markdown
![服务台闭环](docs/assets/screenshots/service-desk.png)
```

其他截图同理。
