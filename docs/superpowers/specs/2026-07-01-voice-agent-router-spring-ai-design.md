# 语音底座-智能决策知识问答整体计划书

## 1. 项目目标

以渭南天然气业务场景为例，建设一个 Java 智能问答底座，支持用户通过语音或文本输入进行智能决策知识问答。

本阶段实现目标：

1. 智能路由：用户输入经过问题改写、意图判断和上下文判断后，动态分发到对应子 Agent。
2. 子 Agent 跳转：当前会话处于某个子 Agent 时，如果新问题与当前子 Agent 无关，则回弹主路由或直接跳转到目标子 Agent。
3. 会话状态管理：管理上下文、当前 Agent、工具调用结果、RAG 结果、路由历史和当前会话状态。
4. 语音输入处理：支持客服语音输入经过 ASR 转文字，再进入统一文本路由、Agent 分析和回答链路。
5. 指标监控：记录用户输入全流程每一步执行时间、调用结果、首字符响应时间和异常信息。
6. 知识库接口预留：第一阶段使用 Mock 或 HTTP 适配，后续可接入向量库、企业知识库或业务系统。

## 2. 技术选型

推荐技术栈：

- Java 17
- Spring Boot 3.x
- Spring WebFlux
- Spring AI
- Redis
- Micrometer + Spring Boot Actuator
- OpenTelemetry，可选
- SSE 流式响应
- WebSocket 或 HTTP 音频上传，用于语音流接入
- ASR 语音识别服务适配，第一阶段可用 Mock 或第三方 HTTP API
- TTS 语音合成服务适配，可选预留
- JUnit 5 + Reactor Test

Spring AI 在本项目中的定位是 AI 能力适配层，而不是业务编排核心。

使用 Spring AI 承担：

- 大模型 ChatClient 调用
- 流式 token 输出
- Embedding 调用
- RAG 基础能力
- Vector Store 适配
- Tool Calling 适配
- 模型供应商切换
- AI 调用观测能力接入

不交给 Spring AI 承担：

- 主路由 Agent 的业务规则
- 子 Agent 跳转状态机
- 会话状态结构
- QA 快速命中链路
- 首字符性能兜底策略
- 全链路指标分段定义
- 企业知识库统一接口边界

## 2.1 软件环境与分阶段配置

项目依赖建议分阶段配置，不建议第一天就同时引入数据库、缓存、向量库、ASR 和 TTS。优先跑通主链路，再逐步替换真实基础设施。

### 第一阶段：最小可运行环境

必须配置：

- JDK 17
- Maven 或 Gradle
- Spring Boot 3.x
- Spring AI
- 一个大模型 API Key，例如 OpenAI、通义千问、智谱、DeepSeek 兼容接口等
- Mock ASR 或第三方 HTTP ASR

第一阶段可以先不安装：

- Redis
- MySQL 或 PostgreSQL
- 向量库
- TTS 服务
- Prometheus / Grafana

第一阶段使用内存会话状态、Mock QA、Mock ASR 和 Mock KnowledgeBaseClient，目标是先验证语音转写、路由分发、Agent 跳转、RAG 调用和 SSE 流式响应主链路。

### 第二阶段：课题验收推荐环境

建议配置：

- Redis：保存热会话状态、当前 Agent、短期上下文、QA 快速命中缓存。
- PostgreSQL：保存会话审计、路由历史、Prompt 版本、QA 标准问答配置和工具调用记录。
- pgvector：作为轻量向量检索能力，与 PostgreSQL 共用同一数据库。
- 第三方 ASR：替换 Mock ASR，用于真实客服语音转文字。
- Mock TTS 或真实 TTS：按演示需要开启。

推荐组合：

```text
JDK 17
Maven
Spring Boot 3
Spring AI
Redis
PostgreSQL + pgvector
第三方 ASR
Mock 或真实 TTS
Actuator + Micrometer
```

选择 PostgreSQL + pgvector 的原因：部署简单，既能保存业务数据和审计数据，也能承担第一阶段知识库向量检索，避免额外维护 Milvus、Elasticsearch 或 OpenSearch 集群。

### 第三阶段：生产演示或扩展环境

可扩展配置：

- Milvus、Qdrant、Elasticsearch 或 OpenSearch：用于更大规模知识库检索。
- 企业知识库 HTTP API：对接已有文档库、工单系统、客服知识库或业务系统。
- Prometheus + Grafana：展示路由耗时、ASR 耗时、RAG 耗时、LLM 首 token 耗时和兜底率。
- 日志平台：保存 traceId、conversationId、voiceSessionId 和 Agent 跳转链路。
- 真实 TTS：生成语音客服回复。

