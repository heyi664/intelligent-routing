# 语音客服 Agent 路由下一阶段开发计划

> **给后续执行者：** 实施本计划时必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，按任务逐项执行。每一步都使用 checkbox（`- [ ]`）跟踪进度。

**目标：** 将当前关键词规则路由升级为可测试的 Qwen 结构化智能路由，并补齐最小可用的 trace 日志、fallback 策略和本地 RAG 基础能力。

**架构思路：** 保持 `RouterService` 作为确定性编排层：先走 QA 快速命中，再由 `RouterAgent` 产出候选路由，由 `AgentSwitchPolicy` 做最终决策，最后交给 `AgentRuntime` 执行目标 Agent。LLM、DashScope/Qwen、腾讯 ASR 等供应商细节继续封装在 `ai/`、`asr/` 和路由适配类里，业务 Agent 只依赖项目自己的接口。

**技术栈：** Java 17、Maven、Spring Boot 3.3.13、Spring Web MVC、JUnit 5、DashScope/Qwen 兼容 Chat Completions、腾讯云 ASR Java SDK 适配。

## 全局约束

- 保持 `POST /api/chat` 和 `POST /api/voice/demo` 的请求/响应兼容。
- 保持 `app.chat.agent-mode=manual` 手动 Agent 调试入口可用。
- QA 快速命中必须早于路由、Agent、RAG、LLM 调用。
- 保留 `RuleBasedRouterAgent` 作为本地 fallback 和确定性测试实现。
- 业务 Agent 不直接依赖 DashScope/Qwen DTO 或腾讯云 SDK 类型。
- 当前基线验证命令：`mvn test`，已通过 `18 tests, 0 failures, 0 errors`。

---

## 当前项目结构梳理

- `src/main/java/com/xinchan/voiceqa/api/`：同步文本问答 HTTP 接口、请求/响应 DTO、聊天配置。
- `src/main/java/com/xinchan/voiceqa/voice/`：语音 Demo 编排，将 stable ASR transcript 转为 `ChatRequest`。
- `src/main/java/com/xinchan/voiceqa/asr/`：`AsrClient`、Mock ASR、腾讯云 ASR SDK 适配、ASR provider 配置。
- `src/main/java/com/xinchan/voiceqa/ai/`：Qwen/DashScope 兼容请求、响应、HTTP transport、`SpringAiGateway`。
- `src/main/java/com/xinchan/voiceqa/routing/`：`RouterService`、`RouterAgent`、规则路由、路由候选、路由决策、Agent 切换策略。
- `src/main/java/com/xinchan/voiceqa/agent/`：具体 Agent、Prompt 工厂、LLM responder、Agent runtime 分发。
- `src/main/java/com/xinchan/voiceqa/qa/`：内存 QA 快速命中 Demo。
- `src/main/java/com/xinchan/voiceqa/conversation/`：内存会话状态仓储，当前主要记录 current agent。
- `src/main/java/com/xinchan/voiceqa/knowledge/`：知识库接口和 Mock 知识片段。

## 本计划涉及文件

- 新增：`src/main/java/com/xinchan/voiceqa/routing/QwenRouterAgent.java`  
  职责：调用聊天模型生成结构化路由候选，并将合法 JSON 转为 `RouteCandidate`。
- 新增：`src/main/java/com/xinchan/voiceqa/routing/RouterPromptFactory.java`  
  职责：构造路由专用 system/user prompt，并明确模型必须返回的 JSON schema。
- 修改：`src/main/java/com/xinchan/voiceqa/routing/RuleBasedRouterAgent.java`  
  职责：当 `app.chat.router-provider=rule` 或未配置该属性时，继续作为默认 `RouterAgent` bean。
- 修改：`src/main/java/com/xinchan/voiceqa/api/ChatProperties.java`  
  职责：新增 router provider 和路由置信度阈值配置，保留原有 manual agent 行为。
- 修改：`src/main/resources/application.properties`  
  职责：默认使用安全的本地规则路由，可显式切换到 Qwen 路由。
- 修改：`src/main/java/com/xinchan/voiceqa/routing/AgentSwitchPolicy.java`  
  职责：使用配置化置信度阈值，低置信度进入 `CLARIFICATION_AGENT`。
