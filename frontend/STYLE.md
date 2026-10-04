# 页面风格：Quiet Ledger（安静账本）

界面维护时复用现有组件和样式原语。减少装饰，保持桌面交互、浅深主题和信息层次一致。

## 1. 一句话

**单色、细线、留白；颜色只表达状态。** 页面像一本干净的账本：用户的目标、执行的结果才是视觉重点，界面本身要"消失"。

判断标准：把页面截图去色后，仍然能看懂层次——说明层次来自排版和留白，而不是颜色。

## 2. 七条规则

| # | 规则 | 怎么做 |
|---|---|---|
| 1 | 灰阶为主 | 文字 `zinc-900/100`，次要 `zinc-500`。品牌蓝只出现在焦点环和"进行中"圆点 |
| 2 | 颜色 = 状态 | 成功绿 / 警告琥珀 / 失败红 / 进行中蓝，**只**用圆点+文字（`.status`），不用实心彩色块、彩色卡片底 |
| 3 | 细线代替阴影 | 1px 边框 + 留白做层次；不要 `shadow-*`、`ring-*`（焦点环除外） |
| 4 | 不装饰 | 无头像、无图标底色方块、无渐变、无 emoji。图标只在它**替代文字**时使用（按钮、折叠箭头） |
| 5 | 一页一个主按钮 | `.btn-primary`（黑/白反色）；其余全是 `.btn`（描边）。批准/拒绝这类对等操作都用 `.btn` |
| 6 | 字号克制 | 正文 `text-sm`；元信息 `.meta`；标题最大 `text-lg`；禁止 `text-[10px]` 之类任意值 |
| 7 | 复用原语 | 用 [style.css](src/style.css) 末尾的 `.card .status .meta .btn .btn-primary`，不要再手写一长串 utility |

## 3. 原语速查

| 类名 | 用途 | 说明 |
|---|---|---|
| `.card` | 容器 | 12px 圆角 + 1px 边框，无阴影无底色。内边距自己加 `p-4` |
| `.status` + `-ok/-warn/-err/-busy` | 状态 | 6px 圆点 + 灰字。不带变体 = 灰点（已取消/未知） |
| `.meta` | 元信息 | 12px 灰字，等宽数字。时间、ID、计数 |
| `.btn` | 普通操作 | 描边、8px 圆角 |
| `.btn-primary` | 页面唯一主操作 | 黑底白字（暗色反过来） |

## 4. 改造对照（最重要，请照做）

**状态徽章**
```html
<!-- ❌ 之前：彩色实心块 -->
<span class="px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200">已就绪</span>
<!-- ✅ 之后 -->
<span class="status status-ok">已就绪</span>
```

**结果 / 错误卡**
```html
<!-- ❌ 之前：整块彩色底 + 图标 + shadow-xs -->
<div class="rounded-xl border border-red-200 bg-red-50/80 p-3.5 shadow-xs"> … </div>
<!-- ✅ 之后：中性卡片，红色只在圆点上 -->
<div class="card p-4">
  <span class="status status-err">执行异常中止</span>
  <p class="mt-2 text-sm text-zinc-700 dark:text-zinc-300">{{ message }}</p>
</div>
```

**列表项（历史、版本、文档）**
```html
<!-- ❌ 之前：每项一张带边框、带 ring 的卡片 -->
<!-- ✅ 之后：无边框行，选中只换浅灰底，状态是圆点 -->
<button class="w-full text-left px-2.5 py-2 rounded-lg hover:bg-zinc-100 dark:hover:bg-zinc-900"
        :class="selected && 'bg-zinc-200/70 dark:bg-zinc-800'">
  <p class="text-sm line-clamp-2">{{ title }}</p>
  <div class="mt-1 flex justify-between"><span class="status status-ok">成功</span><span class="meta font-mono">#12</span></div>
</button>
```

**折叠标题栏**：不要灰色底，不要"展开/收起"文字；一个 `ChevronDown`，折叠时 `-rotate-90`。

**HTTP 方法标签（GET/POST…）**：不要彩色徽章，用 `font-mono font-medium` 的普通文字，必要时 `text-zinc-700`。

## 5. 暗色模式

背景 `zinc-950`，卡片边框 `zinc-800`，选中/悬停底 `zinc-800/900`。**对比度优先于"好看"**：次要文字最浅用 `zinc-400`，不要更暗。

## 6. 已完成的示范与重构

- [ChatMessage.vue](src/components/workbench/ChatMessage.vue)：用户气泡、状态行、取消按钮、执行计划、结果总结、错误卡、工具调用日志、断言行与模型审计
- [TaskHistoryDrawer.vue](src/components/workbench/TaskHistoryDrawer.vue)：历史项（无边框行 + 状态圆点）
- [ConfirmationGateCard.vue](src/components/workbench/ConfirmationGateCard.vue)：干净 `.card` + 琥珀状态点 + 统一 `.btn` / `.btn-primary`
- [OpenApiView.vue](src/components/openapi/OpenApiView.vue)：版本列表无边框行、中性分段控制、普通单色 HTTP 方法标签与细线参数表格
- [ReportsView.vue](src/components/reports/ReportsView.vue)：报告列表无边框行、状态圆点、干净步骤流卡片与 Junit 单主按钮
- [KnowledgeView.vue](src/components/knowledge/KnowledgeView.vue)：文档列表无边框行、检索测试卡片与单主按钮
- [SettingsDrawer.vue](src/components/settings/SettingsDrawer.vue)：LLM 设置中性选项卡、发丝边框与状态指示
- [SimpleModal.vue](src/components/common/SimpleModal.vue)：克制圆角、细线边框、去扩散阴影
- [ChatInput.vue](src/components/workbench/ChatInput.vue)：克制圆角、去多余阴影、黑白主发送按钮
- [AppHeader.vue](src/components/header/AppHeader.vue)：纯文字无饰品牌名、状态点模式指示器

## 7. 自检清单（按修改范围重新核验）

- [x] 页面里有没有 `bg-emerald/red/amber/purple/blue-*` 的大面积底色？（无，颜色严格只用于 `.status` 状态圆点）
- [x] 有没有 `shadow-*`、`ring-*`（焦点环除外）、`border-2`？（无扩散阴影与厚边框，统一 1px 发丝边框）
- [x] 有没有 `text-[Npx]` 任意字号？（无，严格使用 `.meta`(12px)、`text-sm`(14px / 22px)、`text-base`(15px)）
- [x] 同一视图里主按钮是否只有一个？（是，严格执行单一 `.btn-primary`）
- [x] 暗色模式下次要文字是否仍清晰？（是，暗色模式上次要文字统一保持在 `zinc-400` 以上）
- [x] 业务字段是否仍与后端契约一一对应？（是，完全保留后端字段，零虚构）


## 8. 桌面文字与元信息约定（2026-10-03）

- 正文与主导航使用 14px / 22px；等宽栈在通用 monospace 前提供微软雅黑与苹方中文回退，不加载 Web 字体。
- `.meta` 在亮色使用 zinc-500，暗色保持 zinc-400。主导航只保留文字，不叠加装饰图标。
- 工具调用和历史任务共用中文状态映射。模型调用只呈现“模型名称 · tokens · 耗时”的 `.meta` 文本；当前生效模型在设置抽屉查看。
- 初始变量使用输入框底栏的文字折叠按钮；环境选择器显示去掉末尾括号部分的短名，选项与输入底栏保留完整名。
- 总结与错误同时存在时共用一张结果卡，错误状态决定标题，完整保留总结、错误码与错误详情。
- 保留对话内容到输入框之间的留白；任务目标与计划原文不做风格替换。
