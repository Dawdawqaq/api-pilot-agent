# 第二阶段：轻量模式与失败边界

日期：2026-09-21。延续第一阶段同一业务沙箱与官方真实模型授权；模型为 `deepseek-flash`，没有用 stub 替代模型规划、修改或修复。

## 轻量化结果

新增 `lightweight` Profile，须放在 `local` / `docker` 之后，覆盖外部服务配置。

| 能力 | 核心模式 | 知识库模式 |
| --- | --- | --- |
| OpenAPI 导入、Schema 引用、Agent、HTTP 执行、报告 | 保留 | 保留 |
| MySQL 任务与审计存储 | 必需 | 必需 |
| 业务文档上传、分块、混合检索 | 明确禁用 | 启用 |
| Qdrant、MinIO | 不连接、不探测 | 必需 |
| Redis | 不需要 | 不需要 |

本轮将 Qdrant gRPC、HTTP 和 MinIO 地址都配置为 `127.0.0.1:1`，核心应用仍启动成功且健康状态 UP，随后真实模型业务链通过。不停止共享容器来制造故障；其他项目不受影响。

这是运行依赖的缩减，尚未拆分 Maven 模块或裁剪 SDK，也没有宣称内存、包体、启动耗时提升比例。已有知识库数据不会被删除；切回知识库模式后仍可使用。

`GET /api/v1/system/overview` 新增布尔字段 `data.knowledgeEnabled`，核心模式为 false，`qdrantCollection`、`objectStorageBucket` 为 null。响应仍为 `{code,message,data,requestId,timestamp}`。前端据此禁用业务文档文件选择，停止请求文档列表，OpenAPI 上传照常可用。

直接访问知识库文档接口时返回 HTTP 503、`KNOWLEDGE_503_001`，说明当前模式未启用，而非空指针或连接超时。Agent 的知识检索工具返回空证据；规划仍读取真实 OpenAPI 与 Schema，不能将此模式宣称为 RAG 检索质量验证。

## 失败边界验收

每个场景使用独立测试账号与社区。故障由真实模型通过计划修改接口注入，脚本先检查确实生成了指定故障，再确认执行；未按要求注入时会判定评测失败。`businessVerified` 表示符合场景预期，所以正确停止的任务状态应为 FAILED 或 CANCELLED。

| 场景 | 实际行为 | 独立核验 |
| --- | --- | --- |
| 登录与当前用户查询 | SUCCEEDED | 专属账号身份正确 |
| 登录、发帖、查询详情 | SUCCEEDED | 数据库 1 条帖子，独立详情字段正确 |
| 拒绝执行 | CANCELLED | 没有 HTTP 执行工具调用、没有帖子 |
| 修改后提交旧计划哈希 | HTTP 409，仍待确认；随后取消 | 旧批准未改变待确认状态、没有 HTTP 执行、没有帖子 |
| 最后一步引用缺失变量 | FAILED，明确报告 undefinedFixtureId | 整链预检失败，没有步骤响应、没有帖子 |
| 最后一步断言故意设为错误标题 | FAILED，ASSERTION_FAILED | 未重规划，保留错误断言；已有 1 条帖子，没有重复创建 |
| POST 创建成功后提取不存在的字段 | FAILED，EXECUTOR_409_003 | 未重规划，数据库确有 1 条帖子，提示核验写入副作用 |

以上七个场景各一次均符合预期，全部清理成功。每个场景的用途不同，不能混成“任务成功率 100%”。写请求丢响应的真实网络故障尚未注入，本轮只验证收到写响应后提取失败；超时不重试仍由确定性单元测试覆盖。

证据目录位于 `output/evaluation/`：

- `lightweight-normal-00627db243`：正常两条链。
- `lightweight-reject-f9ac24b24c`：拒绝。
- `lightweight-stale-0f6cb4d6e3`：旧确认。
- `lightweight-missing-variable-8de569bf1d`：缺失变量。
- `lightweight-assertion-cfe2249fb0`：断言不满足。
- `lightweight-write-review-f524e680d7`：写响应提取失败。

