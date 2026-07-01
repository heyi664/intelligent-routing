# 语音客服智能问答 Demo

本项目是基于 **Java 17 + Maven + Spring Boot 3.3.x** 的语音客服智能问答 Demo，业务背景以“渭南天然气客服”为例。

当前代码里存在的 HTTP 接口只有两个：`POST /api/chat` 和 `POST /api/voice/demo`。它们目前主要用于验证项目链路，不等同于生产可用接口。

## 链路说明

### 当前 Demo 链路

```text
用户语音
  ↓
HTTP 请求 /api/voice/demo
  ↓
VoiceController
  ↓
VoicePipelineService
  ↓
ASR 识别
  ↓
stable transcript
  ↓
ChatRequest
  ↓
RouterService
  ↓
QA 快速命中
  ↓ 未命中
规则路由 / 子 Agent Demo
  ↓
ChatResponse
```

### 目标链路

```text
用户语音
  ↓
ASR
  ↓
问题改写
  ↓
QA 快速命中
  ↓
智能路由
  ↓
会话状态
  ↓
子 Agent
  ↓
RAG / 工具调用
  ↓
LLM 回答
  ↓
兜底判断
  ↓
TTS
  ↓
语音回复
```

## 当前已实现模块

| 模块 | 当前状态 | 说明 |
| --- | --- | --- |
| Spring Boot 工程骨架 | 已完成 | 项目可以通过 Maven 启动和测试。 |
| Qwen 客户端配置 | 已完成 | 已配置 `qwen3.6-flash` 和 OpenAI-compatible 调用客户端。 |
| 腾讯云 ASR SDK 适配 | 已完成 | 已接入一句话识别 SDK 调用边界。 |
| QA 快速命中 Demo | 已完成 | 当前是内存 Demo，用于验证 QA 命中绕过后续链路。 |
| 规则路由 Demo | 已完成 | 当前是关键词规则路由，用于验证路由链路。 |
| 子 Agent 跳转 Demo | 已完成 | 当前是 Mock Agent，用于验证会话状态和 Agent 跳转。 |
| 会话状态 Demo | 已完成 | 当前是内存状态，不是 Redis。 |
| 知识库接口 Mock | 已完成 | 当前只是接口和 Mock 实现。 |
| 自动化测试 | 已完成 | 当前 `mvn test` 可验证核心 Demo 链路。 |

## 项目当前存在的接口

### 1. `POST /api/chat`

代码位置：

```text
src/main/java/com/xinchan/voiceqa/api/ChatController.java
```

请求体：

```json
{
  "conversationId": "c-1",
  "userId": "u-1",
  "message": "天然气缴费怎么操作？"
}
```

在项目中的作用：
- 这是当前文本输入链路的唯一 HTTP 入口。
- 用来验证 QA 快速命中、规则路由、会话状态、Agent 跳转和 Mock Agent。
- 适合做开发调试和接口联调。

当前实现程度：
- 可以调用。
- 当前路由是规则 Demo，不是 Qwen 智能路由。
- 当前 Agent 是 Mock，不是真实业务 Agent。
- 当前 QA 是内存 Demo，不是正式 QA 管理系统。

调用示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/chat `
  -ContentType 'application/json' `
  -Body '{"conversationId":"c-1","userId":"u-1","message":"天然气缴费怎么操作？"}'
```

### 2. `POST /api/voice/demo`

代码位置：

```text
src/main/java/com/xinchan/voiceqa/voice/VoiceController.java
```

请求体：

```json
{
  "voiceSessionId": "v-1",
  "conversationId": "c-voice-1",
  "userId": "u-1",
  "audioBytes": "AQID"
}
```

在项目中的作用：
- 这是当前语音链路的 Demo HTTP 入口。
- 用来验证“音频输入 -> ASR -> stable transcript -> 文本路由”的链路结构。
- 默认 `app.asr.provider=mock` 时，使用 Mock ASR 产生转写文本。
- 切到 `app.asr.provider=tencent` 时，会调用腾讯云 ASR SDK 适配层。

当前实现程度：
- 可以调用。
- 这是 Demo 接口，不是正式语音上传接口。
- 当前请求用 JSON + Base64 字节，不支持 multipart 文件上传。
- 默认 Mock ASR 不会识别真实语音内容。
- 腾讯云 ASR 模式需要传真实音频内容的 Base64。

调用示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/voice/demo `
  -ContentType 'application/json' `
  -Body '{"voiceSessionId":"v-1","conversationId":"c-voice-1","userId":"u-1","audioBytes":"AQID"}'
```

