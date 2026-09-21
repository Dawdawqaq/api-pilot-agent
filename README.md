# ApiPilot

ApiPilot 是一个面向个人开发者的 REST API 调用链测试 Agent。它可以导入 OpenAPI
文档和业务规则，接收自然语言测试目标，由大模型生成结构化计划，再由 Java 执行器
完成接口选择、参数传递、契约校验、失败恢复与报告生成。

项目采用“模型参与决策，程序控制执行”的边界：大模型不会直接发起网络请求；每个
HTTP 步骤都必须通过 OpenAPI 接口目录、操作风险、目标地址和敏感数据策略校验。
当前定位为本机单用户工具，不提供多用户安全隔离。

## 核心能力

- 导入 OpenAPI 3.x、Swagger 2.x，以及 Markdown、TXT、PDF 业务文档
- 可选业务知识库使用 MySQL 与 Qdrant 混合检索，并通过 RRF 融合排序
- 将自然语言目标转换为多步骤 API 测试计划
- 在模型规划前按路径、业务词、读写意图和列表/详情形态筛选 OpenAPI 候选，并记录可解释分数
- 支持 JSONPath 变量提取、跨步骤传参、响应断言和 OpenAPI Schema 契约校验
- 根据请求参数与响应字段推断接口依赖候选，生成生产者—消费者关系
- 自动生成缺参、非法枚举、错误类型等基础负向用例
- 以步骤为粒度持久化执行状态，对读取请求有限重试，复用已成功步骤的运行时结果
- 对可修复的参数或提取错误进行剩余计划重规划，并锁定已完成步骤
- 通过 SSE 推送任务状态、工具轨迹、计划 Diff 和测试结果
- 输出覆盖率指标、脱敏报告和 JUnit XML
- 规划或执行失败也生成终态报告，保留错误阶段、候选证据和已有步骤审计

## 安全与可靠性

- 采用开源免登录工作区模式，即开即用
- HTTP 请求必须匹配当前项目已导入的 OpenAPI Operation
- 写操作和删除操作必须使用服务端持久化的确认记录，模型字段不能替代人工确认
- 确认绑定计划哈希；修改计划时原子更新计划、运行时引用和待确认记录，拒绝旧版本批准
- 自动修复不能改变接口、原有业务断言及提取变量名称；写请求结果不确定时停止并要求核验
- 执行前拒绝环回、链路本地、云 Metadata 等受限目标，并限制危险请求头
- Token、Cookie、密码、邮箱和手机号等内容在事件、报告和回放样本中统一脱敏
- 运行时敏感值以引用流转，数据库实现使用 AES-GCM 加密
- 任务使用数据库租约领取和续约，应用重启后可以回收过期任务
- 写入结果无法确认时进入 `NEEDS_REVIEW`，停止自动重放并生成独立核验报告
- 按全局和项目限制非终态任务数量，容量满时返回明确的 HTTP 429
- 项目可限制外部模型供应商、文档与 Schema 出站、候选接口 Top-K 和 Prompt 预算

## 工作流程

```mermaid
flowchart LR
    A["OpenAPI 与业务文档"] --> B["接口目录与知识库"]
    U["自然语言测试目标"] --> C["可选业务知识检索"]
    B --> C
    C --> D["Agent 生成结构化计划"]
    D --> E["目录、参数与风险校验"]
    E --> F{"是否包含危险操作"}
    F -->|是| G["人工确认或多轮对话调整"]
    F -->|否| H["步骤级 Java 执行器"]
    G --> H
    H --> I["变量提取与契约断言"]
    I --> J{"结果是否可修复"}
    J -->|是| K["仅重规划剩余步骤"]
    K --> E
    J -->|否| L["覆盖率、报告与回放样本"]
```

## 技术栈

