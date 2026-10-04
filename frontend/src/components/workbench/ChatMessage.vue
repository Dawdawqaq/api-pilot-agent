<script setup>
import { ref, computed } from 'vue'
import { ChevronDown, XCircle, AlertCircle, Loader2, Check, StopCircle } from 'lucide-vue-next'
import ConfirmationGateCard from './ConfirmationGateCard.vue'
import Tooltip from '../common/Tooltip.vue'
import { presentSteps } from '../../domain/task-state'
import { presentStatus } from '../../domain/status-presentation'
import { taskRecovery } from '../../domain/task-recovery'

const props = defineProps({
  readOnly: { type: Boolean, default: false },
  task: {
    type: Object,
    required: true
  },
  actionLoading: {
    type: Boolean,
    default: false
  }
})

const displayedSteps = computed(() => presentSteps(props.task))
const recovery = computed(() => taskRecovery(props.task))
const hasError = computed(() => Boolean(props.task.errorMessage || props.task.errorCode))
const resultTitle = computed(() => !hasError.value ? '测试结果总结' : props.task.status === 'NEEDS_REVIEW' ? '执行结果需核验' : props.task.status === 'CANCELLED' ? '执行已取消' : '执行异常中止')
const emit = defineEmits(['confirm-approve', 'confirm-reject', 'modify-plan', 'cancel-task', 'reuse-goal', 'open-settings', 'open-projects'])

// 展开/收起内嵌工具调用的详细响应 JSON
const expandedToolCalls = ref({})

const toggleToolCall = (id) => {
  expandedToolCalls.value[id] = !expandedToolCalls.value[id]
}

// 展开/收起计划详情
const showPlanDetails = ref(true)

// 快速复制 Payload (支持非安全 HTTP 上下文兼容回退)
const copiedKey = ref('')
const copyJson = async (key, data) => {
  try {
    const text = typeof data === 'string' ? data : JSON.stringify(data, null, 2)
    let success = false
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      success = true
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.focus()
      ta.select()
      success = document.execCommand('copy')
      document.body.removeChild(ta)
    }
    if (success) {
      copiedKey.value = key
      setTimeout(() => {
        if (copiedKey.value === key) copiedKey.value = ''
      }, 1800)
    }
  } catch (err) {
    console.error('复制失败', err)
  }
}

// 时间格式化辅助
const formatTime = (ts) => {
  if (!ts) return ''
  if (typeof ts === 'string' && ts.includes('T')) {
    const parts = ts.split('T')
    return `${parts[0]} ${parts[1]?.slice(0, 8)}`
  }
  return ts
}

// 任务总耗时计算
const taskDuration = computed(() => {
  if (props.task.startedAt && props.task.completedAt) {
    try {
      const start = new Date(props.task.startedAt).getTime()
      const end = new Date(props.task.completedAt).getTime()
      if (!isNaN(start) && !isNaN(end) && end >= start) {
        const diff = end - start
        return diff < 1000 ? `${diff}ms` : `${(diff / 1000).toFixed(1)}s`
      }
    } catch (e) {}
  }
  return null
})

const statusBadge = computed(() => ({
  text: {RECEIVED:'正在规划...',RETRIEVING:'正在规划...',PLANNING:'正在规划...',EXECUTING:'正在执行...',OBSERVING:'正在执行...',REPLANNING:'正在执行...',REPORTING:'生成报告中...',WAITING_CONFIRMATION:'等待人工确认',SUCCEEDED:'测试通过',NEEDS_REVIEW:'需人工核验',FAILED:'执行失败',CANCELLED:'已取消'}[props.task.status] || '未知状态',
  spinning: ['RECEIVED','RETRIEVING','PLANNING','EXECUTING','OBSERVING','REPLANNING','REPORTING'].includes(props.task.status)
}))

const toolAssertions = call => (call.response?.steps || []).flatMap(step =>
  (step.assertions || []).map(assertion => ({...assertion,stepName:step.name})))

const isTerminal = computed(() => {
  return ['SUCCEEDED', 'NEEDS_REVIEW', 'FAILED', 'CANCELLED'].includes(props.task.status)
})

