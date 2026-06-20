# 踩坑记录

## 1. 密钥不能进入仓库

问题：原始项目存在 `.env`，容易把真实 API Key 提交到 Git。

处理：删除 `.env`，增加 `.env.example`，`.gitignore` 明确忽略环境文件。

## 2. 测试不能依赖真实外部服务

问题：`@SpringBootTest` 启动完整上下文时会依赖真实 LLM Key、数据库等外部配置。

处理：增加 test profile 和 H2 依赖，把基础测试改成轻量级类加载测试。

## 3. Agent 黑盒难排查

问题：只看最终答案很难知道是召回错、重排错还是生成错。

处理：增加 Agent Trace，把关键阶段拆成 span。

## 4. 安全日志可能泄露敏感信息

问题：Trace 如果直接记录输入输出，可能把 token、cookie、password 写进日志。

处理：Trace 和 Safety Event 都做脱敏，覆盖 `api_key`、`authorization`、`cookie`、`password`、`secret`、`Bearer token`。

## 5. Prompt Injection 可能藏在知识库内容里

问题：用户问题正常，但被检索文档包含“忽略之前指令”。

处理：对 retrieved chunk 做净化，再进入 Prompt Assemble。

## 6. 前端严格类型检查

问题：Vue 页面数组首项访问在 `vue-tsc` 下可能报 `possibly undefined`。

处理：增加显式空值判断，保证生产构建通过。