| 领域 | 技术 |
| --- | --- |
| 应用框架 | Java 21、Spring Boot 3.5、WebFlux |
| Agent 与模型 | Spring AI、Spring AI Alibaba Agent Framework、OpenAI-compatible Chat / Embedding API |
| 数据访问 | MyBatis-Plus、Flyway、MySQL 8 |
| 向量与检索 | MySQL、Qdrant、RRF |
| 文件存储 | MinIO |
| 测试 | JUnit 5、WireMock、Testcontainers、Playwright |

## 主要模块

```text
com.dochelper
├── agent          # 任务编排、计划生成、多轮修改、确认、重规划、租约与事件流
├── common         # 全局通用响应、全局异常与匿名安全上下文
├── contract       # Schema 校验、负向用例、覆盖率与失败回放
├── evaluation     # 可重复质量评测记录
├── executor       # Operation 解析、HTTP 执行、变量、重试与步骤恢复
├── governance     # 项目级模型数据出站策略
├── knowledge      # 文档解析、切分与知识管理
├── openapi        # OpenAPI 导入、接口目录与依赖候选
├── project        # 项目与多环境管理
├── retrieval      # 混合检索与 RRF 融合
├── secret         # 运行时敏感值引用与加密存储
└── report         # 测试报告、步骤明细、Markdown 与 JUnit XML 导出
```

## 环境要求与快速拉起

- **JDK**: 21
- **Maven**: 3.9+
- **MySQL**: 8.x
- **可选知识库**：Qdrant 1.14.x、MinIO；核心模式不需要这两个服务

### 快速一键拉起（推荐 Docker Compose）

推荐核心模式：`docker-compose.core.yml` 仅启动应用与 MySQL。先设置 `AI_API_KEY`、
`SECRET_STORE_MASTER_KEY`、`DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`，再构建启动。
此配置只向本机暴露应用端口，使用真实 DeepSeek：

```bash
docker compose -f docker-compose.core.yml up --build -d
```

启动完成后访问 `http://localhost:28080/` 进入 Web 控制台。此 Compose 与共享 dev-infra 是两种启动方式，不必同时运行。

需要业务文档检索时，原 `docker-compose.yml` 提供应用、MySQL、Qdrant 与 MinIO：
`docker compose -f docker-compose.yml up --build -d`。两种配置使用各自数据库卷，不会自动迁移数据；不要同时占用相同应用端口。

> 配置变量参考 [`.env.example`](.env.example)。Flyway 自动迁移数据库；仅知识库模式会初始化 Qdrant Collection，完整 Compose 另外初始化 MinIO 桶。

## 本地启动

先准备数据库和基础设施，再设置核心环境变量：

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/dochelper?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:DB_USERNAME = 'dochelper'
$env:DB_PASSWORD = '<your-database-password>'
$env:SECRET_STORE_MASTER_KEY = '<a-stable-random-32-byte-secret>'
```

默认真实模型演示使用 `local,deepseek,lightweight`，只需共享 MySQL。Windows 可执行以下命令；脚本从外部文件读取密钥，不写入仓库：

```powershell
mvn package
./scripts/start-real-local.ps1 -KeyFile '<密钥文件绝对路径>' -Port 18081
```

需要业务知识库时，确保 Qdrant、MinIO 就绪，在启动脚本后加 `-WithKnowledge`。
核心模式保留 OpenAPI 导入、Schema 证据、Agent、执行与报告，业务文档入口禁用；不是用空向量或模拟结果代替知识库。

启动后可直接访问：

- Web 控制台：`http://localhost:18081/`
- 健康检查：`http://localhost:18081/actuator/health`
- 系统概览：`http://localhost:18081/api/v1/system/overview`

裸跑 `mvn spring-boot:run` 仍默认 `local,stub`，仅用于离线开发，不能用于展示真实模型效果。
脚本日志在 `output/app.log`，启动失败先检查数据库连接、端口占用及模型配置。脚本不负责启动基础设施。

