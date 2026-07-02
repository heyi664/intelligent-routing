# 语音客服智能问答 Demo

本项目是基于 **Java 17 + Maven + Spring Boot 3.3.x** 的语音客服智能问答 Demo。

当前项目重点已经推进到：**先保证文本请求能够路由到指定 agent，并由 agent 调用大模型生成回答；模型异常时记录日志并返回 fallback 回答**。

目前 HTTP 接口主要有两个：

- `POST /api/chat`：文本聊天入口，当前主要用于验证 agent 对话链路。
- `POST /api/voice/demo`：语音 Demo 入口，用于验证 ASR -> 文本路由链路。

## 当前进度

| 模块 | 当前状态 | 说明 |
| --- | --- | --- |
| Spring Boot 工程骨架 | 已完成 | 可启动、可测试，`mvn test` 当前通过。 |
| 文本聊天接口 `/api/chat` | 已完成 | 请求可以进入 `RouterService`，再分发到 agent。 |
| 手动选择 agent | 已完成 | 通过 `app.chat.agent-mode=manual` 和 `app.chat.manual-agent` 可指定聊天 agent。 |
| 子 Agent 注册与分发 | 已完成 | 已有缴费、安全、业务分析、RAG、澄清、fallback 等 agent。 |
| Agent 调用 LLM | 已完成 | agent 通过 `LlmAgentResponder` 组装 prompt 并调用 Qwen/DashScope。 |
| LLM fallback 日志 | 已完成 | 模型异常、空响应会记录具体错误，并给用户返回 fallback 回答。 |
| 本地空参 responder 问题 | 已修复 | 已删除 `LlmAgentResponder` 的空参/localOnly 路径，运行时不再出现 `enabled=false`。 |
| QA 快速命中 Demo | 已完成 | 命中内存 QA 时会绕过 agent/runtime。 |
| 规则路由 Demo | 已完成 | 当前自动路由仍是关键词规则，用于占位验证链路。 |
| Qwen 智能路由 Agent | TODO | 需要后续替换规则路由，输出结构化路由结果。 |
| 语音 ASR Demo | 已完成基础链路 | 默认 mock ASR，可切换腾讯云 ASR 适配。 |
| RAG 知识库 | TODO | 当前只有接口/占位能力，未接真实知识库。 |

## 当前文本对话链路

```text
POST /api/chat
  -> ChatController.chat
  -> RouterService.route
  -> QA 快速命中检查
  -> 手动 agent / 规则路由
  -> MockAgentRuntime.execute
  -> XxxAgent.answer
  -> LlmAgentResponder.answer
  -> SpringAiGateway.streamAsText
  -> QwenChatModelClient.streamAsText
  -> QwenRestClientTransport.complete
  -> DashScope /chat/completions
```

关键代码位置：

| 环节 | 文件 |
| --- | --- |
| HTTP 入口 | `src/main/java/com/xinchan/voiceqa/api/ChatController.java` |
| 路由编排 | `src/main/java/com/xinchan/voiceqa/routing/RouterService.java` |
| agent 分发 | `src/main/java/com/xinchan/voiceqa/agent/MockAgentRuntime.java` |
| 具体 agent | `src/main/java/com/xinchan/voiceqa/agent/*Agent.java` |
| LLM 调用与 fallback | `src/main/java/com/xinchan/voiceqa/agent/LlmAgentResponder.java` |
| Qwen 客户端 | `src/main/java/com/xinchan/voiceqa/ai/QwenChatModelClient.java` |
| DashScope HTTP 调用 | `src/main/java/com/xinchan/voiceqa/ai/QwenRestClientTransport.java` |

## 当前已有 Agent

