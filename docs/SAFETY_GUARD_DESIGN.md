# Safety Guard 设计

## 目标

Safety Guard 负责给企业知识库 Agent 加第一层安全边界，重点防 Prompt Injection、系统提示词泄露、密钥泄露和越权工具调用。

## 防护点

1. 用户输入检查：命中高风险规则时直接拒答。
2. 检索内容净化：如果知识库文档中夹带“忽略之前指令”等注入内容，会替换为安全占位文本。
3. 工具调用检查：未授权工具调用会被拦截。
4. 最终答案检查：输出前再次脱敏，避免 token、cookie、password 等泄露。

## 规则类型

- `PROMPT_LEAK`
- `PROMPT_INJECTION`
- `SECRET_LEAK`
- `UNAUTHORIZED_ACCESS`
- `TOOL_ABUSE`

## 审计事件

安全事件写入：

- 内存事件列表，方便本地演示。
- `safety_event_log` 表，方便生产审计。

## 设计原则

- 安全检查不依赖外部模型，保证基础防护稳定。
- 安全事件不阻塞主链路，日志写入失败只降级，不影响用户问答。
- 不把原始密钥写入 Trace 或日志。

## 面试讲法

> 我把安全防护放在输入、检索内容、工具调用和最终输出四个位置。这样不只是防用户恶意输入，也能防知识库文档里的间接 Prompt Injection。所有拦截都会进入 Safety Event，便于后续审计和策略迭代。