> `SECRET_STORE_MASTER_KEY` 在长期运行环境中必须稳定保存。
> Windows 启动脚本默认用当前用户 DPAPI 保存本地主密钥到 Git 忽略的 `.local-notes`，支持后续重启。
> 裸 Java 启动未配置时仍使用临时密钥；容器需要显式配置稳定主密钥。

## 接入 DeepSeek

配置 OpenAI-compatible Chat API 并切换 Profile：

```powershell
$env:AI_BASE_URL = 'https://api.deepseek.com'
$env:AI_API_KEY = '<your-api-key>'
$env:AI_CHAT_MODEL = 'deepseek-flash'
$env:SPRING_PROFILES_ACTIVE = 'local,deepseek,lightweight'

mvn spring-boot:run
```

DeepSeek Profile 用于生成结构化测试计划。当前 Profile 未配置独立的 Embedding
接口，向量生成保留确定性本地回退，用于验证 RAG 工程链路；生产环境可配置真实
语义 Embedding Model（如 `text-embedding-v4`）。

## 运行测试

运行单元测试：

```powershell
mvn test
```

在公共 MySQL、Qdrant 和 MinIO 已启动时运行集成测试：

```powershell
mvn -Ppublic-infra-it "-Dit.test=com.dochelper.infrastructure.PublicInfrastructureIT" verify
```

评测资源位于 `src/test/resources/evaluation`，包含 3 份不同规模 OpenAPI、50 条检索与
规划回归样本，以及 15 条安全攻击样本。样本数量校验和安全策略测试可以离线复现；
真实模型的任务成功率、Token 与耗时需要在固定模型版本和有效 API Key 下单独运行，
项目不把静态样本数量当作模型通过率。

本轮验证范围、真实模型结果与复现命令见 [核心链路验收记录](docs/core-agent-validation.md)。
轻量模式与扩展失败场景见 [第二阶段验收](docs/phase2-validation.md)。
取消、超时、进程重启及真实丢响应结果见 [第三阶段验收](docs/phase3-resilience.md)。
浏览器真实链路、交互改进及响应式验收见 [第四阶段验收](docs/phase4-browser-demo.md)。
接口候选排序、轻量轨迹和失败报告见 [第五阶段验收](docs/phase5-agent-quality.md)。
最终候选召回、真实模型回归和任务准入数据见 [最终回归记录](docs/final-regression.md)。
单元测试证明程序约束，不等于模型任务成功率。
设计取舍、可用简历表述及追问准备见 [面试讲解提纲](docs/agent-interview-guide.md)。

## 示例资料

被测服务的 OpenAPI 与业务知识应由对应项目维护，避免 ApiPilot 仓库复制并逐渐产生过期契约。
本仓库仅保留自动化测试所需的固定夹具，以及[技术基线 ADR](docs/adr/0001-stage0-technology-baseline.md)。

## 当前边界

- 执行器面向 API 功能与契约测试，不替代 JMeter、k6 等专业压测工具
- SSRF 防护在应用层执行地址解析与网段拒绝；高安全部署仍应使用独立执行网络和出站代理，
  以消除 DNS 校验与实际连接之间的竞态窗口
- 本地 Embedding 回退不代表真实语义检索质量
- 核心模式只需 MySQL；知识库模式另需 Qdrant、MinIO。仍非单文件工具，构建包也尚未按可选模块拆分
- 写操作失败可能已经产生业务副作用；任务状态 `NEEDS_REVIEW` 与错误码 `EXECUTOR_409_003` 表示需要人工核验，不能直接重跑
- 每个 HTTP 步骤及重试前检查取消与截止时间；不能撤回已发出的请求，不承诺任意崩溃恢复或恰好一次执行
- SSE 当前使用数据库增量轮询；任务规模显著增长后再评估 Redis Stream
- 质量评测结果取决于固定的数据集、模型版本和运行环境，不发布未经实际运行的成功率

## 开源许可 (License)

本项目采用 [Apache License 2.0](LICENSE) 开源许可证。
