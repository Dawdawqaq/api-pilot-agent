# ApiPilot 开发者贡献指南

感谢你对 ApiPilot 项目的关注与贡献。为了保证代码库的工程质量、架构一致性与协作效率，请在提交代码前仔细阅读本指南。

---

## 1. 环境准备

参与 ApiPilot 开发前，请确保本地已就绪以下基础开发环境：

### 1.1 基础运行时与构建工具
* **JDK**: 21（必须，不支持 JDK 17 或更低版本）
* **Maven**: 3.9+（必须）

### 1.2 依赖基础设施
* **MySQL**: 8.x（关系型数据持久化）
* **Redis**: 7.x（任务状态流转、排他租约与执行锁）
* **Qdrant**: 1.14.x（向量知识库与混合检索）
* **MinIO**: S3 兼容对象存储（业务文档与测试资产归档）

> 提示：上述基础设施可通过项目根目录提供的 `docker-compose.yml` 一键拉起，也可以连接本地已有的独立服务实例。

### 1.3 环境变量配置
复制根目录模板文件生成本地环境变量配置文件：

```bash
cp .env.example .env
```

根据本地环境填写数据库连接（`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`）及核心密钥（`SECRET_STORE_MASTER_KEY`）。

---

## 2. 本地启动与调试

ApiPilot 采用开源免登录工作区模式，项目启动后直接进入主控制台。

### 2.1 默认确定性模式（开发推荐）
默认激活的 Spring Profile 为 `local,stub`。此模式下使用本地确定性规划器与规则推断，不需要配置任何外部大模型 API Key 即可完整调试所有端到端流程：

```bash
mvn spring-boot:run
```

### 2.2 接入真实大模型
若需调试真实的大模型计划推断与 RAG 检索生成，请将 Profile 切换为 `local,deepseek` 并配置模型凭证：

```bash
# 设置大模型参数（以 DeepSeek 为例）
export AI_BASE_URL="https://api.deepseek.com"
export AI_API_KEY="<your-api-key>"
export AI_CHAT_MODEL="deepseek-chat"
export SPRING_PROFILES_ACTIVE="local,deepseek"

mvn spring-boot:run
```

### 2.3 数据库与向量索引初始化
* **关系型数据库**：首次启动时，Flyway 会自动执行 `src/main/resources/db/migration/` 下的迁移脚本完成建表与结构更新。
* **Qdrant 向量集合**：应用启动时会自动检测并初始化 Knowledge Collection，并校验向量维度与配置的一致性。

启动成功后，浏览器访问控制台：`http://localhost:8080/`。

---

## 3. 分支与提交规范

### 3.1 分支管理
* 所有新功能的开发与 Bug 修复均应基于最新的 `main` 分支拉取新分支；
* 分支命名格式：
  * 新功能：`feat/<简述>`（例：`feat/plan-modification-limit`）
  * 问题修复：`fix/<简述>`（例：`fix/qdrant-dimension-validation`）
  * 文档变更：`docs/<简述>`（例：`docs/update-contributing`）
  * 重构优化：`refactor/<简述>`（例：`refactor/remove-auth-coupling`）

### 3.2 Commit Message 规范
提交信息应清晰反映修改意图，推荐格式：

```text
<type>(<scope>): <中文简述>
```

* **Type 类型**：`feat`（新功能）、`fix`（修复）、`docs`（文档）、`refactor`（重构）、`test`（测试）、`chore`（构建/杂项）
* **Scope 范围**：`agent`、`openapi`、`retrieval`、`executor`、`contract`、`report`、`common` 等模块名
* **示例**：
  * `feat(agent): 增加多轮计划修改熔断机制与租约排他锁`
  * `fix(retrieval): 修复 Qdrant 初始化集合时的维度防崩溃校验`
  * `docs(readme): 更新免登录工作区与快速启动指南`

### 3.3 PR 粒度原则
* **单一职责**：每个 Pull Request 应当只专注于解决一个明确的问题或交付一个独立的功能特性；
* PR 标题与对应功能分支名称保持语义对齐。

---

## 4. 代码风格与架构规范

### 4.1 中文注释与 JavaDoc 标准
* 所有公共类（Public Class）、接口、公共方法（Public Method）**必须配备结构化中文 JavaDoc 注释**；
* 必须规范注明功能描述、`@param` 入参含义、`@return` 返回值含义及 `@throws` 异常边界；
* **代码及注释中禁止使用 Emoji 表情**，保持代码风格严肃专业。

### 4.2 敏感凭证零落地
* **严禁在代码中硬编码任何真实密码、网关 Token、API Key 或密钥**；
* 所有敏感配置必须通过 `application.yml` 结合系统环境变量注入；
* 涉及执行日志与报告展示的数据，必须严格接入 `SensitiveDataSanitizer` 进行脱敏处理。

### 4.3 模块分层架构
ApiPilot 严格遵循清晰的 DDD / 分层架构设计：

```text
com.dochelper.<module>
├── api                  # REST 控制器、DTO 请求体与 VO 响应体
├── application          # 应用用例编排、事务边界与跨聚合服务
├── domain               # 聚合根、领域实体、值对象、领域异常与仓储接口
└── infrastructure       # 仓储实现（MyBatis-Plus）、外部适配器与基础设施对接
```

* **依赖规则**：`api` → `application` → `domain` ← `infrastructure`；
* 领域层（`domain`）严禁直接反向依赖外部技术框架或具体持久化实现。

---

## 5. 测试要求

### 5.1 单元测试标准
* 提交 PR 前，必须在本地执行全量测试并确保 **100% 成功通过（0 失败，0 错误）**：
  ```bash
  mvn test
  ```
* 任何新增业务逻辑、算法或异常分支必须附带对应的单元测试，单元测试应保持轻量、无外部基础设施强依赖（推荐使用 Mockito）。

### 5.2 集成测试（IT）
* 依赖公共基础设施（真实 MySQL、Redis、MinIO、Qdrant）的端到端集成测试，测试类命名统一遵循 `*IT.java`；
* 集成测试由 Maven Failsafe 插件独立调度执行：
  ```bash
  mvn -Ppublic-infra-it verify
  ```

---

## 6. PR 提交流程

1. **Fork 本仓库** 到个人 GitHub 账号；
2. **克隆并创建分支**：从最新的 upstream `main` 创建本地特性分支；
3. **本地编码与验证**：完成修改后，务必在本地运行 `mvn test` 确认全量测试通过；
4. **提交代码并推送**：按照提交规范提交，推送到个人的 remote 仓库；
5. **创建 Pull Request**：
   * 指向官方仓库的 `main` 分支；
   * 按照模板清晰描述：**变更目的（Why）**、**技术实现（What）**与**验证方式（How）**；
6. **自动化 CI 与 Code Review**：
   * 等待 GitHub Actions CI 自动化流水线构建与测试通过；
   * 积极响应 Maintainer 的 Review 审查意见并补充调整。