### 当前本机配置检查

当前开发机已具备：

- Java Runtime：OpenJDK 17.0.5
- Java Compiler：javac 17.0.5
- 当前 Demo：可直接用 `javac` 编译运行

当前开发机暂未检测到：

- Maven
- Gradle
- Redis
- PostgreSQL / pgvector
- 真实 ASR 服务配置
- 大模型 API Key

因此当前阶段继续使用 Java 17 + 纯 JDK Demo 开发。进入 Spring Boot + Spring AI 阶段前，建议至少补充 Maven；Redis、PostgreSQL、pgvector、真实 ASR 和 TTS 仍按第二阶段逐步接入。
### 配置优先级

优先级建议如下：

1. 先配置 JDK 17、Maven、模型 API Key，跑通文本和 Mock 语音主链路。
2. 再接 Redis，解决会话状态和 QA 快速命中性能。
3. 再接 PostgreSQL + pgvector，解决长期记录和知识库检索。
4. 再替换真实 ASR 和 TTS，完成语音客服体验。
5. 最后接 Prometheus/Grafana 和日志平台，完善监控大盘。

## 3. 总体架构

```text
Client / Voice Gateway
        |
        +-----------------------------+
        |                             |
        v                             v
Text Chat API                  Voice Stream API
        |                             |
        |                             v
        |                    AsrGateway / AsrClient
        |                             |
        +-------------< Stable Transcript
        |
        v
SSE Chat API
        |
        v
ConversationStateService
        |
        v
RouterService
        |
        +---------------------+
        |                     |
        v                     v
   Fast QA Path          AgentRuntime
        |                     |
        v                     v
 Redis / Local Cache   QaAgent / RagAgent / BusinessDecisionAgent / FallbackAgent
                              |
                              v
                      SpringAiGateway
                              |
              +---------------+---------------+
              |               |               |
              v               v               v
          ChatClient     EmbeddingModel    Tool Calling
                              |
                              v
                    KnowledgeBaseClient
                              |
              +---------------+---------------+
              |               |               |
              v               v               v
          Mock KB        HTTP KB API     Vector Store
```

核心原则：

- 业务编排层自己控制路由、状态和监控。
- Spring AI 只作为模型、RAG、Embedding 和工具调用的适配层。
- QA 命中路径绕过 LLM，确保首字符响应小于等于 200ms。
- 所有对外回答默认使用 SSE 流式输出，以首字符响应时间作为关键指标。
- 语音输入先经过 ASR 形成稳定文本，后续复用同一套路由、Agent、RAG 和监控链路。
- TTS 只作为回答输出适配层，不能影响文本路由和 Agent 编排。

## 4. 模块划分

建议 Java 包结构：

```text
src/main/java/com/xinchan/voiceqa
  api/
    ChatController.java
    VoiceController.java
    ChatRequest.java
    VoiceChatRequest.java
    ChatStreamEvent.java

  voice/
    VoiceSession.java
    VoiceInputEvent.java
    VoicePipelineService.java

  asr/
    AsrClient.java
    AsrResult.java
    MockAsrClient.java
    HttpAsrClient.java

  tts/
    TtsClient.java
    TtsRequest.java
    TtsResult.java
    MockTtsClient.java

  conversation/
    ConversationState.java
    ConversationStateService.java
    RedisConversationStateRepository.java

  routing/
    RouterService.java
    RouteDecision.java
    RouteTarget.java
    IntentClassifier.java
    QuestionRewriter.java
    AgentSwitchPolicy.java

  agent/
    Agent.java
    AgentRequest.java
    AgentResponseEvent.java
    AgentRuntime.java
    QaAgent.java
    RagAgent.java
    BusinessDecisionAgent.java
    ClarificationAgent.java
    FallbackAgent.java

  ai/
    SpringAiGateway.java
    ChatModelClient.java
    StreamingChatModelClient.java
    SpringAiPromptFactory.java

  knowledge/
    KnowledgeBaseClient.java
    KnowledgeQuery.java
    KnowledgeChunk.java
    MockKnowledgeBaseClient.java
    HttpKnowledgeBaseClient.java

  monitoring/
    TraceContext.java
    TraceStage.java
    MetricsRecorder.java
    ChatTraceLogger.java

  config/
    AgentPromptProperties.java
    RouterProperties.java
    SpringAiProperties.java
```

## 4.1 语音客服链路设计

语音客服入口需要先完成语音到文本的转换，再进入统一的文本智能路由链路。核心原则是：ASR 只负责转写，RouterService 只处理文本语义，Agent 只负责业务分析和回答。

推荐链路：