- 新增：`src/main/java/com/xinchan/voiceqa/monitoring/ChatTrace.java`  
  职责：记录请求阶段事实，用于日志和后续指标。
- 新增：`src/main/java/com/xinchan/voiceqa/monitoring/ChatTraceLogger.java`  
  职责：每次请求完成后输出一条结构化摘要日志。
- 修改：`src/main/java/com/xinchan/voiceqa/routing/RouterService.java`  
  职责：记录路由阶段事实并调用 trace logger，不改变响应结构。
- 新增：`src/main/java/com/xinchan/voiceqa/knowledge/InMemoryKnowledgeBaseClient.java`  
  职责：用小型本地知识语料替代单条硬编码 mock chunk。
- 修改：`src/main/java/com/xinchan/voiceqa/agent/RagAgent.java`  
  职责：先检索本地知识片段，再将引用上下文加入 prompt；无结果时明确 fallback。

---

## 任务 1：路由 Provider 配置化

**文件：**
- 修改：`src/main/java/com/xinchan/voiceqa/api/ChatProperties.java`
- 修改：`src/main/java/com/xinchan/voiceqa/routing/RuleBasedRouterAgent.java`
- 修改：`src/main/resources/application.properties`
- 测试：`src/test/java/com/xinchan/voiceqa/routing/RouterAgentConfigurationTest.java`

**接口：**
- 消费：现有 `RouterAgent`、`RuleBasedRouterAgent`、`ChatProperties`。
- 产出：`ChatProperties.RouterProvider`、`getRouterProvider()`、`getRouteConfidenceThreshold()`。

- [ ] **步骤 1：先写失败测试**

```java
package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.api.ChatProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouterAgentConfigurationTest {

    @Test
    void defaultsToRuleRouterAndSixtyPercentThreshold() {
        ChatProperties properties = new ChatProperties();

        assertEquals(ChatProperties.RouterProvider.RULE, properties.getRouterProvider());
        assertEquals(0.60, properties.getRouteConfidenceThreshold(), 0.001);
    }
}
```

- [ ] **步骤 2：运行测试，确认失败**

运行：`mvn -Dtest=RouterAgentConfigurationTest test`

预期：编译失败，因为 `RouterProvider`、`getRouterProvider()`、`getRouteConfidenceThreshold()` 还不存在。

- [ ] **步骤 3：添加配置字段和默认值**

在 `ChatProperties` 中新增：

```java
public enum RouterProvider {
    RULE,
    QWEN
}

private RouterProvider routerProvider = RouterProvider.RULE;
private double routeConfidenceThreshold = 0.60;

public RouterProvider getRouterProvider() {
    return routerProvider;
}

public void setRouterProvider(RouterProvider routerProvider) {
    this.routerProvider = routerProvider;
}

public double getRouteConfidenceThreshold() {
    return routeConfidenceThreshold;
}

public void setRouteConfidenceThreshold(double routeConfidenceThreshold) {
    this.routeConfidenceThreshold = routeConfidenceThreshold;
}
```

在 `application.properties` 中新增：

```properties
app.chat.router-provider=rule
app.chat.route-confidence-threshold=0.60
```

- [ ] **步骤 4：让规则路由按配置生效**

给 `RuleBasedRouterAgent` 增加条件注解，使默认配置下只有规则路由作为 `RouterAgent` bean：

```java
package com.xinchan.voiceqa.routing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.chat", name = "router-provider", havingValue = "rule", matchIfMissing = true)
public class RuleBasedRouterAgent implements RouterAgent {
    ...
}
```

- [ ] **步骤 5：运行测试，确认通过**

运行：`mvn -Dtest=RouterAgentConfigurationTest test`

预期：`BUILD SUCCESS`。

- [ ] **步骤 6：提交**

```bash
git add src/main/java/com/xinchan/voiceqa/api/ChatProperties.java src/main/java/com/xinchan/voiceqa/routing/RuleBasedRouterAgent.java src/main/resources/application.properties src/test/java/com/xinchan/voiceqa/routing/RouterAgentConfigurationTest.java
git commit -m "feat: configure router provider"
```

## 任务 2：Qwen 结构化路由 Agent

**文件：**
- 新增：`src/main/java/com/xinchan/voiceqa/routing/RouterPromptFactory.java`
- 新增：`src/main/java/com/xinchan/voiceqa/routing/QwenRouterAgent.java`
- 测试：`src/test/java/com/xinchan/voiceqa/routing/QwenRouterAgentTest.java`

