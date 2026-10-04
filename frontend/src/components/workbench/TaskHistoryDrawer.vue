<script setup>
import { History, ChevronLeft, Plus } from 'lucide-vue-next'
import Tooltip from '../common/Tooltip.vue'
import HistorySearch from '../common/HistorySearch.vue'
import { presentStatus } from '../../domain/status-presentation'

const props = defineProps({
  historyState: { type: Object, default: () => ({}) },
  isOpen: {
    type: Boolean,
    default: true
  },
  tasks: {
    type: Array,
    default: () => []
  },
  currentTaskId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['toggle', 'select-task', 'new-task', 'search', 'load-more'])

</script>

<template>
  <!-- 左侧固定常驻面板：无弹窗、无遮罩，固定在视口左侧，永不随对话下滑而消失 -->
  <aside
    class="shrink-0 h-full border-r border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 flex flex-col transition-all duration-200 select-none overflow-hidden"
    :class="isOpen ? 'w-56 sm:w-64' : 'w-12'"
  >
    <!-- 固定在左侧顶部的按钮：位置始终绝对固定 -->
    <div
      class="h-12 px-2 flex items-center border-b border-zinc-200/80 dark:border-zinc-800 shrink-0"
      :class="isOpen ? 'justify-between' : 'justify-center'"
    >
      <!-- 展开状态：显示标题与收起箭头 -->
      <button
        v-if="isOpen"
        type="button"
        class="flex-1 flex items-center justify-between px-2 py-1.5 rounded-lg text-xs font-medium text-zinc-700 dark:text-zinc-200 hover:bg-zinc-200/60 dark:hover:bg-zinc-850 transition-colors"
        @click="emit('toggle')"
      >
        <div class="flex items-center gap-1.5">
          <History class="w-4 h-4 text-brand-600 dark:text-brand-400 shrink-0" />
          <span class="font-medium">历史任务</span>
          <span class="px-1.5 py-0.5 rounded-full bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300 text-xs font-mono tabular-nums">
            {{ tasks.length }}
          </span>
        </div>
        <ChevronLeft class="w-4 h-4 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200" />
      </button>

      <!-- 折叠状态：固定的小图标按钮 -->
      <Tooltip v-else content="展开历史任务列表" position="right">
        <button
          type="button"
          class="w-8 h-8 rounded-lg flex items-center justify-center text-zinc-600 dark:text-zinc-300 hover:bg-zinc-200/60 dark:hover:bg-zinc-850 transition-colors"
          @click="emit('toggle')"
        >
          <History class="w-4 h-4 text-brand-600 dark:text-brand-400" />
        </button>
      </Tooltip>
    </div>

    <!-- 新建测试任务入口 -->
    <div class="p-2 border-b border-zinc-200/60 dark:border-zinc-800 shrink-0">
      <button
        v-if="isOpen"
        type="button"
        class="w-full flex items-center justify-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-white dark:bg-zinc-900 text-zinc-800 dark:text-zinc-200 border border-zinc-200 dark:border-zinc-750 shadow-xs hover:bg-zinc-100 dark:hover:bg-zinc-800 hover:text-zinc-900 dark:hover:text-white transition-all active:scale-[0.98] focus-ring"
        @click="emit('new-task')"
      >
        <Plus class="w-3.5 h-3.5 text-brand-600 dark:text-brand-400" />
        <span>新建测试任务</span>
      </button>

      <Tooltip v-else content="新建测试任务" position="right">
        <button
          type="button"
          class="w-8 h-8 mx-auto rounded-lg flex items-center justify-center text-zinc-600 dark:text-zinc-300 hover:bg-zinc-200/60 dark:hover:bg-zinc-850 transition-colors active:scale-95"
          @click="emit('new-task')"
        >
          <Plus class="w-4 h-4 text-brand-600 dark:text-brand-400" />
        </button>
      </Tooltip>
    </div>

    <HistorySearch v-if="isOpen" label="搜索任务目标" :query="historyState.query" :busy="historyState.busy" :total="historyState.total" :loaded="tasks.length" @search="query => emit('search',query)" />
    <p v-if="isOpen && historyState.error" role="alert" class="px-3 py-2 text-xs text-red-600 dark:text-red-400">{{ historyState.error }} <button class="underline" :disabled="historyState.busy" @click="emit('search',historyState.query)">重试读取</button></p>
    <!-- 历史结果按真实游标继续读取，不截断为最近二十条。 -->
    <div v-if="isOpen" class="flex-1 overflow-y-auto p-2 space-y-2">
      <div v-if="tasks.length === 0" class="py-12 text-center text-xs text-zinc-400">
        {{ historyState.busy ? '正在读取历史…' : historyState.query ? '没有匹配的任务' : '暂无任务记录' }}
      </div>
      <!-- 历史项：无卡片边框，选中仅用浅灰底；状态是圆点 + 文字 -->
      <button
        v-for="t in tasks"
        :key="t.id"
        type="button"
        class="w-full text-left px-2.5 py-2 rounded-lg transition-colors focus-ring"
        :class="String(t.id) === String(currentTaskId)
          ? 'bg-zinc-200/70 dark:bg-zinc-800'
          : 'hover:bg-zinc-100 dark:hover:bg-zinc-900'"
        @click="emit('select-task', t.id)"
      >
        <p class="text-sm text-zinc-900 dark:text-zinc-100 line-clamp-2 break-words">{{ t.goal }}</p>
        <div class="mt-1 flex items-center justify-between">
          <span class="status" :class="presentStatus(t.status).tone">{{ presentStatus(t.status).text }}</span>
          <span class="meta font-mono">#{{ t.id }}</span>
        </div>
      </button>
      <button v-if="historyState.hasMore" class="btn w-full" :disabled="historyState.busy" @click="emit('load-more')">{{ historyState.busy ? '正在读取…' : '加载更多任务' }}</button>
    </div>
  </aside>
</template>