```text
用户语音
 -> VoiceController 接收音频流或音频文件
 -> VoicePipelineService 创建 voiceSessionId 和 traceId
 -> AsrClient 执行语音识别
 -> 获取 partial transcript，用于前端实时展示
 -> 获取 stable transcript，用于正式路由分析
 -> 构造 ChatRequest
 -> RouterService 执行 QA / 路由 / Agent / RAG
 -> SSE 返回文本答案
 -> 可选调用 TtsClient 合成语音回复
```

ASR 结果建议区分两类：

- `partialTranscript`：临时转写结果，用于客服界面实时展示，不进入正式路由。
- `stableTranscript`：稳定转写结果，用于问题改写、意图判断和 Agent 调用。

ASR 接口建议定义为：

```java
public interface AsrClient {
    Flux<AsrResult> transcribe(VoiceInputEvent event);
}
```

```java
public record AsrResult(
    String voiceSessionId,
    String transcript,
    boolean stable,
    double confidence,
    long offsetMs
) {
}
```

VoicePipelineService 负责把稳定转写结果转成文本请求：

```java
public class VoicePipelineService {

    public Flux<ChatStreamEvent> handleVoice(VoiceChatRequest request) {
        return asrClient.transcribe(request.toVoiceInputEvent())
            .filter(AsrResult::stable)
            .next()
            .flatMapMany(asr -> routerService.route(ChatRequest.fromAsr(request, asr)));
    }
}
```

TTS 作为输出适配层预留：

```java
public interface TtsClient {
    Mono<TtsResult> synthesize(TtsRequest request);
}
```

第一阶段可以只返回文本答案和事件流；如果需要完整语音客服体验，再开启 TTS，将最终文本答案或分段答案合成为语音。

语音链路需要处理的异常：

- 音频格式不支持：直接返回明确错误，不进入 RouterService。
- ASR 置信度过低：进入 ClarificationAgent，请用户复述或补充。
- ASR 超时：进入 FallbackAgent 或返回“正在为您转人工/稍后重试”。
- 用户长时间静音：结束本轮语音输入，不触发业务 Agent。
- ASR partial 反复变化：只展示，不落入会话上下文。
## 5. 智能路由设计

每次用户输入执行以下流程：

```text
接收用户输入
 -> 创建 traceId
 -> 读取 ConversationState
 -> QA 快速匹配
 -> 问题改写
 -> 意图判断
 -> 判断是否延续当前 Agent
 -> 生成 RouteDecision
 -> 执行目标 Agent
 -> 流式返回
 -> 保存上下文、工具结果和路由历史
 -> 写入指标
```

路由决策对象：

```java
public record RouteDecision(
    RouteTarget target,
    boolean stayInCurrentAgent,
    boolean bounceToMainRouter,
    double confidence,
    String rewrittenQuestion,
    String reason
) {
}
```

路由策略：

- 如果 QA 缓存命中，直接进入 Fast QA Path，不调用 Spring AI。
- 如果当前 Agent 能处理新问题，并且意图连续，保持当前 Agent。
- 如果当前 Agent 不能处理，但可以识别目标 Agent，直接跳转目标 Agent。
- 如果当前 Agent 判断不清，回弹 RouterService 重新决策。
- 如果 RouterService 仍无法判断，进入 ClarificationAgent。
- 如果模型、知识库或工具调用失败，进入 FallbackAgent。

### 5.1 编排 Agent 职责边界

调用哪个子 Agent 由编排路由层决定，但不建议让大模型单独决定最终执行结果。推荐设计为：

```text
RouterService
   |
   +--> FastQaService：优先判断是否命中标准 QA
   |
   +--> RouterAgent：使用规则和 Spring AI 辅助生成候选路由
   |
   +--> AgentSwitchPolicy：结合当前会话状态做最终跳转决策
   |
   v
AgentRuntime：执行最终目标 Agent
```

其中：

- `RouterService` 是入口编排器，负责串联 QA 快速命中、路由识别、状态判断、Agent 执行和指标记录。
- `RouterAgent` 是路由识别 Agent，负责问题改写、意图识别、候选目标 Agent 判断和置信度输出。
- `AgentSwitchPolicy` 是确定性策略层，负责根据当前会话状态、候选 Agent、置信度和跳转规则生成最终 `RouteDecision`。
- `AgentRuntime` 只负责根据最终 `RouteDecision.target` 调用对应子 Agent，不再重新判断路由。

RouterAgent 输出的是候选路由，不直接执行子 Agent：

```java
public record RouteCandidate(
    String rewrittenQuestion,
    String intent,
    RouteTarget targetAgent,
    boolean shouldStayInCurrentAgent,
    double confidence,
    String reason
) {
}
```

