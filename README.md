# 语音客服智能问答 Demo

本项目是基于 **Java 17 + Maven + Spring Boot 3.3.x** 的语音客服智能问答 Demo，当前重点是验证文本/语音入口进入统一路由链路后，能够按用户意图分发到不同 Agent，并由 Qwen/DashScope 生成回答。

当前主要接口：

- `POST /api/chat`：文本聊天入口，用于验证 QA 快速命中、智能路由、Agent 分发和 LLM 回答链路。
- `POST /api/voice/demo`：语音 Demo 入口，用于验证 ASR -> 文本路由链路。

## 当前进度

| 模块 | 当前状态 | 说明 |
| --- | --- | --- |
| Spring Boot 工程骨架 | 已完成 | 可启动、可测试，当前 `mvn test` 通过。 |
| 文本聊天接口 `/api/chat` | 已完成 | 请求进入 `RouterService` 后，先做 Fast QA，再进入手动 Agent 或智能路由。 |
| 手动选择 Agent | 已完成 | 可通过 `app.chat.agent-mode=manual` 和 `app.chat.manual-agent` 指定目标 Agent。 |
| 多 Agent 注册与分发 | 已完成 | 已有缴费、安全、业务分析、RAG、澄清、fallback 等 Agent。 |
| Agent 调用 LLM | 已完成 | Agent 通过 `LlmAgentResponder` 组装角色 prompt 并调用 Qwen/DashScope。 |
| LLM fallback 日志 | 已完成 | 模型异常、空响应会记录日志，并返回 fallback 文案。 |
| QA 快速命中 Demo | 已完成 | 命中内存 QA 时会绕过 RouterAgent 和 AgentRuntime，直接返回 `QA_AGENT`。 |
| 规则路由 Demo | 已保留 | `app.chat.router-provider=rule` 时启用，用作本地 fallback/对照实现。 |
| Qwen 智能路由 Agent | 已完成第一版 | `app.chat.router-provider=qwen` 时启用，使用 Qwen 输出结构化路由 JSON。 |
| 路由置信度阈值 | 已完成 | `app.chat.route-confidence-threshold` 控制低置信度是否转澄清 Agent，默认 `0.60`。 |
| Qwen 路由日志 | 已完成 | 记录 request、raw response、decision、fallback，便于定位路由模型问题。 |
| 路由 JSON 解析增强 | 已完成 | 支持紧凑 JSON、格式化 JSON、Markdown ```json 代码块；非法输出会 fallback 到澄清。 |
| 语音 ASR Demo | 已完成基础链路 | 默认 mock ASR，可切换腾讯云 ASR 适配。 |
| RAG 知识库 | 占位 | 当前已有接口/Agent，占位能力为主，未接真实知识库。 |

## 当前文本对话链路

```text
POST /api/chat
  -> ChatController.chat
  -> RouterService.route
  -> FastQaService.findAnswer
     -> 命中：直接返回 QA_AGENT
     -> 未命中：继续路由
  -> RouterAgent.classify
     -> RuleBasedRouterAgent 或 QwenRouterAgent
  -> AgentSwitchPolicy.decide
  -> ConversationStateRepository.saveCurrentAgent
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
| 配置属性 | `src/main/java/com/xinchan/voiceqa/api/ChatProperties.java` |
| 路由编排 | `src/main/java/com/xinchan/voiceqa/routing/RouterService.java` |
| 规则路由 | `src/main/java/com/xinchan/voiceqa/routing/RuleBasedRouterAgent.java` |
| Qwen 智能路由 | `src/main/java/com/xinchan/voiceqa/routing/QwenRouterAgent.java` |
| 路由 prompt | `src/main/java/com/xinchan/voiceqa/routing/RouterPromptFactory.java` |
| Agent 切换策略 | `src/main/java/com/xinchan/voiceqa/routing/AgentSwitchPolicy.java` |
| Agent 分发 | `src/main/java/com/xinchan/voiceqa/agent/MockAgentRuntime.java` |
| 具体 Agent | `src/main/java/com/xinchan/voiceqa/agent/*Agent.java` |
| Agent LLM 调用与 fallback | `src/main/java/com/xinchan/voiceqa/agent/LlmAgentResponder.java` |
| Qwen 客户端 | `src/main/java/com/xinchan/voiceqa/ai/QwenChatModelClient.java` |
| DashScope HTTP 调用 | `src/main/java/com/xinchan/voiceqa/ai/QwenRestClientTransport.java` |

## 路由 LLM 与 Agent LLM 调用

当前路由 LLM 和 Agent 回答 LLM **最终使用同一个 Qwen 客户端**：`StreamingChatModelClient -> QwenChatModelClient -> QwenRestClientTransport`。

