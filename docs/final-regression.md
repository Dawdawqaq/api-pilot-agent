# 最终回归、候选召回与运行边界

验收日期：2026-09-21。模型为 `deepseek-flash`，被测服务为隔离的 Luminous 开发沙箱。

## 本轮收尾内容

### 写入结果未知成为独立终态

写请求超时、连接中断、响应过大，或已经收到写响应但后续解析失败时，执行器返回 `EXECUTOR_409_003`。Agent 顶层现在进入 `NEEDS_REVIEW`，不再改写为普通的 `FAILED / AGENT_400_002`。

该状态具有以下行为：

- 作为终态停止自动恢复、重规划和重放；
- 事件流写入 `MANUAL_REVIEW_REQUIRED`；
- 页面显示“需要核验”，提示先检查远端业务结果；
- 报告保留终止阶段、原始错误码、请求指纹相关审计、脱敏请求响应和已有步骤；
- JUnit XML 将无 HTTP 步骤的待核验任务导出为失败用例，避免空测试集被误认为通过。

真实 `write-review` 场景中，帖子写入一次后响应字段提取失败。任务 ID `2101934977330552835`，最终状态和报告状态均为 `NEEDS_REVIEW`，远端独立核验只有一条帖子，系统没有自动重放。

### 服务端任务准入

任务创建现在先由数据库统计非终态任务，再在单实例临界区内完成容量检查和持久化：

- 全局最多 4 个非终态任务；
- 单项目最多 2 个非终态任务；
- 本机执行线程池最多 4 个线程、队列容量 8；
- 超过项目或全局上限返回 HTTP 429、`AGENT_429_003`；
- 线程池拒绝已经持久化的任务时，任务进入明确失败终态并生成报告，不会停留在 `RECEIVED`；
- 实验台历史摘要显示运行、排队和待确认数量。

该机制面向当前单实例、单用户定位。它控制误操作和模型费用，不宣称为分布式并发调度。

## 候选召回回归

固定数据集 `api-pilot-evaluation-v1` 包含 3 份 OpenAPI、36 个接口、50 条中文目标和 67 个期望 Operation，其中 35 条是单接口目标。OpenAPI 只提供英文 path/operationId，部分接口没有中文摘要，用来验证中文目标到英文契约的候选召回。

第一次运行暴露出旧候选器只能依赖同语言词面匹配。随后增加可解释的中英文业务概念归一化、操作意图、读写方法及列表/详情形态评分。词典只覆盖常见 API 业务词，不调用额外模型或向量服务。

| 指标 | 修复前 | 最终结果 |
| --- | ---: | ---: |
| 单接口 Top-1 Accuracy | 5.71% | 100.00% |
| MRR | 18.06% | 100.00% |
| Operation Recall@3 | 22.39% | 94.03% |
| Operation Recall@12 | 61.19% | 100.00% |
| Case Complete Recall@12 | 56.00% | 100.00% |

测试会生成 `target/evaluation/candidate-recall.json`，同时设置最低回归门槛：Top-1 不低于 95%，Recall@3 不低于 90%，Recall@12 和 Case Complete Recall@12 必须为 100%。

这些数字只适用于仓库内固定数据集。业务词典与样本属于同一项目资产，因此不能外推为任意行业 OpenAPI 的召回率。

## 最终真实模型回归

脚本分别创建独立账号、项目和业务夹具，执行后核验数据库业务状态并清理本轮 Luminous 数据。六个场景为：

| 场景 | 预期终态 | 最终终态 | 业务核验 | Tokens | 耗时 |
| --- | --- | --- | --- | ---: | ---: |
| 登录并读取当前用户 | `SUCCEEDED` | `SUCCEEDED` | 通过 | 4,150 | 3.11 s |
| 登录、创建帖子、查询详情 | `SUCCEEDED` | `SUCCEEDED` | 通过 | 4,499 | 4.16 s |
| 提取失败后有限重规划 | `SUCCEEDED` | `SUCCEEDED` | 通过，写入未重复 | 15,051 | 9.61 s |
| 错误业务断言 | `FAILED` | `FAILED` | 通过，断言未被模型删除 | 9,536 | 7.06 s |
| 写响应解析失败 | `NEEDS_REVIEW` | `NEEDS_REVIEW` | 通过，写入一次且未重放 | 9,502 | 6.55 s |
| 后续步骤缺少变量 | `FAILED` | `FAILED` | 通过，执行前拦截且未写入 | 9,432 | 6.36 s |

汇总指标：

| 指标 | 结果 |
| --- | ---: |
| Expected Outcome Pass Rate | 100%（6/6） |
| Valid Plan Rate | 100%（6/6） |
| Negative Case Safe Stop Rate | 100%（3/3） |
| Report Generation Rate | 100%（6/6） |
| 原始任务 `SUCCEEDED` 比例 | 50%（3/6） |
| 总模型 Tokens | 52,170 |
| 平均 Tokens | 8,695 |
| 平均任务耗时 | 6.14 s |
| P95 任务耗时 | 9.61 s |

原始成功比例只有 50% 是预期结果：三个负向场景应当失败或进入人工核验。简历和面试应使用 Expected Outcome Pass Rate，不能把所有终态强行描述成任务成功率。

完整本机证据位于 Git 忽略目录 `output/evaluation/final-20260921T072131Z/summary.json`，文件 SHA-256 为 `F7A5FE71F1A41B8F8502A4C440F262CDC70F3E4FCF21143FFCC1B728F85B82EB`。每个场景只运行一次，因此这些数据是最终版本回归记录，不是统计意义上的长期成功率或 SLA。

全量自动化回归共 41 个测试套件、93 项测试，失败 0、错误 0、跳过 0。JavaScript 语法、Python 评测脚本编译和 `git diff --check` 均通过；后者只输出 Windows 工作区的 LF/CRLF 提示。

## 复现命令

```powershell
mvn "-Dtest=OpenApiCandidateRecallEvaluationTest" test

$dockerExe = 'C:\Users\zx080\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
python scripts/evaluate_final_regression.py --docker $dockerExe
```

真实模型回归要求 ApiPilot 已使用 `local,deepseek,lightweight` 启动，Luminous 开发沙箱运行在 `127.0.0.1:8080`。脚本只清理本轮带随机专属账号的业务数据；失败时保留无法确认终止的夹具，避免清理与执行竞态。
