# 语音客服智能问答 Demo

本项目是基于 **Java 17 + Maven + Spring Boot 3.3.x** 的语音客服智能问答 Demo，当前重点是验证文本/语音入口进入统一路由链路后，能够按用户意图分发到不同 Agent，并由 Qwen/DashScope 生成回答。

当前主要接口：

- `POST /api/chat`：文本聊天入口，用于验证 QA 快速命中、智能路由、Agent 分发和 LLM 回答链路。
- `POST /api/voice/demo`：语音 Demo 入口，用于验证 ASR -> 文本路由链路。
- `WS /api/voice/realtime`：WebSocket 实时语音入口，录音过程中持续发送 16k PCM 二进制分片，腾讯实时 ASR 增量返回文本，最终文本进入智能路由链路。

## 2026-07-10 进度更新：腾讯实时 ASR

正式语音入口第一版已打通：

- 浏览器使用 Web Audio API 采集麦克风音频，重采样为 16k、16-bit、单声道 PCM，并在录音过程中通过 WebSocket 二进制帧持续发送。
- 后端收到 `start` 后立即建立腾讯云实时语音识别 WebSocket，会话期间直接转发 PCM 分片，不再等待录音结束后生成完整 WAV。
- 腾讯 ASR 的临时结果和稳定分句通过 `asr_partial` 返回页面；收到云端 `final=1` 后发送 `asr_final`，再进入智能路由和 Agent 流式回答。
- 保留 `mock` 模式及旧 Base64 JSON 音频帧兼容路径，便于本地自动化测试和旧调用方迁移。
- 新增腾讯实时 ASR 参数签名、配置清洗、PCM 转发、partial/final 时序和静态页面测试。

## 2026-07-09 进度更新：指标/日志可观测性与流式响应

本次已完成前两项开发任务的第一版落地：

- 指标与日志可观测性：新增轻量级进程内指标 `ObservabilityMetrics`，并提供 `GET /api/observability/metrics` 查询入口；路由、Agent LLM、语音会话、ASR、语音错误、流式 delta 均会记录基础计数或最近耗时。关键日志统一携带 `traceId`、`conversationId`、`targetAgent`、耗时、fallback/错误原因，便于从一次语音请求串到 ASR、路由和 Agent 回答。
- 流式响应：Qwen HTTP 传输层支持 SSE `stream=true`；`SpringAiGateway`、`LlmAgentResponder`、`ChatAgent`、`AgentRuntime`、`RouterService` 增加流式调用链路；6 个具体 Agent 已接入 `answerStreaming`。WebSocket 语音入口新增 `chat_start`、`chat_delta`、`chat_done` 消息，并保留原 `chat_response` 作为兼容最终结果。
- 浏览器语音测试页：开始请求时生成并传递 `traceId`，页面支持实时追加 `chat_delta`，最终用 `chat_done/chat_response` 收口展示。

验证方式：

```powershell
mvn test
```

流式语音页面验证：启动服务后打开 `http://localhost:8080/voice-test.html`，说一句如“家里闻到燃气味，怀疑燃气泄漏了，我现在应该怎么办？”，页面应先出现 ASR 结果，再逐步追加 Agent 回复，并显示最终 `targetAgent`。

