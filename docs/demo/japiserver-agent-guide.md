# JApiServer Agent 联调规则

## 登录后查询资料

JApiServer 的登录接口是 `POST /auth/login`。Agent 任务会提供 `username` 和
`password` 两个初始变量，计划中必须使用 `{{username}}` 与 `{{password}}`
占位符，禁止把变量值复制到计划或日志。

登录成功后，访问令牌位于 `$.data.token`。第一步需要把它提取为
`accessToken`，后续请求通过 Bearer 认证传入 `{{accessToken}}`。

推荐只读验证链路：

1. 调用 `POST /auth/login`，断言 HTTP 200，并提取 `$.data.token`。
2. 调用 `GET /api/v1/user/profile`，使用 Bearer Token，断言 HTTP 200、
   `$.data.username` 存在且等于初始变量 `username`。
3. 如需更多只读证据，可调用 `GET /api/v1/dashboard/stats?range=7d`，
   使用相同 Bearer Token 并断言 HTTP 200。

## 健康检查

`GET /health/ready` 不需要认证。HTTP 200 表示 Java 服务、MySQL 和 Redis
共同就绪；若核心依赖不可用，接口返回 HTTP 503。

## 安全约束

- Agent 不得调用接口目录之外的路径。
- 默认自动评估只执行 GET 和登录 POST。
- 密码和访问令牌必须在入库前脱敏。
- 删除账号、轮换 API Key、修改资料等写操作不属于自动评估范围。