const statusTone = computed(() => {
  const s = props.task.status
  if (s === 'SUCCEEDED') return 'status-ok'
  if (s === 'FAILED') return 'status-err'
  if (['WAITING_CONFIRMATION', 'NEEDS_REVIEW'].includes(s)) return 'status-warn'
  if (['CANCELLED'].includes(s)) return ''
  return 'status-busy'
})
</script>

<template>
  <div class="space-y-6 pb-6 border-b border-zinc-100 dark:border-zinc-800/80 last:border-none">

    <!-- 1. 用户目标：右对齐浅灰气泡，无头像 -->
    <div class="flex justify-end">
      <div class="max-w-2xl rounded-2xl bg-zinc-100 dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 px-4 py-2.5">
        <p class="text-sm whitespace-pre-wrap break-words">{{ task.goal }}</p>
        <div class="mt-1.5 meta text-right font-mono">Task #{{ task.id }} · {{ formatTime(task.createdAt) }}</div>
      </div>
    </div>

    <!-- 2. Agent 响应：整宽、无头像、无背景，状态用圆点 + 文字 -->
    <div>
      <div class="flex-1 min-w-0">
        <div class="flex items-center justify-between gap-3 mb-3">
          <div class="flex items-center gap-3 flex-wrap">
            <span class="status" :class="statusTone">
              <Loader2 v-if="statusBadge.spinning" class="w-3 h-3 animate-spin" />
              <span>{{ statusBadge.text }}</span>
            </span>
            <span v-if="task.replanCount > 0" class="meta">重规划 ×{{ task.replanCount }}</span>
            <span v-if="task.modificationCount > 0" class="meta">修订 ×{{ task.modificationCount }}</span>
            <span v-if="task.cancelRequested && !isTerminal" class="meta">正在取消中…</span>
          </div>

          <button
            v-if="!isTerminal"
            type="button"
            class="btn"
            @click="emit('cancel-task', task.id)"
          >
            <StopCircle class="w-3.5 h-3.5" />
            <span>取消任务</span>
          </button>
        </div>

        <!-- 规划中骨架屏提示 (当任务处于规划阶段且计划步骤尚未返回时展示，防布局抖动) -->
        <div
          v-if="(!task.plan || task.plan.length === 0) && ['RECEIVED', 'RETRIEVING', 'PLANNING'].includes(task.status)"
          class="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50/60 dark:bg-zinc-900/40 space-y-3 mb-3 animate-pulse"
        >
          <div class="flex items-center gap-2 text-xs font-medium text-zinc-600 dark:text-zinc-300">
            <Loader2 class="w-3.5 h-3.5 animate-spin text-brand-600 dark:text-brand-400" />
            <span>正在检索接口契约并编排多步测试执行链路...</span>
          </div>
          <div class="space-y-2">
            <div class="h-3 bg-zinc-200 dark:bg-zinc-800 rounded w-4/5"></div>
            <div class="h-3 bg-zinc-200 dark:bg-zinc-800 rounded w-3/5"></div>
          </div>
        </div>

        <!-- 计划：一张 .card，标题行无底色，步骤之间只用细分割线 -->
        <div v-if="task.plan && task.plan.length > 0" class="card mb-3 overflow-hidden">
          <button type="button" :aria-expanded="showPlanDetails"
            class="w-full text-left px-4 py-2.5 flex items-center justify-between hover:bg-zinc-50 dark:hover:bg-zinc-800/50 transition-colors focus-ring"
            @click="showPlanDetails = !showPlanDetails"
          >
            <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100">执行计划 <span class="meta">· {{ task.plan.length }} 步</span></span>
            <ChevronDown class="w-4 h-4 text-zinc-400 transition-transform" :class="showPlanDetails ? '' : '-rotate-90'" />
          </button>

          <ol v-if="showPlanDetails" class="px-4 border-t border-zinc-100 dark:border-zinc-800 divide-y divide-zinc-100 dark:divide-zinc-800">
            <li v-for="step in displayedSteps" :key="step.index" class="py-2.5 flex items-start gap-3">
              <!-- 状态只用图标颜色表达；未确认时显示序号 -->
              <span class="mt-0.5 w-4 shrink-0 text-center" :aria-label="step.displayStatus === 'success' ? '已通过' : step.displayStatus === 'failed' ? '未通过' : step.displayStatus === 'confirmation' ? '待确认' : '结果尚未确认'">
                <Check v-if="step.displayStatus === 'success'" class="w-4 h-4 text-emerald-600" />
                <XCircle v-else-if="step.displayStatus === 'failed'" class="w-4 h-4 text-red-600" />
                <AlertCircle v-else-if="step.displayStatus === 'confirmation'" class="w-4 h-4 text-amber-600" />
                <span v-else class="meta">{{ step.displayIndex }}</span>
              </span>
              <div class="min-w-0 flex-1">
                <p class="text-sm text-zinc-800 dark:text-zinc-200 break-words">{{ step.objective }}</p>
                <p v-if="step.request" class="mt-0.5 meta font-mono truncate">
                  <span class="text-zinc-700 dark:text-zinc-300 font-medium">{{ step.request.method }}</span> {{ step.request.path }}
                </p>
              </div>
            </li>
          </ol>
        </div>

        <!-- 3. 安全闸门卡片 (仅在 WAITING_CONFIRMATION 状态呈现) -->
        <ConfirmationGateCard
          v-if="task.status === 'WAITING_CONFIRMATION' && task.confirmation"
          :confirmation="task.confirmation"
          :current-step-data="task.plan?.find(s => s.index === task.confirmation.stepIndex)"
          :loading="actionLoading"
          :read-only="readOnly"
          @approve="(data) => emit('confirm-approve', task.id, data)"
          @reject="(data) => emit('confirm-reject', task.id, data)"
          @modify="(inst) => emit('modify-plan', task.id, inst)"
        />

        <!-- 总结与错误共用容器，保留所有字段且按状态选择标题。 -->
        <div v-if="task.resultSummary || hasError" class="card p-4 mb-3" data-task-result>
          <div class="flex items-center justify-between gap-3">
            <span class="status" :class="hasError && !['NEEDS_REVIEW','CANCELLED'].includes(task.status) ? 'status-err' : statusTone">{{ resultTitle }}</span>
            <span v-if="taskDuration" class="meta font-mono">总耗时 {{ taskDuration }}</span>
          </div>
          <p v-if="task.resultSummary" class="mt-2 text-sm text-zinc-700 dark:text-zinc-300 whitespace-pre-wrap break-words">{{ task.resultSummary }}</p>
          <p v-if="task.errorCode" class="mt-2 meta font-mono break-words">{{ task.errorCode }}</p>
          <p v-if="task.errorMessage" class="mt-2 text-sm text-zinc-700 dark:text-zinc-300 whitespace-pre-wrap break-words">{{ task.errorMessage }}</p>
          <template v-if="recovery">
            <p class="mt-3 text-sm text-zinc-600 dark:text-zinc-300">{{ recovery.message }}</p>
            <div class="mt-3 flex flex-wrap gap-2">
              <button v-if="recovery.reuse" class="btn" :disabled="readOnly || actionLoading" @click="emit('reuse-goal')">复用目标到草稿</button>
              <button v-if="recovery.settings" class="btn" :disabled="readOnly" @click="emit('open-settings')">检查模型与规则</button>
              <button v-if="recovery.projects" class="btn" :disabled="readOnly" @click="emit('open-projects')">检查执行环境</button>
            </div>
          </template>
        </div>

        <!-- 6. 纯内嵌折叠的工具调用与 HTTP 执行结果 (AgentToolCallResponse) -->
        <div v-if="task.toolCalls && task.toolCalls.length > 0" class="space-y-2 mt-4">
          <div class="text-sm font-medium text-zinc-900 dark:text-zinc-100">
            执行日志与 HTTP 响应追踪
          </div>

          <div
            v-for="call in task.toolCalls"
            :key="call.id"
            class="card overflow-hidden text-xs"
          >
            <!-- 头部概览栏 -->
            <button type="button" :aria-expanded="!!expandedToolCalls[call.id]"
              class="w-full text-left px-4 py-2.5 flex items-center justify-between hover:bg-zinc-50 dark:hover:bg-zinc-800/50 transition-colors focus-ring"
              @click="toggleToolCall(call.id)"
            >
              <div class="flex items-center gap-2.5 min-w-0">
                <ChevronDown class="w-4 h-4 text-zinc-400 transition-transform shrink-0" :class="expandedToolCalls[call.id] ? '' : '-rotate-90'" />
                <span class="font-mono text-sm text-zinc-900 dark:text-zinc-100 font-medium truncate">
                  {{ call.toolName }}
                </span>

              </div>

              <div class="flex items-center gap-3 shrink-0">
                <span v-if="call.durationMs" class="meta font-mono">{{ call.durationMs }}ms</span>
                <span class="status" :class="presentStatus(call.status).tone">
                  {{ presentStatus(call.status).text }}
                </span>
              </div>
            </button>

            <!-- 内嵌展开工具实际响应，HTTP 工具返回整条调用链而非单个响应体。 -->
            <div v-if="expandedToolCalls[call.id]" class="px-4 py-3 border-t border-zinc-100 dark:border-zinc-800 space-y-3">
              <p v-if="call.errorCode || call.errorMessage" class="text-sm text-red-600 dark:text-red-400">{{ call.errorCode }} {{ call.errorMessage }}</p>
              <div v-for="step in call.response?.steps || []" :key="step.stepIndex" class="meta">
                步骤 {{ step.stepIndex + 1 }} · {{ step.name }} · {{ step.responseStatus ? `HTTP ${step.responseStatus}` : '未收到 HTTP 响应' }} · {{ step.durationMs }}ms
              </div>
              <!-- 断言列表 -->
              <div v-if="toolAssertions(call).length" class="space-y-1.5">
                <div class="meta font-medium">断言校验:</div>
                <div class="space-y-1">
                  <div
                    v-for="(ast, idx) in toolAssertions(call)"
                    :key="idx"
                    class="flex items-center gap-2 text-xs font-mono text-zinc-700 dark:text-zinc-300"
                  >
                    <span class="status" :class="ast.passed ? 'status-ok' : 'status-err'"></span>
                    <span class="text-zinc-500 font-medium">[{{ ast.type }}]</span>
                    <span class="break-words">{{ ast.stepName ? `${ast.stepName}：` : '' }}{{ ast.message || ast.type }} · 预期 {{ ast.expected }} · 实际 {{ ast.actual }}</span>
                  </div>
                </div>
              </div>

              <!-- 请求与响应 Payload 预览 (带快速复制按钮) -->
              <div class="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs font-mono">
                <div>
                  <div class="flex items-center justify-between mb-1">
                    <span class="meta">Request Payload</span>
                    <button
                      type="button"
                      class="meta hover:text-zinc-900 dark:hover:text-zinc-100 transition-colors"
                      @click="copyJson(`req-${call.id}`, call.request)"
                    >
                      {{ copiedKey === `req-${call.id}` ? '已复制' : '复制' }}
                    </button>
                  </div>
                  <pre class="p-2.5 rounded-lg bg-zinc-50 dark:bg-zinc-950 text-zinc-800 dark:text-zinc-200 overflow-x-auto max-h-48 border border-zinc-200 dark:border-zinc-800">{{ JSON.stringify(call.request, null, 2) }}</pre>
                </div>
                <div>
                  <div class="flex items-center justify-between mb-1">
                    <span class="meta">工具响应</span>
                    <button
                      type="button"
                      class="meta hover:text-zinc-900 dark:hover:text-zinc-100 transition-colors"
                      @click="copyJson(`res-${call.id}`, call.response)"
                    >
                      {{ copiedKey === `res-${call.id}` ? '已复制' : '复制' }}
                    </button>
                  </div>
                  <pre class="p-2.5 rounded-lg bg-zinc-50 dark:bg-zinc-950 text-zinc-800 dark:text-zinc-200 overflow-x-auto max-h-48 border border-zinc-200 dark:border-zinc-800">{{ JSON.stringify(call.response, null, 2) }}</pre>
                </div>
              </div>
            </div>

          </div>
        </div>

        <!-- 模型调用审计仅保留一行元信息，不增加装饰状态。 -->
        <div v-if="task.modelCalls && task.modelCalls.length > 0" class="mt-3 space-y-1">
          <p v-for="mc in task.modelCalls" :key="mc.id" class="meta" data-model-audit>
            模型 {{ mc.modelName }} · {{ mc.totalTokens }} tokens · {{ mc.durationMs }}ms
          </p>
        </div>

      </div>
    </div>

  </div>
</template>
