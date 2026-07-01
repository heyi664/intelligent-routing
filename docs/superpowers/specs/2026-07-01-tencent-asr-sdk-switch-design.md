# Tencent ASR SDK Switch Design

目标：在现有语音客服 Demo 中接入腾讯云 ASR Java SDK，同时保留 Mock ASR 作为默认开发模式。

设计：新增 `app.asr.provider` 配置，默认 `mock`；配置为 `tencent` 时注入 `TencentAsrSdkClient`。腾讯云密钥优先读取 `app.asr.secret-id` / `app.asr.secret-key`，为空时读取环境变量 `TENCENT_ASR_SECRET_ID` / `TENCENT_ASR_SECRET_KEY`。真实 SDK 类型只出现在 `TencentCloudJavaSdkInvoker` 内部，业务链路继续依赖项目自己的 `AsrClient`。

数据流：`VoiceController` 接收音频请求，`VoicePipelineService` 调用当前激活的 `AsrClient`，只取 stable transcript 进入 `RouterService`。腾讯云 SDK 一句话识别把音频 bytes 转 Base64，设置 `SourceType=1`，调用 `SentenceRecognition` 并返回 `Result`。

错误处理：空音频、空识别结果、缺失密钥、腾讯云 SDK 异常都快速失败并带明确错误信息。实时 WebSocket ASR 暂不实现，继续保留 TODO。

验证：新增配置装配测试、SDK invoker 请求构造测试，保留现有 `mvn test` 全量验证。