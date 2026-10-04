# 个人使用与维护

适用于 Windows 上的本地单用户 ApiPilot。通用 Docker 部署见根目录 README，本页说明 Windows 启动工具及本机数据维护。

## 日常启动、停止和日志

首次需要 Java 21、Maven、Node.js/npm 和 Docker Desktop。日常使用新版只需一个应用地址；Vite 是前端开发工具，日常不用另开。

1. 启动共享开发设施。使用设施仓库已有的 DocHelper 配置，不删除其他项目的数据。

   ```powershell
   cd ..\dev-infra
   .\scripts\Start-DevInfra.ps1 -Profile dochelper
   ```

2. 构建并启动完整模式。首次缺少产物会自动构建；源码变化后使用 `-Build`。已有应用先停止，Windows 会锁定运行中的 jar。

   ```powershell
   cd <仓库根目录>
   .\scripts\app.ps1 start -Mode Full -Build
   ```

   Full 启用业务文档存储和向量检索，连接本项目 MySQL、MinIO、Qdrant。脚本只读取设施 `.env` 中的 DocHelper 凭据，保留调用者显式设置的环境变量。稳定主密钥使用当前 Windows 用户的 DPAPI 文件保存，不在终端打印。

3. 打开 `http://127.0.0.1:18081/`。当前 frontend 构建产物与 API 打入同一个 Spring Boot jar，不保留旧界面回退。普通启动复用上次构建。

   ```powershell
   .\scripts\app.ps1 start -Mode Full
   .\scripts\app.ps1 status
   ```

4. 随时查看后台日志。默认跟踪最后 80 行，按 Ctrl+C 只退出跟踪，应用继续运行。

   ```powershell
   .\scripts\app.ps1 logs
   .\scripts\app.ps1 logs -NoFollow -Tail 200
   ```

5. 停止应用。脚本校验 PID、启动时间和本项目 jar，只操作自己的应用，不停止共享 Docker 容器。

   ```powershell
   .\scripts\app.ps1 stop
   ```

Windows 的 Stop-Process 是进程终止。操作前尽量等任务结束；已发出的远端请求不能撤回，重启恢复由既有租约机制处理，写入结果不确定须核验远端结果。

仅做核心 API 测试时可以切换 Core：

```powershell
.\scripts\app.ps1 stop
.\scripts\app.ps1 start -Mode Core
```

Core 使用 `local,stub,lightweight`，关闭知识库和对象存储，不要求 Qdrant/MinIO；保存的真实对话配置仍可生效。stub 是启动时的开发回退配置，是否调用真实供应商以设置里的当前模型为准。完整备份仍需 MySQL 和 MinIO，以保存已有资料。

单独构建或改端口：

```powershell
.\scripts\app.ps1 build
.\scripts\app.ps1 start -Mode Full -Port 18082
```

改模式、端口或重建前先停止应用。status 使用启动记录的端口。端口被占用时脚本拒绝启动，不停止占用者，先确认进程或使用 -Port。

IDEA Run 仍可开发，它直接编译运行源码；这里是构建 jar 后由 Java 启动，适合日常使用。前端热更新见 [前端 README](../frontend/README.md)，IDEA/Vite 与后台脚本选一种后端启动方式。构建失败先看 Maven/npm 输出，启动未就绪先看 status 和 logs；Docker 未启动或凭据不匹配会导致连接失败。脚本不修改 UAC、系统权限或终止共享设施。

## 首次使用检查

新项目先创建环境、导入 OpenAPI，检查对话模型和项目资料策略。工作台“开始前检查”显示项目、模型、当前接口版本、资料策略、环境的实际配置；缺项直达对应页面。知识库保持可选，归档项目不能新建任务或执行回放。

检查通过只表示配置及访问策略满足条件。模型连接尚未验证、目标服务连通性尚未探测会明确写出；检查不调用模型或发送目标 HTTP 请求，地址校验可能解析 DNS。真正的模型连接测试由用户在设置中主动执行。

提交前重新读取检查并核对项目/环境是否改变，避免旧检查用于新上下文。实际执行时后端仍验证计划和请求。

## 独立嵌入与重建索引

进入“业务知识库 → 配置嵌入”。对话模型和 Embedding 独立配置，保存 DeepSeek 对话 Key 不等于启用真实语义检索。当前四维确定性开发向量只验证流程，没有测量真实语料召回率。

API 嵌入填写基础地址、模型名称及独立 Key。基础地址可以带 `/v1` 等版本部分，不填写完整 `/embeddings` 地址；供应商须支持 OpenAI 兼容嵌入协议。

输出维度可选。留空只识别返回维度，不发送 dimensions 参数；显式填写时发送参数并核对结果。识别维度与请求参数分别保存，刷新和重启不会把自动结果误当成请求参数。

“测试连接”只发送固定探测文本，不发送业务文档、不保存配置，可能产生少量费用。Key 留空仅沿用同地址已保存的嵌入 Key，换地址须重新填写；Key 加密保存，只返回是否配置，不进入浏览器存储。

“重建索引并启用配置”处理全部项目已索引的业务切片，包括回收站项目保留的文档。API 模式必须勾选允许发送这些资料。重建期间暂停知识库写入和检索；失败文档仍通过原“重试索引”处理，不假装已索引。

流程使用独立新集合：探测维度、批量写入全部切片，成功后事务保存配置/加密 Key 并切换读取集合。构建失败继续用旧索引，避免提前清空。已替换的本项目生成集合会清理，原始基线集合用于回退。删除失败或中断留下的未启用集合登记在本项目配置中，页面显示数量并提供“清理未使用索引”，只处理明确登记的本项目集合。