AgentSwitchPolicy 再生成最终决策：

```java
public class AgentSwitchPolicy {

    public RouteDecision decide(
        ConversationState state,
        RouteCandidate candidate
    ) {
        if (candidate.confidence() < 0.6) {
            return RouteDecision.to(RouteTarget.CLARIFICATION_AGENT);
        }

        if (candidate.shouldStayInCurrentAgent()) {
            return RouteDecision.to(state.currentAgent());
        }

        if (candidate.targetAgent() != state.currentAgent()) {
            return RouteDecision.to(candidate.targetAgent());
        }

        return RouteDecision.to(state.currentAgent());
    }
}
```

这样拆分后，路由链路具备三个优势：

- 可控：大模型只给候选结果，最终执行由代码策略决定。
- 可测：可以单独测试 RouterAgent 的识别结果，也可以单独测试 AgentSwitchPolicy 的跳转逻辑。
- 可调：提示词调整不会直接破坏会话状态机，低置信度和异常情况都能回到确定性策略。

## 6. 会话状态管理

ConversationState 建议包含：

```java
public record ConversationState(
    String conversationId,
    String userId,
    RouteTarget currentAgent,
    String lastIntent,
    List<ConversationMessage> messages,
    List<ToolCallResult> toolResults,
    List<KnowledgeChunk> lastRagChunks,
    List<RouteHistory> routeHistories,
    Instant updatedAt
) {
}
```

状态管理策略：

- Redis 保存热会话状态。
- 上下文只保留最近 N 轮消息，避免 prompt 膨胀。
- 工具调用结果结构化保存，进入 prompt 前由 PromptFactory 摘要化。
- RAG 结果保存知识片段 ID、来源、摘要和分数。
- 每次 Agent 切换记录 fromAgent、toAgent、reason、confidence。

## 7. Spring AI 接入设计

Spring AI 通过 SpringAiGateway 统一封装，业务层不直接依赖 Spring AI 的具体 API。

```java
public interface StreamingChatModelClient {
    Flux<String> stream(ChatModelRequest request);
}
```

```java
public class SpringAiGateway implements StreamingChatModelClient {
    private final ChatClient chatClient;

    public Flux<String> stream(ChatModelRequest request) {
        return chatClient
            .prompt()
            .system(request.systemPrompt())
            .user(request.userPrompt())
            .stream()
            .content();
    }
}
```

设计要求：

- 所有 Agent 只能依赖 `StreamingChatModelClient`，不能直接依赖 `ChatClient`。
- Prompt 构造集中放在 `SpringAiPromptFactory`。
- Tool Calling 通过独立工具注册层接入，避免业务 Agent 混入工具细节。
- RAG 检索接口优先依赖 `KnowledgeBaseClient`，Spring AI Vector Store 只是其中一种实现。

## 8. 知识库接口预留

统一知识库接口：

```java
public interface KnowledgeBaseClient {
    Mono<List<KnowledgeChunk>> retrieve(KnowledgeQuery query);
}
```

第一阶段实现：

- `MockKnowledgeBaseClient`：返回固定渭南天然气测试知识。
- `HttpKnowledgeBaseClient`：预留企业知识库 HTTP API。

第二阶段可扩展：

- Spring AI Vector Store 实现
- Elasticsearch/OpenSearch 实现
- pgvector 实现
- Milvus 实现
- 企业文档库或业务系统 API 实现

KnowledgeChunk 建议字段：

```java
public record KnowledgeChunk(
    String id,
    String source,
    String title,
    String content,
    double score
) {
}
```

## 9. 性能指标设计

目标指标：

| 场景 | 首字符响应目标 | 策略 |
|---|---:|---|
| 命中 QA | <= 200ms | Redis 或本地缓存直接返回，绕过 LLM |
| 单次 RAG | <= 900ms | 快速检索 + SSE 首包 + Spring AI 流式生成 |
| 路由分发 + 子 Agent RAG | <= 1400ms | 轻量路由 + RAG 并行预取 + 流式响应 |
| 最终兜底回复 | <= 6000ms | 分阶段超时控制，超时进入 FallbackAgent |
| 语音首个转写片段 | <= 1000ms | ASR partial transcript 流式返回，仅展示不路由 |
| 语音稳定转写后路由 | 复用文本指标 | stable transcript 生成后构造 ChatRequest |

关键实现：

