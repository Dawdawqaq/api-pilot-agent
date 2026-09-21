# 为 ApiPilot 贡献代码

感谢你参与 ApiPilot。提交变更前，请先阅读下面的开发和验证约定。

## 开发环境

- JDK 21
- Maven 3.9+
- MySQL 8.x
- 可选：Qdrant 1.14.x、MinIO

核心模式只依赖 MySQL。知识库相关功能和集成测试还需要 Qdrant 与 MinIO。

复制配置模板并填写本机配置：

```bash
cp .env.example .env
```

不要提交 `.env`、API Key、数据库密码、运行日志或测试产生的数据。

## 本地运行

默认 Profile 为 `local,stub`，用于离线开发：

```bash
mvn spring-boot:run
```

使用真实 OpenAI-compatible Chat 模型时，配置对应地址、密钥和模型名称：

```bash
export AI_BASE_URL="https://api.deepseek.com"
export AI_API_KEY="<your-api-key>"
export AI_CHAT_MODEL="deepseek-flash"
export SPRING_PROFILES_ACTIVE="local,deepseek,lightweight"
mvn spring-boot:run
```

Windows 可以使用仓库中的启动脚本：

```powershell
mvn package
./scripts/start-real-local.ps1 -KeyFile '<密钥文件绝对路径>' -Port 18081
```

## 代码约定

- Java 类名使用 UpperCamelCase，方法与变量使用 lowerCamelCase，常量使用全大写下划线形式。
- 代码注释和 JavaDoc 使用中文；固定协议名、类名与官方 API 名称可以保留英文。
- Controller 负责参数接收与校验，业务逻辑放在 Application/Service 层，持久化实现放在 Infrastructure 层。
- 新增外部输入时必须考虑校验、大小限制、敏感信息脱敏和异常边界。
- 修改 Agent 执行链时，不得绕过 OpenAPI Operation、目标环境、HTTP 方法和人工确认约束。

## 测试

提交前运行单元测试：

```bash
mvn test
```

需要本地 MySQL、Qdrant 与 MinIO 的集成测试：

```bash
mvn -Ppublic-infra-it -Dit.test=com.dochelper.infrastructure.PublicInfrastructureIT verify
```

新增逻辑应覆盖关键成功路径和会改变安全边界的失败路径。测试数据请使用明确的 `[E2E_TEST]` 标记，并在测试完成后清理。

## 提交与 Pull Request

建议使用下面的提交格式：

```text
<type>(<scope>): <简要说明>
```

常用类型包括 `feat`、`fix`、`refactor`、`test`、`docs` 和 `chore`。

Pull Request 请说明：

- 要解决的问题
- 最终行为与关键设计
- 已运行的验证
- 兼容性、安全或数据迁移影响

保持单个 Pull Request 目标明确，避免同时混入无关格式化和重构。
