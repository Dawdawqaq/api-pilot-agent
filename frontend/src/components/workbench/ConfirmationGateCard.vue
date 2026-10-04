<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { remainingConfirmation } from '../../domain/task-recovery'
import { Loader2, ChevronDown } from 'lucide-vue-next'

const props = defineProps({
  readOnly: { type: Boolean, default: false },
  confirmation: {
    type: Object,
    required: true
  },
  currentStepData: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['approve', 'reject', 'modify'])

const requestOpen = ref(false)
const decisionNote = ref('')
const copied = ref(false)
const showModifyBox = ref(false)
const modifyInstruction = ref('')
const now = ref(Date.now())
let timer
onMounted(() => { timer = setInterval(() => { now.value = Date.now() },1000) })
onUnmounted(() => clearInterval(timer))
const remaining = computed(() => remainingConfirmation(props.confirmation.expiresAt,now.value))
const expired = computed(() => remaining.value?.expired === true)

// 时间格式化辅助
const formatTime = (ts) => {
  if (!ts) return ''
  if (typeof ts === 'string' && ts.includes('T')) {
    const parts = ts.split('T')
    return `${parts[0]} ${parts[1]?.slice(0, 8)}`
  }
  return ts
}

const copyHash = async () => {
  if (!props.confirmation?.planHash) return
  try {
    let success = false
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(props.confirmation.planHash)
      success = true
    } else {
      const ta = document.createElement('textarea')
      ta.value = props.confirmation.planHash
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.focus()
      ta.select()
      success = document.execCommand('copy')
      document.body.removeChild(ta)
    }
    if (success) {
      copied.value = true
      setTimeout(() => {
        copied.value = false
      }, 2000)
    }
  } catch (err) {
    console.error('复制失败', err)
  }
}

const handleApprove = () => {
  if (props.loading || props.readOnly || expired.value) return
  emit('approve', {
    approved: true,
    note: decisionNote.value.trim() || undefined,
    planHash: props.confirmation.planHash
  })
}

const handleReject = () => {
  if (props.loading || props.readOnly || expired.value) return
  emit('reject', {
    approved: false,
    note: decisionNote.value.trim() || '用户人工拒绝此危险操作',
    planHash: props.confirmation.planHash
  })
}

const submitModify = () => {
  if (props.loading || props.readOnly || expired.value || !modifyInstruction.value.trim()) return
  emit('modify', modifyInstruction.value.trim())
}
</script>

