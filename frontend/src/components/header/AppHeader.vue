<script setup>
import { computed } from 'vue'
import { FolderKanban, Layers, Settings } from 'lucide-vue-next'
import Dropdown from '../common/Dropdown.vue'
import ThemeToggle from '../common/ThemeToggle.vue'
import Tooltip from '../common/Tooltip.vue'

const props = defineProps({
  currentTab: {
    type: String,
    default: 'workbench' // 'workbench' | 'openapi' | 'reports' | 'knowledge'
  },
  projects: {
    type: Array,
    default: () => []
  },
  selectedProjectId: {
    type: String,
    default: ''
  },
  environments: {
    type: Array,
    default: () => []
  },
  selectedEnvironmentId: {
    type: String,
    default: ''
  },
  systemOverview: {
    type: Object,
    default: () => ({})
  },
  isMockMode: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([
  'update:currentTab',
  'select-project',
  'select-environment',
  'open-settings',
  'toggle-mock-mode',
  'open-create-project',
  'open-create-environment'
])

const projectOptions = computed(() => {
  return props.projects.map(p => ({
    label: p.name,
    value: String(p.id),
    description: p.code,
    tag: p.status
  }))
})

const environmentOptions = computed(() => {
  return props.environments.map(e => ({
    label: e.name,
    displayLabel: e.name?.replace(/\s*[（(][^()（）]*[)）]\s*$/, '').trim() || e.name,
    value: String(e.id),
    description: e.baseUrl,
    tag: e.defaultEnvironment ? '默认' : undefined
  }))
})

const tabs = [
  { id: 'projects', name: '项目与环境' },
  { id: 'contract', name: '契约与回放' },
  { id: 'workbench', name: '任务工作台' },
  { id: 'openapi', name: '接口文档' },
  { id: 'reports', name: '测试报告' },
  { id: 'knowledge', name: '业务知识库' },
]
</script>

<template>
  <header class="sticky top-0 z-40 w-full min-h-14 shrink-0 bg-white/95 dark:bg-zinc-900/95 backdrop-blur-md border-b border-zinc-200 dark:border-zinc-800 transition-colors">
    <div class="min-h-14 px-4 py-2 flex flex-wrap items-center justify-between gap-x-4 gap-y-2">

      <!-- 左侧：产品名与核心业务选项卡 -->
      <div class="flex shrink-0 items-center gap-3">
        <div class="flex items-center select-none">
          <!-- 纯文字品牌表现形式，对齐用户上传的 ChatGPT 纯文字风格 -->
          <span class="text-base font-semibold tracking-tight text-zinc-900 dark:text-zinc-100 hover:opacity-90 transition-opacity cursor-default">
            ApiPilot
          </span>
        </div>

        <!-- 核心业务导航 -->
        <nav class="hidden md:flex items-center space-x-1">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            type="button"
            class="inline-flex items-center gap-1.5 px-2 py-1.5 text-sm font-medium whitespace-nowrap rounded-lg transition-colors"
            :class="[
              currentTab === tab.id
                ? 'bg-zinc-100 dark:bg-zinc-800 text-zinc-900 dark:text-white'
                : 'text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-200 hover:bg-zinc-50 dark:hover:bg-zinc-850'
            ]"
            @click="emit('update:currentTab', tab.id)"
          >
            <span>{{ tab.name }}</span>
            <span
              v-if="tab.id === 'knowledge' && systemOverview.knowledgeEnabled === false"
              class="text-xs px-1.5 py-0.5 rounded bg-zinc-200 dark:bg-zinc-700 text-zinc-500 dark:text-zinc-400 font-normal"
            >
              未启用
            </span>
          </button>
        </nav>
      </div>

      <!-- 右侧：数据模式、项目与环境选择、设置与主题 -->
      <div class="flex shrink-0 items-center gap-1.5 sm:gap-2.5">
        <!-- 演示模式与真实模式指示胶囊 (Quiet Ledger: 发丝边框 + 状态点) -->
        <Tooltip :content="isMockMode ? '当前为代表性演示数据 [MOCK_PREVIEW]，点击可切换直连本地后端' : '当前使用真实后端数据，点击可切回演示模式'" position="bottom">
          <button
            type="button"
            class="px-2.5 py-1 text-xs font-mono rounded-md border border-zinc-250 dark:border-zinc-800 bg-white dark:bg-zinc-900 text-zinc-700 dark:text-zinc-300 hover:bg-zinc-50 dark:hover:bg-zinc-800/60 transition-colors flex items-center gap-1.5 whitespace-nowrap focus-ring"
            @click="emit('toggle-mock-mode')"
          >
            <span class="w-1.5 h-1.5 rounded-full" :class="isMockMode ? 'bg-amber-500' : 'bg-emerald-500'"></span>
            <span>{{ isMockMode ? '预览数据' : '后端直连' }}</span>
          </button>
        </Tooltip>

        <!-- FB-001: 项目选择器 -->
        <Dropdown
          :model-value="selectedProjectId"
          :options="projectOptions"
          :prefix-icon="FolderKanban"
          placeholder="选择测试项目"
          @update:model-value="(val) => emit('select-project', val)"
        >
          <template #footer="{ close }">
            <button
              type="button"
              class="w-full text-left px-3 py-1.5 text-xs text-zinc-900 dark:text-zinc-100 hover:bg-zinc-100 dark:hover:bg-zinc-800 font-medium flex items-center gap-1 transition-colors"
              :disabled="isMockMode"
              @click="close(); emit('open-create-project')"
            >
              + 新建测试项目
            </button>
          </template>
        </Dropdown>

        <!-- FB-001: 环境选择器 -->
        <Dropdown
          :model-value="selectedEnvironmentId"
          :options="environmentOptions"
          :prefix-icon="Layers"
          placeholder="选择执行环境"
          @update:model-value="(val) => emit('select-environment', val)"
        >
          <template #footer="{ close }">
            <button
              type="button"
              class="w-full text-left px-3 py-1.5 text-xs text-zinc-900 dark:text-zinc-100 hover:bg-zinc-100 dark:hover:bg-zinc-800 font-medium flex items-center gap-1 transition-colors"
              :disabled="isMockMode || !selectedProjectId"
              @click="close(); emit('open-create-environment')"
            >
              + 新建执行环境
            </button>
          </template>
        </Dropdown>

        <div class="h-4 w-px bg-zinc-200 dark:bg-zinc-800 mx-0.5"></div>

        <!-- 系统与治理设置抽屉入口 -->
        <Tooltip content="系统 LLM 配置与项目规则治理" position="bottom">
          <button
            type="button"
            class="p-2 rounded-lg text-zinc-600 dark:text-zinc-300 hover:text-zinc-900 dark:hover:text-white hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors focus-ring"
            aria-label="系统与治理设置"
            @click="emit('open-settings')"
          >
            <Settings class="w-4 h-4" />
          </button>
        </Tooltip>

        <!-- FB-002: 主题切换按钮 -->
        <ThemeToggle />
      </div>

    </div>
  </header>
</template>
