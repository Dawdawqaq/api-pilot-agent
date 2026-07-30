# ADR-0001：阶段 0 技术基线与兼容性结论

- 状态：已接受
- 日期：2026-07-29
- 决策范围：DocHelper / ApiPilot MVP

## 1. 背景

ApiPilot 需要同时使用 Spring Boot、Spring AI Alibaba Agent Framework、Spring AI Tool Calling、
OpenAI 兼容模型协议、WebFlux SSE 与 Qdrant VectorStore。上述组件独立升级较快，直接按“最新版本”
组合容易在运行期出现二进制或协议不兼容，因此阶段 0 先建立最小工程并执行真实集成测试。

## 2. 最终版本

| 组件 | 锁定版本 | 结论 |
| --- | --- | --- |
| Java | 21 | 编译、测试通过 |
| Spring Boot | 3.5.8 | 与 Spring AI Alibaba 1.1.2.2 官方父 POM 一致 |
| Spring AI Alibaba | 1.1.2.2 | 使用当前稳定修复版，不使用存在已知问题的 1.1.2.1 |
| Spring AI | 1.1.2 | 显式导入 BOM，避免业务依赖缺少版本 |
| Qdrant Java Client | 1.13.0 | 由 Spring AI 1.1.2 传递引入 |
| Qdrant Server | 1.14.1 | 与客户端次版本差不超过 1 |
| Maven | 3.9.x | 使用标准 Maven 构建 |

参考资料：

- [Spring AI Alibaba Releases](https://github.com/alibaba/spring-ai-alibaba/releases)
- [Spring AI Alibaba 1.1.2.2 父 POM](https://raw.githubusercontent.com/alibaba/spring-ai-alibaba/v1.1.2.2/pom.xml)
- [Spring AI Qdrant VectorStore 文档](https://docs.spring.io/spring-ai/reference/api/vectordbs/qdrant.html)
- [Spring AI Tool Calling 文档](https://docs.spring.io/spring-ai/reference/api/tools.html)

## 3. 决策

### 3.1 依赖管理

同时显式导入以下两个 BOM：

1. `org.springframework.ai:spring-ai-bom:1.1.2`
2. `com.alibaba.cloud.ai:spring-ai-alibaba-bom:1.1.2.2`

原因是 Spring AI Alibaba 的 BOM 不会为业务项目直接使用的全部 Spring AI 组件提供可解析版本。
只导入 Alibaba BOM 时，`spring-ai-openai` 与 Qdrant Starter 会出现 Maven 依赖版本缺失。

### 3.2 模型协议

模型接入使用 OpenAI 兼容协议，配置抽象为：

- `baseUrl`
- `apiKey`
- `chatModel`
- `embeddingModel`

当前默认值面向 DashScope，但业务代码不绑定单一厂商。阶段 0 只验证底层 OpenAI 模型实现能够解析，
不调用真实模型服务，也不要求 API Key。

### 3.3 无 API Key 启动

提供 `stub` Profile：

- `StubChatModel` 返回确定性对话结果。
- `DeterministicEmbeddingModel` 返回 4 维确定性向量。
- Qdrant 仍执行真实建表、写入、召回和删除。

因此模型额度与业务基础设施验证相互解耦。真实 API Key 只在阶段 7 联调时提供。

### 3.4 Agent Tool Calling

使用 Spring AI Alibaba `ReactAgent` 和 Spring AI 方法工具注册机制。Stub `ChatModel` 的
`getDefaultOptions()` 必须返回 `ToolCallingChatOptions`，否则 Agent Framework 会将普通
`DefaultChatOptions` 替换为工具调用选项并输出警告。

当前版本不在 Agent Builder 上重复传入独立 `chatOptions`。实测重复传入时，框架的选项合并逻辑会因
`DefaultToolCallingChatOptions` 缺少可合并的 `@JsonProperty` 字段而抛出异常。后续真实模型适配器也应
由 `ChatModel` 提供同类型默认选项，并为该行为保留兼容性测试。

### 3.5 Qdrant 服务端

Spring AI 1.1.2 传递引入 Qdrant Java Client 1.13.0。公共开发设施最初使用 Qdrant 1.18.2，
客户端明确报告次版本跨度超过 1，不属于受支持组合。

处理方式：

- 公共 Qdrant 锁定到 1.14.1。
- 使用新卷 `dev-infra_qdrant-1-14-data`。
- 保留原 `dev-infra_qdrant-data`，不执行破坏性降级或删除。
- 不关闭客户端兼容性检查。

## 4. 验证范围

自动化测试覆盖：

1. Java 21 下构建 `ReactAgent`。
2. 方法工具生成 JSON Schema 并完成调用。
3. WebFlux SSE 返回 `PLANNING → EXECUTING → SUCCEEDED` 生命周期事件。
4. 通过 gRPC 6334 对真实 Qdrant 完成向量写入、TopK 召回和删除。
5. 激活 `stub` Profile，在空 API Key 下装配 ChatModel、EmbeddingModel 和 VectorStore。

阶段 0 的测试命令：

```powershell
mvn test
```

## 5. 后果

正向影响：

- 后续阶段可以在固定版本上开发，不再反复处理框架漂移。
- 无 API Key 即可测试 Agent 结构、工具与 RAG 基础设施。
- Qdrant 版本由实际客户端约束驱动，避免隐藏协议风险。

约束：

- 升级 Spring AI 或 Spring AI Alibaba 时必须重新执行本 ADR 中的全部兼容性测试。
- 升级 Qdrant 前必须先检查传递引入的 Java Client 版本。
- `stub` Profile 仅用于本地开发和自动化验证，不能作为生产模型配置。
