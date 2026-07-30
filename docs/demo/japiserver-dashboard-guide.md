# JApiServer 控制台统计规则

租户管理员登录后可以调用 `GET /api/v1/dashboard/stats` 查询控制台统计。
接口必须携带 Bearer Token。

查询参数 `range` 支持 `7d` 和 `30d`，默认评测使用 `7d`。成功时返回 HTTP 200，
响应的 `data` 字段包含当前租户可见的统计数据。该接口是只读操作，不会修改额度、
订单、量表或用户资料。
