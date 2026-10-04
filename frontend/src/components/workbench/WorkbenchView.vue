<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { ArrowDown } from 'lucide-vue-next'
import ChatMessage from './ChatMessage.vue'
import ChatInput from './ChatInput.vue'
import TaskHistoryDrawer from './TaskHistoryDrawer.vue'

const props = defineProps({
  projectId: { type: String, default: '' },
  reuseDraft: { type: Object, default: null },
  historyState: { type: Object, default: () => ({}) },
  isMockMode: { type: Boolean, default: false },
  submitGoal: { type: Function, required: true },
  tasks: {
    type: Array,
    default: () => []
  },
  activeTask: {
    type: Object,
    default: null
  },
  currentEnvironment: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  },
  actionLoading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([
  'select-task',
  'confirm-approve',
  'confirm-reject',
  'modify-plan',
  'cancel-task', 'reuse-goal', 'search-history', 'load-more-history', 'open-settings', 'open-projects'
])

// 默认在移动端折叠历史栏，桌面端展开
const showHistory = ref(typeof window !== 'undefined' ? window.innerWidth >= 768 : true)
const scrollContainerRef = ref(null)
const isAtBottom = ref(true)

// 智能吸底检测与平滑滚动
const checkScroll = () => {
  const el = scrollContainerRef.value
  if (!el) return
  const threshold = 80
  const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight <= threshold
  isAtBottom.value = atBottom
}

const scrollToBottom = (smooth = true) => {
  const el = scrollContainerRef.value
  if (!el) return
  el.scrollTo({
    top: el.scrollHeight,
    behavior: smooth ? 'smooth' : 'auto'
  })
}

// 用户停留在底部时跟随真实详情刷新，主动查看历史时保留滚动位置。
watch(() => props.activeTask, (task,previous) => {
  if (task?.id !== previous?.id || isAtBottom.value) nextTick(() => scrollToBottom(false))
}, {flush:'post'})

onMounted(() => {
  scrollToBottom(false)
})
</script>

<template>
  <div class="flex-1 flex min-h-0 overflow-hidden bg-white dark:bg-zinc-950">

    <!-- 左侧固定常驻历史面板 (按钮位置绝对固定，点击直接在下方展开，无弹窗遮罩) -->
    <TaskHistoryDrawer
      :history-state="historyState"
      :is-open="showHistory"
      :tasks="tasks"
      :current-task-id="activeTask?.id"
      @toggle="showHistory = !showHistory"
      @select-task="(id) => emit('select-task', id)"
      @new-task="emit('select-task', null)"
      @search="query => emit('search-history',query)"
      @load-more="emit('load-more-history')"
    />

    <!-- 右侧独立对话主视口 (独立上下滚动，滚动时左侧历史栏纹丝不动) -->
    <div class="relative flex-1 flex flex-col h-full overflow-hidden min-w-0">
      <main
        ref="scrollContainerRef"
        class="flex-1 overflow-y-auto px-4 pt-6 pb-8"
        @scroll="checkScroll"
      >
        <div class="max-w-3xl mx-auto min-h-full flex flex-col justify-start">

        <!-- 空状态：纯净留白、高效、减少视觉注意力分散 (彻底移除中心图形 logo) -->
        <div v-if="!activeTask" class="my-auto py-20 text-center space-y-2 select-none">
          <h3 class="text-base font-medium text-zinc-800 dark:text-zinc-200 tracking-tight">
            输入自然语言测试目标开始执行
          </h3>
          <p class="text-xs text-zinc-500 dark:text-zinc-400 max-w-sm mx-auto leading-relaxed">
            执行器将自动解析 OpenAPI 接口契约并受控编排执行。
          </p>
        </div>

        <!-- 当前活动任务对话卡片 -->
        <div v-else class="space-y-6">
          <ChatMessage
            :task="activeTask"
            :action-loading="actionLoading"
            :read-only="isMockMode"
            @confirm-approve="(id, data) => emit('confirm-approve', id, data)"
            @confirm-reject="(id, data) => emit('confirm-reject', id, data)"
            @modify-plan="(id, inst) => emit('modify-plan', id, inst)"
            @cancel-task="(id) => emit('cancel-task', id)"
            @reuse-goal="emit('reuse-goal')"
            @open-settings="emit('open-settings')"
            @open-projects="emit('open-projects')"
          />
        </div>

      </div>
    </main>

    <!-- 悬浮智能吸底按钮 (用户上滑查看历史时出现) -->
    <transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="opacity-0 translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 translate-y-2"
    >
      <div v-if="!isAtBottom && activeTask" class="absolute bottom-28 right-8 z-30">
        <button
          type="button"
          class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white dark:bg-zinc-800 text-zinc-700 dark:text-zinc-200 border border-zinc-200 dark:border-zinc-700 shadow-lg hover:bg-zinc-50 dark:hover:bg-zinc-750 transition-all text-xs font-medium"
          @click="scrollToBottom(true)"
        >
          <ArrowDown class="w-3.5 h-3.5 text-brand-600 dark:text-brand-400" />
          <span>回到底部</span>
        </button>
      </div>
    </transition>

    <!-- 底部悬浮输入栏 (始终吸底) -->
    <ChatInput
      :project-id="projectId"
      :reuse-draft="reuseDraft"
      :submit-goal="submitGoal"
      :environment-name="currentEnvironment?.name || '未选择环境'"
      :loading="loading"
      :read-only="isMockMode"
    />
    </div>

  </div>
</template>