指标查询样例：

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/observability/metrics
```

返回字段示例包括：`route.requests`、`route.streamingRequests`、`llm.agentCalls`、`llm.agentFallbacks`、`voice.sessionsStarted`、`voice.sessionsCompleted`、`asr.calls`、`chat.streamDeltas`、`chat.streamDeltaChars`。
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
| WebSocket 实时语音入口 | 已完成第一版 | `/api/voice/realtime` 支持文本控制帧和二进制 PCM 音频帧；录音过程中持续调用腾讯实时 ASR，最终文本进入 `RouterService` 和 Agent 回复链路。 |
| 会话状态与聊天历史 | 已完成第一版 | 支持一整轮 user + assistant 入库；Agent LLM 回答前会加载同会话最近 N 轮历史，路由 prompt 保持原样。 |
| Redis/PG 持久化 | 已完成第一版 | `app.memory.enabled=true` 后启用；PG 存聊天轮次/会话状态/摘要表，Redis 缓存会话状态并支持密码。数据库本身需先创建。 |
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
     -> ConversationMemoryService.loadForPrompt 加载最近历史给 Agent LLM
  -> SpringAiGateway.streamAsText
  -> QwenChatModelClient.streamAsText
  -> QwenRestClientTransport.complete
  -> DashScope /chat/completions
  -> RouterService.recordTurn
     -> PG chat_turn 保存一整轮 user + assistant
     -> PG/Redis 更新会话状态
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
| Agent prompt 与聊天历史注入 | `src/main/java/com/xinchan/voiceqa/agent/AgentPromptFactory.java` |
| 会话记忆服务 | `src/main/java/com/xinchan/voiceqa/memory/ConversationMemoryService.java` |
| PG 持久化建表与读写 | `src/main/java/com/xinchan/voiceqa/memory/PgMemoryStore.java` |
| Redis 会话状态缓存 | `src/main/java/com/xinchan/voiceqa/memory/RedisStateCache.java` |
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
- `stream`：路由调用为 `false`；流式 Agent 回答为 `true`
- `messages`：均为 system + user 两条消息
- HTTP transport：共用 `QwenRestClientTransport`，但按调用用途创建带不同超时的 `RestClient`

差异主要在 prompt 和期望输出：

| 调用类型 | 位置 | 目的 | 期望输出 |
| --- | --- | --- | --- |
| 路由 LLM | `QwenRouterAgent` | 判断应该切到哪个 Agent | 结构化 JSON：`targetAgent`、`confidence`、`reason` 等 |
| Agent LLM | `LlmAgentResponder` | 生成直接给用户看的回答 | 自然语言回答 |

当前已接入独立的 HTTP 超时与重试策略：

- `app.ai.connect-timeout-ms=3000`：建立 TCP/TLS 连接的最长等待时间。
- `app.ai.router-timeout-ms=6000`：路由模型读取超时，保持较短以避免阻塞整条链路。
- `app.ai.agent-timeout-ms=30000`：Agent 回答读取超时，允许生成较长内容。
- `app.ai.router-max-retries=1`、`app.ai.agent-max-retries=1`：失败后的额外尝试次数。
- `app.ai.retry-backoff-ms=200`：重试基础退避时间，后续尝试按次数递增。

重试只针对连接/读取 I/O 中断、HTTP 429 和 HTTP 5xx；鉴权失败、参数错误、非法响应等确定性错误不会重试。流式回答只有在尚未输出任何 `delta` 时才允许重试，已经输出部分内容后不会重试，以免页面出现重复文字。

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
| Qwen/HTTP/TLS 偶发读取中断 | 已接入 connect/read timeout 和有限重试，可降低临时网络抖动的影响，但不能消除上游服务或本地网络故障。 | 后续可增加熔断、失败率指标和告警。 |
| 流式回答中途断开 | 已输出部分 `delta` 后不会自动重试，避免重复内容；最终仍会进入现有 fallback 收口。 | 后续可增加可恢复流式协议或明确的前端中断提示。 |
| 安全类高风险问题仍依赖模型判断 | 少数口语化表达可能被 Qwen 判为低置信度澄清。 | 对“燃气味、泄漏、嘶嘶声、阀门”等关键词增加安全兜底规则。 |
| RAG 仍是占位 | 当前可路由到 `RAG_AGENT`，但未接真实知识库。 | 接入本地文档/数据库/向量检索。 |
| PowerShell 直接 `Invoke-RestMethod` 可能显示乱码 | `targetAgent/source` 正常，`answer` 展示可能受终端编码影响。 | 使用 `Invoke-WebRequest` + UTF-8 bytes 解码，或调整终端编码。 |

## 配置

`src/main/resources/application.properties` 中的核心配置：

```properties
spring.application.name=voice-agent-router-demo
server.port=8080

app.asr.provider=mock
app.asr.app-id=${TENCENT_ASR_APP_ID:}
app.asr.secret-id=${TENCENT_ASR_SECRET_ID:}
app.asr.secret-key=${TENCENT_ASR_SECRET_KEY:}
app.asr.region=${TENCENT_ASR_REGION:ap-guangzhou}
app.asr.engine-model-type=${TENCENT_ASR_ENGINE_MODEL_TYPE:16k_zh}
app.asr.voice-format=${TENCENT_ASR_VOICE_FORMAT:wav}
app.asr.sample-rate=${TENCENT_ASR_SAMPLE_RATE:16000}
app.asr.timeout-ms=${TENCENT_ASR_TIMEOUT_MS:5000}
app.asr.realtime-timeout-ms=${TENCENT_ASR_REALTIME_TIMEOUT_MS:15000}
app.asr.realtime-vad-silence-time-ms=${TENCENT_ASR_VAD_SILENCE_TIME_MS:1000}