锁为单应用进程内读写锁，适合本地单实例，不宣称多实例一致性。真实语义效果须用供应商和业务评测集验证；本机合成协议测试只证明协议、维度和切换流程。

## 回放与数据保留

“契约与回放”按项目列出真实失败样本，支持搜索、游标加载及执行筛选。失败报告“查看失败回放”直达同一执行，无需查数据库取 ID。请求、原始断言和本次断言结构化显示，脱敏 JSON 可展开。

只读回放实际发送请求，结果可能仍失败；负向用例只生成资料，不自动执行。写请求需要回到工作台经过人工确认，写入结果不确定须先核验远端结果。

“项目与环境 → 数据与保留”显示任务、报告、执行、失败样本、接口历史、文档和切片数量。文档大小来自文件元数据，不能当作数据库/向量库/磁盘实际占用；清理后 MySQL 表空间文件不一定缩小。

| 操作 | 数据行为 |
| --- | --- |
| 归档 | 项目仍可见，保留历史；禁止新任务和执行，可编辑为启用 |
| 移入回收站 | 隐藏项目，资料、环境和历史保留；编码保留，可搜索恢复 |
| 清理旧历史 | 永久删除当前项目预览确认的旧终态历史及关联审计、回放、运行密钥；保留接口、环境、业务文档和模型配置 |
| 备份恢复 | 替换整个 DocHelper 数据库，还原备份对象和主密钥；不是单项目撤销 |

进行中任务阻止归档和回收。清理保留天数 7—3650，每批最多 500 个任务和 500 次执行；保留进行中、仍有租约、NEEDS_REVIEW 任务，不删除保留任务/报告引用的执行。OpenAPI 历史和整个项目物理删除不在此清理范围。

清理先预览再输入完整项目编码。预览绑定项目、有效 5 分钟，新预览覆盖旧预览；范围变化或应用重启后重新预览。服务器事务中重新计算范围，核对令牌/编码，按外键依赖顺序删除，失败回滚。页面不能撤销，保留证据先备份。

## 备份、校验与恢复

备份含 dochelper SQL、dochelper-files 原始对象、当前用户 DPAPI 保护的主密钥和 SHA-256 清单。Qdrant 是可重建的派生索引，Redis 不是此备份的持久业务来源，两者不进入备份。

等待任务结束并停止应用，再备份，避免资料在导出时变化：

```powershell
cd <仓库根目录>
.\scripts\app.ps1 stop
.\scripts\backup.ps1 backup
.\scripts\app.ps1 start -Mode Full
```

成功路径打印在终端，格式 `.local-notes/backups/时间戳`；带 -pending 的目录表示未完成。工具使用 DocHelper MySQL 账号和固定 MinIO 桶，不读取其他命名空间。

可随时校验，替换下面时间戳为实际成功目录：

```powershell
.\scripts\backup.ps1 verify -BackupPath .\.local-notes\backups\<备份时间戳>
```

需要恢复历史时才执行以下命令，会替换整个 DocHelper 数据库，须停止应用并明确指定 dochelper：

```powershell
.\scripts\app.ps1 stop
.\scripts\backup.ps1 restore -BackupPath .\.local-notes\backups\<备份时间戳> -Confirmation dochelper
.\scripts\app.ps1 start -Mode Full
```

恢复前校验 SQL、归档、密钥文件和对象逐项内容，确认当前 Windows 用户可以解密。恢复先写对象，再导入 SQL 和恢复主密钥；不删除备份之外已有对象。MySQL/MinIO 没有分布式事务，中断后检查输出，从同一完整备份重试。恢复后设置索引待重建标记，阻止旧向量检索/写入，显式重建成功才解除。

DPAPI 绑定当前 Windows 用户和电脑，这套脚本覆盖本机同用户恢复，不提供跨机器主密钥迁移。显式 SECRET_STORE_MASTER_KEY 优先于本地文件，恢复时须与备份主密钥一致。备份含业务 SQL 和文档，作为项目私有资料保存。

jar 和前端构建覆盖固定输出，node_modules 是依赖目录，不会每次启动新增一套。日志 `logs/dochelper.log` 单文件 10 MB 滚动，压缩归档保留 7 天、总量上限 100 MB，在滚动时清理；启动输出每次覆盖。数据库与文档随使用累积，按保留规则维护。备份每次新建，暂不自动删除，自行保留需要的恢复点；确认目录用途后再删除旧备份。

## 能力与工程边界

接口字段、参数、返回值和异常见 [接口契约](api.md)。重建由 KnowledgeIndexService 管理，清理由 ProjectDataService/Repository 事务核对，备份工具独立运行，不启动 Agent。

本机备份脚本默认使用 dev-infra-mysql-1 容器、DocHelper 数据库账号和固定 dochelper-files 桶；其他本机部署可通过 DOCHELPER_MYSQL_CONTAINER 指定自己的 MySQL 容器，数据库账号必须为 dochelper。Compose 服务账号通常也是 dochelper。不是跨平台或跨机器灾备工具，使用前保留合适的恢复点。

Windows 启动脚本默认从仓库相邻的 dev-infra/.env 读取 DocHelper 专用凭据，可通过 DOCHELPER_DEV_INFRA 指定设施目录；不访问其他项目数据库。调用者显式环境变量优先，不自动加载仓库 .env。没有共享设施时直接设置自己的连接环境变量即可。

单实例受控重建、加密密钥持久化及生成失败保留旧索引已在本机验证；这不证明供应商语义质量。备份完整性与恢复前验证通过，没有覆盖现有用户数据库进行恢复演练。MySQL 与 MinIO 之间没有分布式事务，恢复范围与中断重试的边界按上文处理。