| Agent | RouteTarget | 作用 |
| --- | --- | --- |
| `PaymentAgent` | `PAYMENT_AGENT` | 缴费、充值、账单、欠费相关问题。 |
| `SafetyAgent` | `SAFETY_AGENT` | 燃气泄漏、阀门、安全应急、抢修相关问题。 |
| `BusinessDecisionAgent` | `BUSINESS_DECISION_AGENT` | 供气负荷、调度、趋势、风险分析。 |
| `RagAgent` | `RAG_AGENT` | 知识库问答占位。 |
| `ClarificationAgent` | `CLARIFICATION_AGENT` | 问题不明确时做澄清。 |
| `FallbackAgent` | `FALLBACK_AGENT` | 无法处理时兜底回复。 |

## 如何启动

默认配置会使用规则路由：

```powershell
mvn spring-boot:run
```

开发阶段建议先用手动 agent 模式，方便验证某一个 agent：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.chat.agent-mode=manual --app.chat.manual-agent=CLARIFICATION_AGENT"
```

如果本机没有 `mvn` 命令，需要在 IDEA 里启动 Spring Boot，或者配置 Maven 到 PATH。

## 如何请求 agent

PowerShell 示例，注意 `message` 必须写在 `@{ ... }` 里面：

```powershell
$body = @{
  conversationId = "c-llm-test"
  userId = "u-1"
  message = "天然气怎么用"
} | ConvertTo-Json

$response = Invoke-WebRequest `
  -Method Post `
  -Uri http://localhost:8080/api/chat `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) `
  -UseBasicParsing

$text = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
$text | ConvertFrom-Json
```

如果配置了手动澄清 agent，IDEA 控制台应该看到类似日志：

```text
LLM agent request targetAgent=CLARIFICATION_AGENT conversationId=c-llm-test
```

如果模型调用失败，会看到 fallback 日志，例如：

```text
LLM agent fallback targetAgent=CLARIFICATION_AGENT conversationId=c-llm-test errorType=... errorMessage=...
```

用户接口仍会返回 fallback 回答，避免直接报错给用户。

## 配置

`src/main/resources/application.properties` 中的核心配置：

```properties
spring.application.name=voice-agent-router-demo
server.port=8080

app.asr.provider=mock
app.asr.secret-id=${TENCENT_ASR_SECRET_ID:}
app.asr.secret-key=${TENCENT_ASR_SECRET_KEY:}
app.asr.region=${TENCENT_ASR_REGION:ap-guangzhou}
app.asr.engine-model-type=${TENCENT_ASR_ENGINE_MODEL_TYPE:16k_zh}
app.asr.voice-format=${TENCENT_ASR_VOICE_FORMAT:wav}
app.asr.sample-rate=${TENCENT_ASR_SAMPLE_RATE:16000}
app.asr.timeout-ms=${TENCENT_ASR_TIMEOUT_MS:5000}

app.ai.provider=qwen
app.ai.model=qwen3.6-flash
app.ai.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
app.ai.api-key=${DASHSCOPE_API_KEY:${QWEN_API_KEY:}}
app.ai.timeout-ms=6000

app.chat.agent-mode=router
app.chat.manual-agent=CLARIFICATION_AGENT
```

### DashScope / Qwen API Key

推荐使用环境变量：

```text
DASHSCOPE_API_KEY=你的 DashScope API Key
```

兼容别名：

```text
QWEN_API_KEY=你的 Qwen API Key
```

Windows 用户级设置示例：

```powershell
setx DASHSCOPE_API_KEY "你的 DashScope API Key"
```

设置用户级环境变量后，需要重启 IDEA、终端或 VS Code 才能读到新值。

## 语音 Demo 接口

`POST /api/voice/demo`

请求体示例：

```json
{
  "voiceSessionId": "v-1",
  "conversationId": "c-voice-1",
  "userId": "u-1",
  "audioBytes": "AQID"
}
```

调用示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/voice/demo `
  -ContentType "application/json; charset=utf-8" `
  -Body '{"voiceSessionId":"v-1","conversationId":"c-voice-1","userId":"u-1","audioBytes":"AQID"}'
```

## 测试

```powershell
mvn test
```