两者当前请求参数配置基本一致：

- `model`：同一个 `app.ai.model=qwen3.6-flash`
- `baseUrl`：同一个 `app.ai.base-url`
- `apiKey`：同一个 `app.ai.api-key`
- `stream`：均为 `false`
- `messages`：均为 system + user 两条消息
- HTTP transport：同一个 `RestClient`

差异主要在 prompt 和期望输出：

| 调用类型 | 位置 | 目的 | 期望输出 |
| --- | --- | --- | --- |
| 路由 LLM | `QwenRouterAgent` | 判断应该切到哪个 Agent | 结构化 JSON：`targetAgent`、`confidence`、`reason` 等 |
| Agent LLM | `LlmAgentResponder` | 生成直接给用户看的回答 | 自然语言回答 |

注意：`app.ai.timeout-ms=6000` 当前已经在配置中声明，但还没有真正接入 `RestClient` 的 connect/read timeout。也就是说，目前路由 LLM 和 Agent LLM 并不是使用不同超时，而是都没有真正使用该 timeout 配置。后续建议拆分为：

- `app.ai.router-timeout-ms`：路由调用更短，例如 3-6 秒。
- `app.ai.agent-timeout-ms`：回答调用稍长，例如 15-30 秒。

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

默认配置当前使用 Qwen 智能路由：

```powershell
mvn spring-boot:run
```

如果要切换为规则路由：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.chat.agent-mode=router --app.chat.router-provider=rule"
```

如果要手动固定某个 Agent，便于单独验证：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.chat.agent-mode=manual --app.chat.manual-agent=CLARIFICATION_AGENT"
```

如果本机没有 `mvn` 命令，需要在 IDEA 里启动 Spring Boot，或者把 Maven 配置到 PATH。

## 如何请求 `/api/chat`

PowerShell 示例：

```powershell
$body = @{
  conversationId = "c-test"
  userId = "u-1"
  message = "家里燃气泄漏了怎么办"
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

返回结构：

```json
{
  "conversationId": "c-test",
  "targetAgent": "SAFETY_AGENT",
  "answer": "...",
  "source": "SAFETY_AGENT"
}
```

## 智能路由验证结果

本机运行态已做过黑盒验证。

普通单轮样例：

| 用户消息 | 期望/实际目标 |
| --- | --- |
| `家里闻到燃气味，疑似燃气泄漏了怎么办` | `SAFETY_AGENT` |
| `账户余额不足导致停供，想恢复供气应该怎么处理` | `PAYMENT_AGENT` |
| `居民用户办理燃气过户需要准备哪些材料` | `RAG_AGENT` |
| `最近几个小区用气量突然升高，请帮我分析供气调度风险和处置建议` | `BUSINESS_DECISION_AGENT` |
| `帮我看一下这个问题` | `CLARIFICATION_AGENT` |
| `我想查询一下这个月的燃气账单并缴费` | `QA_AGENT`，因为 Fast QA 前置命中 |

同一个 `conversationId` 下的多轮切换样例：

```text
家里燃气泄漏了怎么办
  -> SAFETY_AGENT
账户余额不足导致停供，想恢复供气应该怎么处理
  -> PAYMENT_AGENT
居民用户办理燃气过户需要准备哪些材料
  -> RAG_AGENT
那这个你帮我看一下
  -> CLARIFICATION_AGENT
家里闻到燃气味，疑似燃气泄漏了怎么办
  -> SAFETY_AGENT
```

结论：主链路已经支持同一会话中按不同话题切换 Agent。

## 已修复的问题

| 问题 | 修复情况 |
| --- | --- |
| Qwen 返回格式化 JSON 时解析失败 | 已修复，使用 Jackson 解析 JSON。 |
| Qwen 返回 Markdown ```json 代码块时解析失败 | 已修复，解析前提取第一个 `{` 到最后一个 `}`。 |
| Qwen 路由失败时缺少定位信息 | 已修复，增加 request、raw response、decision、fallback 日志。 |
| 路由置信度硬编码 | 已修复，改为 `app.chat.route-confidence-threshold`。 |
| 手动 Agent 与智能路由不易切换 | 已修复，增加 `app.chat.router-provider=rule/qwen`。 |

## 已知问题与风险

