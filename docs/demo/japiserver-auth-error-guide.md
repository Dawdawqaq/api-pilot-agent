# JApiServer 登录异常规则

调用 `POST /auth/login` 时，如果账号不存在或密码错误，JApiServer 返回 HTTP 401。
错误响应中的 `data` 为 null，不会签发访问令牌，因此 `$.data.token` 应当不存在。

异常登录评测使用不存在的账号，避免增加真实测试账号的失败次数或触发账号锁定。