- ChatController 一接收请求即创建 SSE 流。
- QA 命中时直接发送 answer 事件。
- RAG 场景先发送 `agent_started` 或 `retrieving` 事件，再流式发送模型内容。
- 路由模型调用设置超时。
- 知识库检索设置超时。
- LLM 首 token 设置超时。
- 超时或异常进入 FallbackAgent。
- 语音输入场景单独记录 ASR 首片段耗时、稳定转写耗时和 ASR 置信度。

## 10. 指标监控

每次请求生成 traceId，记录以下阶段：

```text
request_received
voice_received
asr_partial_received
asr_stable_received
context_loaded
qa_checked
question_rewritten
intent_classified
route_decided
agent_started
kb_retrieved
llm_first_token
response_completed
state_persisted
fallback_triggered
```

指标字段：

- traceId
- conversationId
- userId
- currentAgent
- targetAgent
- routeConfidence
- qaHit
- ragHit
- fallbackUsed
- firstTokenLatencyMs
- totalLatencyMs
- routeLatencyMs
- ragLatencyMs
- llmFirstTokenLatencyMs
- asrFirstPartialLatencyMs
- asrStableLatencyMs
- asrConfidence
- voiceSessionId
- errorCode
- errorMessage

落地方式：

- Micrometer Timer 记录阶段耗时。
- Actuator 暴露 `/actuator/metrics`。
- JSON 日志输出完整 trace。
- 后续可接 Prometheus + Grafana。

## 11. 提示词与路由测试

提示词分层：

- Router Prompt：只负责意图、目标 Agent、是否保持当前 Agent。
- Agent Prompt：只负责当前 Agent 的业务回答。
- Rewrite Prompt：只负责问题改写，不参与回答。
- Fallback Prompt：只负责异常兜底和澄清。

路由测试集：

1. 缴费类问题应进入 QaAgent。
2. 供气风险分析应进入 BusinessDecisionAgent 或 RagAgent。
3. 当前在缴费 Agent 时，用户问泄漏处理，应跳转安全相关 Agent。
4. 当前在安全 Agent 时，用户继续追问阀门关闭顺序，应保持当前 Agent。
5. 输入不完整时，应进入 ClarificationAgent。
6. 知识库超时时，应进入 FallbackAgent。

## 12. 实施阶段

### 阶段一：项目骨架

创建 Spring Boot WebFlux 项目，引入 Spring AI、Actuator、测试依赖，完成 SSE 聊天接口。Redis 在第二阶段接入，第一阶段可使用内存状态实现。

### 阶段二：基础领域模型

实现 ConversationState、RouteDecision、AgentRequest、AgentResponseEvent、KnowledgeChunk 等核心模型。

### 阶段二点五：语音转写链路

实现 VoiceController、VoicePipelineService、AsrClient、MockAsrClient 和预留 HttpAsrClient，将 stable transcript 转换为 ChatRequest 后进入 RouterService。

### 阶段三：路由与 Agent Runtime

实现 RouterService、AgentSwitchPolicy、AgentRuntime 和基础 Agent 接口。

### 阶段四：Spring AI 适配层

实现 SpringAiGateway、PromptFactory 和流式模型调用封装。

### 阶段五：知识库接口

实现 KnowledgeBaseClient、MockKnowledgeBaseClient 和预留 HttpKnowledgeBaseClient。

### 阶段六：状态管理

接入 Redis，实现会话状态保存、恢复、上下文裁剪和路由历史记录。

### 阶段七：指标监控

实现 MetricsRecorder、TraceContext 和阶段耗时埋点。

### 阶段八：性能与路由回归测试

编写 QA、RAG、路由跳转、兜底超时测试，验证首字符响应指标。

## 13. 验收标准

功能验收：

- 语音输入能通过 ASR 转成稳定文本，并进入统一 RouterService。
- 用户输入能完成问题改写和意图判断。
- 主路由能分发到正确子 Agent。
- 子 Agent 能根据上下文保持或跳转。
- 会话状态能保存当前 Agent、上下文、工具结果和 RAG 结果。
- 知识库接口可使用 Mock 实现替换真实实现。
- 每次请求可查看完整 trace 和阶段耗时。
- ASR partial transcript 只用于展示，stable transcript 才进入正式会话上下文。

性能验收：

- 语音首个转写片段响应建议小于等于 1000ms。
- QA 命中首字符响应小于等于 200ms。
- 单次 RAG 首字符响应小于等于 900ms。
- 路由分发加子 Agent RAG 首字符响应小于等于 1400ms。
- 最终兜底回复首字符响应小于等于 6000ms。

工程验收：

- Spring AI 被限制在 ai 和 knowledge 适配层。
- 业务 Agent 不直接依赖具体模型供应商。
- 路由、状态、监控均有独立测试。
- Prompt 可配置、可版本化、可回归测试。