| 问题 | 说明 | 建议 |
| --- | --- | --- |
| Qwen/HTTP/TLS 偶发读取中断 | 已观察到路由模型调用阶段出现网络/读取中断；澄清或 Agent 调用后续可能成功。 | 增加超时配置接入、重试、熔断和更细粒度日志。 |
| `app.ai.timeout-ms` 未真正接入 HTTP 客户端 | 配置存在，但 `RestClient.builder().build()` 当前没有设置 connect/read timeout。 | 接入 `ClientHttpRequestFactory` 并应用 timeout。 |
| 路由和 Agent LLM 共用同一超时配置 | 目前没有区分路由调用和回答调用的 timeout。 | 拆分 `router-timeout-ms` 和 `agent-timeout-ms`。 |
| 安全类高风险问题仍依赖模型判断 | 少数口语化表达可能被 Qwen 判为低置信度澄清。 | 对“燃气味、泄漏、嘶嘶声、阀门”等关键词增加安全兜底规则。 |
| RAG 仍是占位 | 当前可路由到 `RAG_AGENT`，但未接真实知识库。 | 接入本地文档/数据库/向量检索。 |
| PowerShell 直接 `Invoke-RestMethod` 可能显示乱码 | `targetAgent/source` 正常，`answer` 展示可能受终端编码影响。 | 使用 `Invoke-WebRequest` + UTF-8 bytes 解码，或调整终端编码。 |

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
app.chat.router-provider=qwen
app.chat.route-confidence-threshold=0.60
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

当前最新验证结果：

```text
Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

当前测试覆盖重点：

- Spring 上下文启动。
- 路由和手动 Agent 模式。
- 规则路由与 Qwen 智能路由配置切换。
- Qwen 路由 JSON 解析：紧凑 JSON、格式化 JSON、Markdown JSON 代码块、非法输出 fallback。
- Agent 分发。
- LLM responder 成功调用、异常 fallback、日志输出。
- 防止 agent/responder 重新出现本地空参构造路径。

## 项目结构

```text
src/main/java/com/xinchan/voiceqa
  DemoApplication.java                 Spring Boot 启动入口
  api/                                 文本问答 HTTP 接口与配置
  voice/                               语音 Demo HTTP 接口和 ASR 编排
  asr/                                 ASR 接口、Mock、腾讯云 SDK 适配
  ai/                                  Qwen 客户端和 SpringAiGateway
  routing/                             路由服务、规则路由、Qwen 路由、切换策略
  agent/                               AgentRuntime 和各类子 Agent
  qa/                                  QA 快速命中，当前为内存 Demo
  conversation/                        会话状态仓储，当前为内存 Demo
  knowledge/                           知识库接口，当前为 Mock/占位
```

## 待实现功能

| 优先级 | 功能 | 当前状态 | 要做什么 |
| --- | --- | --- | --- |
| P0 | HTTP timeout 接入 | TODO | 将 `app.ai.timeout-ms` 应用到 `RestClient` 的 connect/read timeout。 |
| P0 | 路由/回答 timeout 拆分 | TODO | 增加 `router-timeout-ms` 和 `agent-timeout-ms`，避免两类调用共用同一策略。 |
| P0 | 安全类规则兜底 | TODO | 对燃气泄漏、燃气味、阀门、嘶嘶声等高风险场景强制进入 `SAFETY_AGENT`。 |
| P0 | 完整 fallback 与转人工策略 | 部分完成 | 覆盖低置信度路由、RAG 无结果、ASR 失败、模型超时、用户意图不清等场景。 |
| P1 | RAG 知识库第一版 | TODO | 接入本地 Markdown/JSON 或数据库知识库，完成加载、切分、检索、引用来源返回。 |
| P1 | 会话状态与缓存持久化 | TODO | 用 Redis 或等价存储替换内存会话状态，支持 TTL 和跨实例共享。 |
| P1 | 指标、日志与可观测性 | TODO | 记录 QA、路由、Agent、LLM、RAG、fallback 的耗时、命中情况和错误原因。 |
| P2 | 正式语音入口 | TODO | 替换 JSON + Base64 Demo，支持 multipart 音频上传，后续扩展 WebSocket 实时 ASR。 |
| P2 | 流式响应 | TODO | 让 Qwen/Agent 支持流式输出，接口可考虑 SSE，并记录首 token/首字符时间。 |
| P2 | 管理配置能力 | TODO | 提供 QA、Prompt、Agent 开关、路由阈值、知识库文档、指标查看等管理入口。 |
| P3 | 权限与审计 | TODO | 对管理入口、配置修改、知识库更新、人工转接等操作做权限控制和审计记录。 |
| P3 | TTS 语音回复 | TODO | 在文本回答之后接入 TTS，形成完整语音回复链路。 |

## 下一步建议

1. 先接入 `app.ai.timeout-ms`，解决配置存在但未生效的问题。
2. 拆分路由 LLM 和 Agent LLM 的 timeout/retry 配置。
3. 增加安全类高风险关键词兜底，减少模型低置信度误判。
4. 接入第一版本地 RAG 知识库。
5. 增加请求链路指标，包括 QA、路由、LLM、Agent、fallback 的耗时和错误原因。