**接口：**
- 消费：`StreamingChatModelClient.streamAsText(ChatModelRequest)`、`ChatRequest`、`ConversationState`。
- 产出：`QwenRouterAgent.classify(ChatRequest, ConversationState)` 返回 `RouteCandidate`。

- [ ] **步骤 1：测试合法 JSON 和非法输出 fallback**

```java
package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.ai.StreamingChatModelClient;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.conversation.ConversationState;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QwenRouterAgentTest {

    @Test
    void parsesStructuredRouterJson() {
        StreamingChatModelClient model = request -> """
            {"rewrittenQuestion":"燃气泄漏应该怎么处理","intent":"SAFETY_EMERGENCY","targetAgent":"SAFETY_AGENT","shouldStayInCurrentAgent":false,"confidence":0.91,"reason":"用户询问燃气泄漏处理"}
            """;
        QwenRouterAgent agent = new QwenRouterAgent(model, new RouterPromptFactory());

        RouteCandidate candidate = agent.classify(
            new ChatRequest("c-1", "u-1", "家里漏气了怎么办"),
            new ConversationState("c-1", "u-1", RouteTarget.PAYMENT_AGENT, Instant.now())
        );

        assertEquals(RouteTarget.SAFETY_AGENT, candidate.targetAgent());
        assertEquals("SAFETY_EMERGENCY", candidate.intent());
        assertEquals(0.91, candidate.confidence(), 0.001);
    }

    @Test
    void invalidModelOutputRoutesToClarification() {
        StreamingChatModelClient model = request -> "我觉得需要澄清";
        QwenRouterAgent agent = new QwenRouterAgent(model, new RouterPromptFactory());

        RouteCandidate candidate = agent.classify(
            new ChatRequest("c-2", "u-1", "帮我看看这个"),
            new ConversationState("c-2", "u-1", RouteTarget.CLARIFICATION_AGENT, Instant.now())
        );

        assertEquals(RouteTarget.CLARIFICATION_AGENT, candidate.targetAgent());
        assertEquals("ROUTER_OUTPUT_INVALID", candidate.intent());
        assertEquals(0.0, candidate.confidence(), 0.001);
    }
}
```

- [ ] **步骤 2：运行测试，确认失败**

运行：`mvn -Dtest=QwenRouterAgentTest test`

预期：编译失败，因为 `QwenRouterAgent` 和 `RouterPromptFactory` 还不存在。

- [ ] **步骤 3：实现路由 Prompt 工厂**

创建 `RouterPromptFactory`：

```java
package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.conversation.ConversationState;

public class RouterPromptFactory {

    public String systemPrompt() {
        return """
            你是中文燃气客服助手的路由 Agent。
            只能返回 JSON，不要返回解释性文本。
            JSON 字段必须包含：
            rewrittenQuestion, intent, targetAgent, shouldStayInCurrentAgent, confidence, reason。
            targetAgent 只能是 PAYMENT_AGENT, SAFETY_AGENT, BUSINESS_DECISION_AGENT, RAG_AGENT, CLARIFICATION_AGENT, FALLBACK_AGENT。
            用户意图不完整或无法判断时，使用 CLARIFICATION_AGENT。
            """;
    }

    public String userPrompt(ChatRequest request, ConversationState state) {
        return """
            conversationId: %s
            userId: %s
            currentAgent: %s
            userMessage: %s
            """.formatted(
            request.conversationId(),
            request.userId(),
            state.currentAgent(),
            request.message()
        );
    }
}
```

- [ ] **步骤 4：实现 Qwen 路由 Agent**

创建 `QwenRouterAgent`。注意加条件注解，只有 `app.chat.router-provider=qwen` 时启用：