当前测试覆盖重点：

- Spring 上下文启动。
- 路由和手动 agent 模式。
- agent 分发。
- LLM responder 成功调用、异常 fallback、日志输出。
- 防止 agent/responder 重新出现本地空参构造路径。

## 项目结构

```text
src/main/java/com/xinchan/voiceqa
  DemoApplication.java                 Spring Boot 启动入口
  api/                                 文本问答 HTTP 接口
  voice/                               语音 Demo HTTP 接口和 ASR 编排
  asr/                                 ASR 接口、Mock、腾讯云 SDK 适配
  ai/                                  Qwen 客户端和 SpringAiGateway
  routing/                             路由服务、规则路由、跳转策略
  agent/                               AgentRuntime 和各类子 Agent
  qa/                                  QA 快速命中，当前为内存 Demo
  conversation/                        会话状态仓储，当前为内存 Demo
  knowledge/                           知识库接口，当前为 Mock/占位
```

## 待实现功能

| 优先级 | 功能 | 当前状态 | 要做什么 |
| --- | --- | --- | --- |
| P0 | Qwen 智能路由 Agent | TODO | 替换当前关键词规则路由，让 Qwen 输出结构化路由结果，包括 `targetAgent`、置信度、是否需要切换 agent、问题改写和路由原因；保留规则路由作为本地 fallback 或测试实现。 |
| P0 | 真实业务 Agent 能力完善 | 部分完成 | 当前已能分发到多个 agent，但各 agent 仍以 prompt + fallback 为主；后续要补齐缴费、安全、业务分析、RAG、兜底等 agent 的真实业务边界、提示词和必要工具调用。 |
| P0 | 完整 fallback 与转人工策略 | 部分完成 | 当前模型异常会 fallback；后续要覆盖低置信度路由、RAG 无结果、ASR 失败、模型超时、用户意图不清、转人工建议等关键场景。 |
| P1 | RAG 知识库第一版 | TODO | 接入本地 Markdown/JSON 或数据库知识库，完成文档加载、切分、检索、组装 prompt、引用来源返回。 |
| P1 | 会话状态与缓存持久化 | TODO | 用 Redis 或等价存储替换当前内存会话状态，支持 TTL、跨实例共享、短期上下文缓存，并为高频 QA 做缓存。 |
| P1 | 数据库存储与向量索引 | TODO | 接入 PostgreSQL + pgvector，保存业务日志、调用记录、QA 数据、知识库文档和向量索引。 |
| P1 | 指标、日志与可观测性 | TODO | 记录 QA、路由、agent、LLM、RAG、fallback 的耗时、命中情况、错误原因和 conversationId，方便排查线上问题。 |
| P2 | 正式语音入口 | TODO | 替换当前 JSON + Base64 Demo，支持 multipart 音频上传；后续扩展 WebSocket 实时 ASR，返回 partial transcript，并让 stable transcript 进入文本路由链路。 |
| P2 | 流式响应 | TODO | 让 Qwen/agent 支持流式输出，接口可考虑 SSE，记录首 token/首字符时间。 |
| P2 | 管理配置能力 | TODO | 提供 QA、Prompt、Agent 开关、路由阈值、知识库文档、指标查看等管理入口。 |
| P3 | 权限与审计 | TODO | 对管理入口、配置修改、知识库更新、人工转接等操作做权限控制和审计记录。 |
| P3 | TTS 语音回复 | TODO | 在文本回答之后接入 TTS，形成完整语音回复链路。 |

## 下一步

优先开发顺序：

1. 实现 Qwen 智能路由 Agent，替换当前规则路由 Demo。
2. 保留手动 agent 配置，继续作为调试入口。
3. 接入第一版本地 RAG 知识库。
4. 增加请求链路指标，包括 QA、路由、LLM、agent、fallback 耗时和原因。
5. 设计正式语音上传接口，替换当前 JSON + Base64 Demo。