## 评测发现的审计顺序问题

`lightweight-repair-52ea7920b2` 中，模型已修正为 `$.data.title`，任务成功，数据库只有一条帖子，但验收脚本报告 false。

原因是工具审计接口按 `stepIndex, attempt` 排序。重规划再次确认后步骤编号可回退，导致后发生的成功执行排在旧失败前面；脚本直接取列表最后一项，误读了旧结果。

已将工具、模型调用审计排序改为 `createdAt, id`；验收脚本也显式按发生时间取最新记录，不依赖列表位置。原失败证据保留，不修改为成功。

修复后新一轮 `lightweight-repair-fixed-10b2479cbe` 独立验收通过：真实模型重规划 1 次，耗时 10.75 秒，15,590 Token，数据库 1 条帖子，提取标题与输入一致，清理成功。工具调用时间顺序已自动核验。这是第 8 个场景的最终验收；总共实际运行了 9 个场景实例，其中包含保留的旧验收误判。

## 使用与复现

本机已有共享 MySQL 时：

```powershell
mvn package
./scripts/start-real-local.ps1 -KeyFile '<外部密钥文件绝对路径>' -Port 18081
```

默认使用 `local,deepseek,lightweight`。需要知识库时，在 Qdrant 与 MinIO 就绪后增加 `-WithKnowledge`。两种模式访问同一本地数据库；同一端口只能启动一个实例。未配置稳定的 `SECRET_STORE_MASTER_KEY` 时仍使用临时密钥，不能承诺跨重启恢复敏感上下文。

没有共享基础设施时，可使用独立核心 Compose：

```powershell
$env:DB_PASSWORD = '<数据库用户密码>'
$env:MYSQL_ROOT_PASSWORD = '<数据库管理密码>'
$env:SECRET_STORE_MASTER_KEY = '<稳定保存的至少32字节随机密钥>'
$env:AI_API_KEY = '<真实模型密钥>'
docker compose -f docker-compose.core.yml up --build -d
docker compose -f docker-compose.core.yml ps
docker compose -f docker-compose.core.yml logs --tail 100 app
```

首条命令构建并启动应用与 MySQL，后两条查看状态和启动错误。默认访问 `http://localhost:28080`，不发布数据库端口。缺少变量时 Compose 立即报错；端口占用时设置其他 `APP_PORT`，不要停止其他项目。新 Compose 使用独立数据卷，不会自动继承共享库数据。本轮已通过配置校验，未另外启动这套容器。

真实失败场景复现（需要 luminous-dev 沙箱及本轮同等的数据出站授权）：

```powershell
$dockerExe = 'C:\Users\zx080\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
foreach ($scenario in @('reject', 'stale', 'missing-variable', 'assertion', 'write-review', 'repair')) {
    python scripts/evaluate_luminous.py --docker $dockerExe --label "core-$scenario" --scenario $scenario
    if ($LASTEXITCODE -ne 0) { break }
}
```

脚本限制测试接口白名单，核验容器身份，清理专属记录和缓存，并在不确定任务是否已终止时保留夹具、报告 ID。退出码非 0 时先检查证据，不可直接重复写入。模型额度或供应商错误不应计作业务约束通过。

## 验证范围与后续

`mvn -o package`：78 项测试通过，0 失败、0 错误、0 跳过。新增测试验证核心模式不读取知识库、组件缺失时返回明确错误、启用知识库但配置错误时不静默伪造检索结果。Python 编译和 JavaScript 语法检查通过；轻量 Compose 配置校验通过。浏览器端到端和完整历史公共基础设施套件未在本轮运行。

下一步优先补充确认等待超时、请求执行期间取消、重启后恢复和真实网络响应丢失场景。仍不建设多租户平台，不把本次小样本结果当作泛化质量指标。