```java
@Component
@ConditionalOnProperty(prefix = "app.chat", name = "router-provider", havingValue = "qwen")
public class QwenRouterAgent implements RouterAgent {
    private final StreamingChatModelClient modelClient;
    private final RouterPromptFactory promptFactory;

    public QwenRouterAgent(StreamingChatModelClient modelClient, RouterPromptFactory promptFactory) {
        this.modelClient = modelClient;
        this.promptFactory = promptFactory;
    }

    @Override
    public RouteCandidate classify(ChatRequest request, ConversationState state) {
        try {
            String content = modelClient.streamAsText(new ChatModelRequest(
                promptFactory.systemPrompt(),
                promptFactory.userPrompt(request, state),
                request.conversationId(),
                request.conversationId()
            ));
            return parseCandidate(content);
        } catch (RuntimeException ex) {
            return new RouteCandidate(request.message(), "ROUTER_MODEL_ERROR", RouteTarget.CLARIFICATION_AGENT, false, 0.0, "invalid router output");
        }
    }

    // parseCandidate 可先按当前扁平 JSON schema 做字段提取；后续如引入 Jackson，再替换为结构化解析。
}
```

- [ ] **步骤 5：运行测试，确认通过**

运行：`mvn -Dtest=QwenRouterAgentTest test`

预期：`BUILD SUCCESS`。

- [ ] **步骤 6：提交**

```bash
git add src/main/java/com/xinchan/voiceqa/routing/RouterPromptFactory.java src/main/java/com/xinchan/voiceqa/routing/QwenRouterAgent.java src/test/java/com/xinchan/voiceqa/routing/QwenRouterAgentTest.java
git commit -m "feat: add qwen structured router"
```

## 任务 3：路由切换策略使用配置化阈值

**文件：**
- 修改：`src/main/java/com/xinchan/voiceqa/routing/AgentSwitchPolicy.java`
- 测试：`src/test/java/com/xinchan/voiceqa/routing/AgentSwitchPolicyTest.java`

**接口：**
- 消费：`ChatProperties.getRouteConfidenceThreshold()`。
- 产出：由配置阈值驱动的 `AgentSwitchPolicy.decide(...)`。

- [ ] **步骤 1：写阈值测试**

```java
package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.api.ChatProperties;
import com.xinchan.voiceqa.conversation.ConversationState;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentSwitchPolicyTest {

    @Test
    void routesLowConfidenceToClarification() {
        ChatProperties properties = new ChatProperties();
        properties.setRouteConfidenceThreshold(0.75);
        AgentSwitchPolicy policy = new AgentSwitchPolicy(properties);

        RouteDecision decision = policy.decide(
            new ConversationState("c-1", "u-1", RouteTarget.PAYMENT_AGENT, Instant.now()),
            new RouteCandidate("问题", "PAYMENT", RouteTarget.PAYMENT_AGENT, true, 0.70, "below threshold")
        );

        assertEquals(RouteTarget.CLARIFICATION_AGENT, decision.target());
    }
}
```

- [ ] **步骤 2：运行测试，确认失败**

运行：`mvn -Dtest=AgentSwitchPolicyTest test`

预期：编译失败，因为 `AgentSwitchPolicy(ChatProperties)` 构造方法还不存在。

- [ ] **步骤 3：注入 `ChatProperties`**

修改 `AgentSwitchPolicy`：

```java
private final ChatProperties chatProperties;

public AgentSwitchPolicy(ChatProperties chatProperties) {
    this.chatProperties = chatProperties;
}

public RouteDecision decide(ConversationState state, RouteCandidate candidate) {
    if (candidate.confidence() < chatProperties.getRouteConfidenceThreshold()) {
        return RouteDecision.to(RouteTarget.CLARIFICATION_AGENT, candidate, false);
    }
    ...
}
```

- [ ] **步骤 4：更新测试中的直接构造**

将测试里的 `new AgentSwitchPolicy()` 替换为：

```java
new AgentSwitchPolicy(new ChatProperties())
```

- [ ] **步骤 5：运行路由相关测试**

运行：`mvn -Dtest=AgentSwitchPolicyTest,RouterServiceManualAgentTest,DemoTest test`

预期：`BUILD SUCCESS`。

- [ ] **步骤 6：提交**

```bash
git add src/main/java/com/xinchan/voiceqa/routing/AgentSwitchPolicy.java src/test/java/com/xinchan/voiceqa/routing/AgentSwitchPolicyTest.java src/test/java/com/xinchan/voiceqa/routing/RouterServiceManualAgentTest.java src/test/java/com/xinchan/voiceqa/DemoTestRunner.java
git commit -m "feat: configure route confidence threshold"
```

## 任务 4：请求 Trace 日志基础能力