## 运行方式

### 测试

```powershell
mvn test
```

### 启动服务

默认 Mock ASR：

```powershell
mvn spring-boot:run
```

启用腾讯云 ASR：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.asr.provider=tencent"
```

服务地址：

```text
http://localhost:8080
```

## 配置

### application.properties

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
```

#### Qwen / DashScope

推荐通过本机环境变量提供密钥，由 `application.properties` 的占位符读取：

```text
DASHSCOPE_API_KEY=你的 DashScope API Key
```

兼容别名：

```text
QWEN_API_KEY=你的 Qwen API Key
```

Windows 用户级设置：

```powershell
setx DASHSCOPE_API_KEY "你的 DashScope API Key"
```

#### 腾讯云 ASR

```text
TENCENT_ASR_SECRET_ID=你的腾讯云 SecretId
TENCENT_ASR_SECRET_KEY=你的腾讯云 SecretKey
```

可选：

```text
TENCENT_ASR_REGION=ap-guangzhou
TENCENT_ASR_ENGINE_MODEL_TYPE=16k_zh
TENCENT_ASR_VOICE_FORMAT=wav
TENCENT_ASR_SAMPLE_RATE=16000
TENCENT_ASR_TIMEOUT_MS=5000
```

不要把真实密钥写入代码或提交仓库。这里的做法是：先配置本机环境变量，再由 `application.properties` 读取并注入到 Spring 配置里。设置用户级环境变量后，需要重启 IDEA、VS Code 或终端。

## 核心代码结构

```text
src/main/java/com/xinchan/voiceqa
  DemoApplication.java                 Spring Boot 启动入口
  api/                                 文本问答 HTTP 接口
  voice/                               语音 Demo HTTP 接口和 ASR 编排
  asr/                                 ASR 接口、Mock、腾讯云 SDK 适配
  ai/                                  Qwen 客户端和 SpringAiGateway
  routing/                             路由服务、规则路由、跳转策略
  agent/                               AgentRuntime，当前为 Mock
  qa/                                  QA 快速命中，当前为内存 Demo
  conversation/                        会话状态仓储，当前为内存 Demo
  knowledge/                           知识库接口，当前为 Mock
```

## 未完全实现功能与开发顺序

| 顺序 | 功能 | 当前状态 | 要做什么 |
| --- | --- | --- | --- |
| 1 | Qwen 智能路由 Agent | 未完全实现 | 接入 Qwen 做结构化路由判断，输出目标 Agent、置信度、问题改写和跳转原因。 |
| 2 | 真实子 Agent 注册表 | 未实现 | 定义统一 Agent 接口，拆出缴费、安全、业务、RAG、兜底等子 Agent，并按路由结果分发。 |
| 3 | 真实语音上传入口 | 未完全实现 | 增加 multipart 音频上传，校验音频格式，调用腾讯云 ASR 后进入现有路由链路。 |
| 4 | RAG 本地知识库第一版 | 未实现 | 先用本地 Markdown/JSON 做知识库，完成切分、检索、组装提示词和引用返回。 |
| 5 | 指标监控第一版 | 未实现 | 记录 QA、ASR、路由、模型、Agent、RAG、兜底等步骤耗时和关键结果。 |
| 6 | Redis 会话状态和 QA 缓存 | 未实现 | 用 Redis 替换内存会话状态，并缓存高频 QA、短期上下文和 TTL。 |
| 7 | 流式响应 / 首字符响应 | 未实现 | 让 Qwen 和 Agent 支持流式输出，接口支持 SSE 或等价流式返回，并统计首字符时间。 |
| 8 | PostgreSQL + pgvector | 未实现 | 保存业务日志、QA 管理数据、调用记录和知识库向量。 |
| 9 | WebSocket 实时 ASR | 未实现 | 接入实时语音流，返回 partial transcript，stable transcript 进入路由。 |
| 10 | 完整兜底策略 | 未完全实现 | 处理低置信度、模型异常、ASR 失败、RAG 无结果等场景，并支持澄清或转人工提示。 |
| 11 | 管理配置能力 | 未实现 | 提供 QA、Prompt、Agent 开关、路由阈值、知识库文档和指标查看的管理入口。 |

## 下一步

优先开发：**Qwen 智能路由 Agent**。