app.ai.provider=qwen
app.ai.model=qwen3.6-flash
app.ai.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
app.ai.api-key=${DASHSCOPE_API_KEY:${QWEN_API_KEY:}}
app.ai.timeout-ms=6000
app.ai.connect-timeout-ms=3000
app.ai.router-timeout-ms=6000
app.ai.agent-timeout-ms=30000
app.ai.router-max-retries=1
app.ai.agent-max-retries=1
app.ai.retry-backoff-ms=200

app.chat.agent-mode=router
app.chat.manual-agent=CLARIFICATION_AGENT
app.chat.router-provider=qwen
app.chat.route-confidence-threshold=0.60

# Conversation memory. 默认关闭；需要 Redis/PG 时启动参数打开 app.memory.enabled=true
app.memory.enabled=false
app.memory.recent-turn-limit=8
app.memory.redis-host=192.168.23.129
app.memory.redis-port=6379
app.memory.redis-password=${REDIS_PASSWORD:123321}
app.memory.jdbc-url=jdbc:postgresql://192.168.23.129:5432/intelligent-routing?sslmode=disable&connectTimeout=3&socketTimeout=5
app.memory.jdbc-username=${PGVECTOR_USERNAME:postgres}
app.memory.jdbc-password=${PGVECTOR_PASSWORD:postgres}
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

## 浏览器语音测试页面

项目已提供一个最小语音测试页：

```text
http://localhost:8080/voice-test.html
```

使用方式：

1. 启动后端服务。
2. 用 Chrome 或 Edge 打开测试页。
3. 填写或保留默认 `conversationId`、`userId`、`voiceSessionId`。
4. 点击“开始说话”，允许浏览器使用麦克风。
5. 说完后点击“结束说话”。
6. 说话过程中页面会持续展示 ASR 临时文本，结束后展示最终文本、`targetAgent` 和 Agent 回复。

页面通过浏览器 Web Audio API 采集 PCM，在录音回调中持续重采样为 16k 单声道并编码为 PCM16 小端字节，然后通过 `WS /api/voice/realtime` 二进制帧立即发送。`start` 帧携带 `audioFormat=pcm` 和 `sampleRate=16000`。当前 `mock` 模式会在结束时返回固定文本；切换腾讯实时识别需要设置 `app.asr.provider=tencent`，并配置 `TENCENT_ASR_APP_ID`、`TENCENT_ASR_SECRET_ID`、`TENCENT_ASR_SECRET_KEY`。
## WebSocket 实时语音入口

当前已完成第一版“边说边发送、边说边识别”的 WebSocket 入口，端点为：

```text
WS /api/voice/realtime
```

开始和结束使用文本 JSON 控制帧：

```json
{"type":"start","voiceSessionId":"v-1","conversationId":"c-voice-1","userId":"u-1","audioFormat":"pcm","sampleRate":16000}
```

开始成功后，客户端持续发送 WebSocket 二进制帧。每个二进制帧是 16k、16-bit、单声道、PCM 小端原始数据，不包含 WAV 文件头，也不需要 Base64 编码。

```json
{"type":"end"}
```

服务端收到 `start` 后创建本地会话并连接腾讯实时 ASR；收到二进制音频帧后立即转发给腾讯。腾讯返回临时或稳定分句时，服务端发送 `asr_partial`。客户端发送 `end` 后，服务端通知腾讯结束音频流；腾讯返回 `final=1` 后，服务端发送 `asr_final`，再复用 `RouterService` 进入智能路由、Agent 回答和聊天历史记录链路。

旧版 `{"type":"audio","audioBase64":"..."}` 文本帧仍可兼容，但浏览器页面和正式链路默认使用二进制 PCM 帧。

服务端返回的主要消息类型：

| type | 说明 |
| --- | --- |
| `started` | 本地会话和腾讯实时 ASR 连接已创建。 |
| `asr_partial` | ASR 增量文本，包含 `transcript`、`stable`、`index`、起止时间。 |
| `asr_final` | ASR 最终稳定文本，包含 `transcript`。 |
| `chat_start` | 最终文本已进入路由，开始生成 Agent 回答。 |
| `chat_delta` | Agent 回答的增量文本。 |
| `chat_done` | Agent 流式回答结束，包含最终 `targetAgent`。 |
| `chat_response` | 兼容用最终回复，包含 `conversationId`、`targetAgent`、`answer`、`source`。 |
| `error` | 参数缺失、未 start、音频为空、ASR 无稳定文本或处理异常。 |

腾讯实时 ASR 的 AppID 可在腾讯云控制台账号信息中查看。AppID 与 SecretId 不是同一个值，缺少 `TENCENT_ASR_APP_ID` 时，`tencent` 模式会在启动阶段直接给出配置错误。
## 测试