**文件：**
- 新增：`src/main/java/com/xinchan/voiceqa/monitoring/ChatTrace.java`
- 新增：`src/main/java/com/xinchan/voiceqa/monitoring/ChatTraceLogger.java`
- 修改：`src/main/java/com/xinchan/voiceqa/routing/RouterService.java`
- 测试：`src/test/java/com/xinchan/voiceqa/monitoring/ChatTraceTest.java`

**接口：**
- 消费：`ChatRequest`、`RouteDecision`、`ChatResponse`。
- 产出：每次 route 调用生成一个 trace，包含 `targetAgent`、`fallbackUsed`、耗时等基础字段。

- [ ] **步骤 1：写 trace 模型测试**

```java
package com.xinchan.voiceqa.monitoring;

import com.xinchan.voiceqa.routing.RouteTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTraceTest {

    @Test
    void completedTraceContainsRouteFacts() {
        ChatTrace trace = ChatTrace.start("c-1", "u-1", "hello");

        ChatTrace completed = trace.complete(RouteTarget.SAFETY_AGENT, false, "router");

        assertEquals("c-1", completed.conversationId());
        assertEquals(RouteTarget.SAFETY_AGENT, completed.targetAgent());
        assertEquals("router", completed.source());
        assertTrue(completed.elapsedMs() >= 0);
    }
}
```

- [ ] **步骤 2：运行测试，确认失败**

运行：`mvn -Dtest=ChatTraceTest test`

预期：编译失败，因为 monitoring 类还不存在。

- [ ] **步骤 3：新增 trace record 和 logger**

新增 `ChatTrace` record，字段包含：`conversationId`、`userId`、`message`、`startedAtMs`、`elapsedMs`、`targetAgent`、`fallbackUsed`、`source`。

新增 `ChatTraceLogger`：每次完成请求时输出：

```text
chat trace conversationId={} userId={} targetAgent={} source={} fallbackUsed={} elapsedMs={}
```

- [ ] **步骤 4：接入 `RouterService`**

在 `RouterService.route` 开头创建 trace：

```java
ChatTrace trace = ChatTrace.start(request.conversationId(), request.userId(), request.message());
```

在每个 return 前完成并输出：

```java
ChatResponse response = agentRuntime.execute(request, decision);
traceLogger.logCompleted(trace.complete(response.targetAgent(), false, response.source()));
return response;
```

- [ ] **步骤 5：运行测试**

运行：`mvn -Dtest=ChatTraceTest,RouterServiceManualAgentTest test`

预期：`BUILD SUCCESS`。

再运行：`mvn test`

预期：全部测试通过，`Failures: 0`、`Errors: 0`。

- [ ] **步骤 6：提交**

```bash
git add src/main/java/com/xinchan/voiceqa/monitoring/ChatTrace.java src/main/java/com/xinchan/voiceqa/monitoring/ChatTraceLogger.java src/main/java/com/xinchan/voiceqa/routing/RouterService.java src/test/java/com/xinchan/voiceqa/monitoring/ChatTraceTest.java
git commit -m "feat: add chat trace logging"
```

## 任务 5：第一版本地 RAG 语料

**文件：**
- 新增：`src/main/java/com/xinchan/voiceqa/knowledge/InMemoryKnowledgeBaseClient.java`
- 修改：`src/main/java/com/xinchan/voiceqa/knowledge/MockKnowledgeBaseClient.java`
- 测试：`src/test/java/com/xinchan/voiceqa/knowledge/InMemoryKnowledgeBaseClientTest.java`

**接口：**
- 消费：`KnowledgeBaseClient.retrieve(KnowledgeQuery)`。
- 产出：面向安全、缴费、供气分析关键词的本地知识片段检索。

- [ ] **步骤 1：写检索测试**

```java
package com.xinchan.voiceqa.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InMemoryKnowledgeBaseClientTest {

    @Test
    void retrievesSafetyKnowledgeForLeakQuestion() {
        InMemoryKnowledgeBaseClient client = new InMemoryKnowledgeBaseClient();

        List<KnowledgeChunk> chunks = client.retrieve(new KnowledgeQuery("燃气泄漏怎么办", 3));

        assertFalse(chunks.isEmpty());
        assertEquals("safety-leak-001", chunks.get(0).id());
    }
}
```

- [ ] **步骤 2：运行测试，确认失败**