<template>
  <div class="card p-4 my-4">
    <!-- 头部警示提示：琥珀圆点 + 状态文字 -->
    <div class="flex items-center justify-between gap-2 flex-wrap">
      <span class="status status-warn font-medium text-sm">
        人工确认安全闸门 · 步骤 {{ Number(confirmation.stepIndex) + 1 }}
      </span>
      <span v-if="confirmation.expiresAt" class="meta font-mono">
        有效至 {{ formatTime(confirmation.expiresAt) }}
      </span>
    </div>
    <p v-if="remaining" role="status" class="mt-2 meta" :class="expired ? 'text-amber-700 dark:text-amber-300' : ''">{{ remaining.label }}</p>

    <p class="text-xs text-zinc-600 dark:text-zinc-400 mt-2 leading-relaxed">
      此步骤将向目标服务发起写入/变更请求，可能导致远端数据发生持久变更。系统已拦截并等待人工授权。
    </p>

    <!-- 当前步骤的客观描述与请求摘要 -->
    <div v-if="currentStepData" class="mt-3 p-3 rounded-lg bg-zinc-50 dark:bg-zinc-950 border border-zinc-100 dark:border-zinc-800 text-xs">
      <div class="text-sm font-medium text-zinc-900 dark:text-zinc-100 break-words">
        {{ currentStepData.objective }}
      </div>
      <div v-if="currentStepData.request" class="mt-1 meta font-mono flex items-center gap-2">
        <span class="text-zinc-700 dark:text-zinc-300 font-medium">{{ currentStepData.request.method }}</span>
        <span class="truncate">{{ currentStepData.request.path }}</span>
      </div>
    </div>

    <div v-if="currentStepData?.request" class="mt-2">
      <button type="button" :aria-expanded="requestOpen" class="meta inline-flex items-center gap-1 focus-ring" @click="requestOpen = !requestOpen">
        请求参数 <ChevronDown class="w-3.5 h-3.5" :class="requestOpen ? '' : '-rotate-90'" />
      </button>
      <pre v-if="requestOpen" class="mt-2 p-2.5 rounded-lg bg-zinc-50 dark:bg-zinc-950 text-xs font-mono overflow-auto max-h-48">{{ JSON.stringify(currentStepData.request, null, 2) }}</pre>
    </div>

    <!-- 关键 planHash 校验展示 -->
    <div class="mt-3 flex items-center justify-between gap-2 p-2 rounded-lg bg-zinc-50 dark:bg-zinc-950 border border-zinc-100 dark:border-zinc-800 meta font-mono">
      <div class="flex items-center gap-2 min-w-0 truncate">
        <span class="text-zinc-500 shrink-0">planHash:</span>
        <span class="text-zinc-800 dark:text-zinc-200 truncate select-all">{{ confirmation.planHash }}</span>
      </div>
      <button
        type="button"
        class="hover:text-zinc-900 dark:hover:text-zinc-100 shrink-0 transition-colors"
        @click="copyHash"
      >
        {{ copied ? '已复制' : '复制' }}
      </button>
    </div>

    <!-- 决策备注输入框 (可选) -->
    <div class="mt-3">
      <label class="block meta mb-1">
        决策审核备注（可选，最多 500 字符）
      </label>
      <input
        v-model="decisionNote"
        type="text"
        maxlength="500"
        placeholder="例如：已在沙箱环境核验，允许执行写操作"
        class="w-full px-3 py-1.5 text-xs rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100 placeholder-zinc-400 focus-ring"
      />
    </div>

    <!-- 操作按钮组 -->
    <div class="mt-4 flex flex-wrap items-center justify-between gap-2.5">
      <div class="flex items-center gap-2">
        <!-- 批准按钮 -->
        <button
          type="button"
          class="btn-primary"
          :disabled="loading || readOnly || expired"
          @click="handleApprove"
        >
          <Loader2 v-if="loading" class="w-3.5 h-3.5 animate-spin" />
          <span>批准并继续执行</span>
        </button>

        <!-- 拒绝按钮 -->
        <button
          type="button"
          class="btn"
          :disabled="loading || readOnly || expired"
          @click="handleReject"
        >
          <span>拒绝终止</span>
        </button>
      </div>

      <!-- 修改计划指令展开切换 -->
      <button
        type="button"
        class="text-xs text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200 transition-colors"
        :disabled="loading || readOnly || expired" @click="showModifyBox = !showModifyBox"
      >
        {{ showModifyBox ? '收起修改' : '提出计划修改建议 (/modify)' }}
      </button>
    </div>

    <!-- 折叠的修改计划指令输入框 -->
    <div v-if="showModifyBox" class="mt-3 pt-3 border-t border-zinc-100 dark:border-zinc-800">
      <div class="flex gap-2">
        <input
          v-model="modifyInstruction"
          type="text"
          maxlength="2000"
          placeholder="输入给 Agent 的修正提示，例如：跳过第2步，直接测试只读接口"
          class="flex-1 px-3 py-1.5 text-xs rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100 placeholder-zinc-400 focus-ring"
          @keydown.enter.prevent="submitModify"
        />
        <button
          type="button"
          class="btn"
          :disabled="loading || readOnly || expired || !modifyInstruction.trim()"
          @click="submitModify"
        >
          提交指令
        </button>
      </div>
    </div>
  </div>
</template>