```powershell
mvn test
```

当前最新验证结果：

```text
Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

当前测试覆盖重点：

- Spring 上下文启动。
- 路由和手动 Agent 模式。
- 规则路由与 Qwen 智能路由配置切换。
- Qwen 路由 JSON 解析：紧凑 JSON、格式化 JSON、Markdown JSON 代码块、非法输出 fallback。
- 腾讯实时 ASR：WebSocket 签名参数、凭证清洗、AppID 校验、二进制 PCM 转发、增量/最终文本时序。
- 浏览器实时语音页：等待服务端 `started` 后再采集麦克风，持续发送 PCM，处理 `asr_partial` 和 Agent 流式消息。
- Agent 分发。
- LLM responder 成功调用、异常 fallback、日志输出。
- 防止 agent/responder 重新出现本地空参构造路径。
- 会话历史按一整轮 user + assistant 保存。
- Agent LLM prompt 会携带最近会话历史，路由 LLM prompt 保持原样。

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
  conversation/                        会话状态仓储，支持内存与 PG/Redis 持久化
  memory/                              聊天历史、会话摘要、PG 存储与 Redis 缓存
  knowledge/                           知识库接口，当前为 Mock/占位
```

## 待实现功能

| 优先级 | 功能 | 当前状态 | 要做什么 |
| --- | --- | --- | --- |
| P0 | HTTP timeout 接入 | 已完成第一版 | `connect-timeout-ms` 已应用到连接阶段，`router-timeout-ms`/`agent-timeout-ms` 已应用到读取阶段。 |
| P0 | 路由/回答 timeout 与重试拆分 | 已完成第一版 | 路由和 Agent 使用独立读取超时与重试次数；仅重试 I/O 中断、HTTP 429/5xx，流式已出字后不重试。 |
| P0 | 安全类规则兜底 | TODO | 对燃气泄漏、燃气味、阀门、嘶嘶声等高风险场景强制进入 `SAFETY_AGENT`。 |
| P0 | 完整 fallback 与转人工策略 | 部分完成 | 覆盖低置信度路由、RAG 无结果、ASR 失败、模型超时、用户意图不清等场景。 |
| P1 | RAG 知识库第一版 | TODO | 接入本地 Markdown/JSON 或数据库知识库，完成加载、切分、检索、引用来源返回。 |
| P1 | 会话状态与缓存持久化 | 已完成第一版 | PG 记录聊天轮次和会话状态，Redis 缓存当前会话状态并支持 TTL/密码；后续补 LLM 摘要生成与更完整的运维配置。 |
| P1 | 指标、日志与可观测性 | 已完成第一版 | 已提供 `GET /api/observability/metrics`，并记录路由、Agent LLM、语音会话、ASR、错误、流式 delta 的基础计数和最近耗时；后续可接入 Micrometer/Actuator、Prometheus、链路追踪和更完整的耗时分位统计。 |
| P2 | 正式语音入口 | 已完成第一版 | WebSocket 使用二进制 PCM 分片对接腾讯实时 ASR，支持增量转写和最终文本路由；后续可将 `ScriptProcessorNode` 升级为 AudioWorklet，并补断线重连、背压和会话时长限制。 |
| P2 | 流式响应 | 已完成第一版 | ASR 支持 `asr_partial/asr_final`，Qwen HTTP 传输层支持 SSE `stream=true`，WebSocket 语音入口返回 `chat_start`、`chat_delta`、`chat_done`。后续可补首 token 耗时和 HTTP SSE 文本接口。 |
| P2 | 管理配置能力 | TODO | 提供 QA、Prompt、Agent 开关、路由阈值、知识库文档、指标查看等管理入口。 |
| P3 | 权限与审计 | TODO | 对管理入口、配置修改、知识库更新、人工转接等操作做权限控制和审计记录。 |
| P3 | TTS 语音回复 | TODO | 在文本回答之后接入 TTS，形成完整语音回复链路。 |

## 下一步建议

1. 增加安全类高风险关键词兜底，减少模型低置信度误判。
2. 完成超过轮数后的 LLM 会话摘要生成，并写入 `conversation_summary`。
3. 接入第一版本地 RAG 知识库。
4. 增加熔断、失败率指标与告警，进一步处理持续性上游故障。
5. 将轻量指标升级为正式可观测性方案：接入 Micrometer/Actuator、Prometheus/Grafana，并补充首 token 耗时、分位耗时和 traceId 链路查询。