运行：`mvn -Dtest=InMemoryKnowledgeBaseClientTest test`

预期：编译失败，因为 `InMemoryKnowledgeBaseClient` 还不存在。

- [ ] **步骤 3：实现本地语料检索**

新增 `InMemoryKnowledgeBaseClient`，建议带 `@Primary`，避免和旧 `MockKnowledgeBaseClient` 注入冲突。检索逻辑先按关键词评分即可：

- `泄漏`、`漏气`、`阀门` 命中安全知识。
- `缴费`、`欠费`、`充值` 命中缴费知识。
- `供气`、`负荷`、`分析` 命中业务分析知识。

注意当前 `KnowledgeQuery` 字段为 `topK()`，不是 `limit()`。

- [ ] **步骤 4：运行测试**

运行：`mvn -Dtest=InMemoryKnowledgeBaseClientTest test`

预期：`BUILD SUCCESS`。

- [ ] **步骤 5：提交**

```bash
git add src/main/java/com/xinchan/voiceqa/knowledge/InMemoryKnowledgeBaseClient.java src/main/java/com/xinchan/voiceqa/knowledge/MockKnowledgeBaseClient.java src/test/java/com/xinchan/voiceqa/knowledge/InMemoryKnowledgeBaseClientTest.java
git commit -m "feat: add local knowledge corpus"
```

## 任务 6：RAG Agent 先检索知识再调用 LLM

**文件：**
- 修改：`src/main/java/com/xinchan/voiceqa/agent/RagAgent.java`
- 修改：`src/main/java/com/xinchan/voiceqa/agent/AgentPromptFactory.java`
- 测试：`src/test/java/com/xinchan/voiceqa/agent/RagAgentTest.java`

**接口：**
- 消费：`KnowledgeBaseClient.retrieve(new KnowledgeQuery(request.message(), 3))`。
- 产出：RAG 回答路径中带本地知识上下文和引用来源。

- [ ] **步骤 1：写 RAG Agent prompt 捕获测试**

测试目标：使用假的 `SpringAiGateway` 返回 `request.userPrompt()`，确认 prompt 中包含本地知识片段标题和内容，例如 `燃气泄漏处置`、`关闭阀门`。

- [ ] **步骤 2：运行测试，确认失败**

运行：`mvn -Dtest=RagAgentTest test`

预期：编译失败，因为 `RagAgent` 还没有接收 `KnowledgeBaseClient`。

- [ ] **步骤 3：给 RAG Agent 注入知识库客户端**

修改构造方法：

```java
private final LlmAgentResponder responder;
private final KnowledgeBaseClient knowledgeBaseClient;

public RagAgent(LlmAgentResponder responder, KnowledgeBaseClient knowledgeBaseClient) {
    this.responder = responder;
    this.knowledgeBaseClient = knowledgeBaseClient;
}
```

调用 responder 前检索知识片段：

```java
List<KnowledgeChunk> chunks = knowledgeBaseClient.retrieve(new KnowledgeQuery(request.message(), 3));
String context = chunks.stream()
    .map(chunk -> "[%s] %s: %s".formatted(chunk.id(), chunk.title(), chunk.content()))
    .collect(Collectors.joining("\n"));
```

将 `context` 放入 RAG prompt，最小改法可先拼到 `RouteDecision.reason` 中；更干净的改法是在 `AgentPromptFactory` 增加 RAG 专用 prompt 方法。

- [ ] **步骤 4：更新测试中的 Agent 构造**

将测试里的：

```java
new RagAgent(localResponder())
```

替换为：

```java
new RagAgent(localResponder(), new InMemoryKnowledgeBaseClient())
```

- [ ] **步骤 5：运行 Agent 相关测试**

运行：`mvn -Dtest=RagAgentTest,AgentRuntimeDispatchTest,RouterServiceManualAgentTest test`

预期：`BUILD SUCCESS`。

- [ ] **步骤 6：提交**

```bash
git add src/main/java/com/xinchan/voiceqa/agent/RagAgent.java src/main/java/com/xinchan/voiceqa/agent/AgentPromptFactory.java src/test/java/com/xinchan/voiceqa/agent/RagAgentTest.java src/test/java/com/xinchan/voiceqa/agent/AgentRuntimeDispatchTest.java src/test/java/com/xinchan/voiceqa/routing/RouterServiceManualAgentTest.java
git commit -m "feat: ground rag agent in local knowledge"
```

## 任务 7：端到端路由回归测试与 README 更新

**文件：**
- 新增：`src/test/java/com/xinchan/voiceqa/routing/RouterRegressionTest.java`
- 修改：`README.md`

**接口：**
- 消费：公开行为 `RouterService.route(ChatRequest)`。
- 产出：覆盖缴费、安全、业务分析、澄清、手动覆盖、QA 快速命中、语音 stable transcript 的回归测试。

- [ ] **步骤 1：新增路由回归测试**

```java
package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class RouterRegressionTest {

    @Autowired
    private RouterService routerService;

    @Test
    void safetyQuestionRoutesToSafetyAgent() {
        ChatResponse response = routerService.route(new ChatRequest("c-safety-regression", "u-1", "燃气泄漏怎么处理"));

        assertEquals(RouteTarget.SAFETY_AGENT, response.targetAgent());
    }

    @Test
    void unknownQuestionRoutesToClarificationAgent() {
        ChatResponse response = routerService.route(new ChatRequest("c-clarify-regression", "u-1", "这个怎么弄"));

        assertEquals(RouteTarget.CLARIFICATION_AGENT, response.targetAgent());
    }
}
```

- [ ] **步骤 2：运行回归测试**

运行：`mvn -Dtest=RouterRegressionTest test`

预期：`BUILD SUCCESS`。

- [ ] **步骤 3：更新 README 当前进度**

Qwen 路由完成后，将对应行更新为：

```markdown
| Qwen 智能路由 Agent | 已完成第一版 | 支持 Qwen 结构化候选路由输出，规则路由保留为本地 fallback/测试实现。 |
```

Trace/RAG 完成后，新增：

```markdown
| 请求链路 trace 日志 | 已完成第一版 | 输出 conversationId、targetAgent、source、fallbackUsed、elapsedMs。 |
| 本地 RAG 知识库 | 已完成第一版 | 使用内存知识片段完成安全、缴费、供气分析场景的基础检索。 |
```

- [ ] **步骤 4：运行全量验证**

运行：`mvn test`

预期：所有测试通过，`Failures: 0`、`Errors: 0`。

- [ ] **步骤 5：提交**

```bash
git add src/test/java/com/xinchan/voiceqa/routing/RouterRegressionTest.java README.md
git commit -m "test: add router regression coverage"
```

---

## 本计划之后的后续 Backlog

1. 在不破坏现有同步 `ChatResponse` 接口的前提下，新增 SSE 流式响应。
2. 增加 Redis 版 `ConversationStateRepository`，支持 TTL 和上下文裁剪。
3. 增加 Actuator 与 Micrometer 阶段耗时指标，覆盖 `qa_checked`、`route_decided`、`agent_started`、`llm_first_token`、`response_completed`。
4. 将本地 RAG 语料替换为 PostgreSQL + pgvector 或企业知识库 HTTP API。
5. 将 `/api/voice/demo` 的 JSON + Base64 输入替换为 multipart 上传，再扩展 WebSocket ASR partial transcript。
6. 在文本回答生成后增加 TTS 输出适配。

## 自检结果

- 需求覆盖：README 中 P0 的 Qwen 智能路由、fallback/trace 基础能力由任务 1-4 覆盖；P1 的第一版本地 RAG 由任务 5-6 覆盖；回归测试和文档更新由任务 7 覆盖。
- 占位扫描：本计划避免了“TODO/稍后实现/补充适当处理”这类不可执行描述；每个任务都有明确文件、接口、命令和预期结果。
- 类型一致性：计划使用当前代码中已有的 `RouteCandidate`、`RouteDecision`、`RouteTarget`、`StreamingChatModelClient`、`ChatModelRequest`、`ChatRequest`、`ChatResponse`、`KnowledgeBaseClient`、`KnowledgeQuery.topK()` 等名称。

计划已保存到 `docs/superpowers/plans/2026-07-07-next-stage-voice-agent-router.md`。

执行方式建议：

**1. Subagent-Driven（推荐）**：每个任务派发一个新 subagent，任务间做 review，迭代更快。

**2. Inline Execution**：在当前会话按计划逐项执行，每批任务后做检查点。
