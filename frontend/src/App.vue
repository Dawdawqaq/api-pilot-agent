<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import WorkspaceReadiness from './components/workbench/WorkspaceReadiness.vue'
import AppHeader from './components/header/AppHeader.vue'
import WorkbenchView from './components/workbench/WorkbenchView.vue'
import OpenApiView from './components/openapi/OpenApiView.vue'
import ReportsView from './components/reports/ReportsView.vue'
import KnowledgeView from './components/knowledge/KnowledgeView.vue'
import SettingsDrawer from './components/settings/SettingsDrawer.vue'
import SimpleModal from './components/common/SimpleModal.vue'
import ManagementView from './components/projects/ManagementView.vue'
import ContractView from './components/contract/ContractView.vue'
import { api, formatApiError } from './api/client'
import { isTerminalTask } from './domain/task-state'
import { workspaceState } from './domain/workspace-state'
import { MOCK_PROJECTS, MOCK_ENVIRONMENTS, MOCK_SYSTEM_OVERVIEW, MOCK_TASKS_LIST, MOCK_TASK_MAP } from './api/mockData'

const currentTab = ref('workbench')
const readinessRevision = ref(0)
const replayExecutionId = ref('')
const navigate = tab => {
  if (tab === 'settings') isSettingsOpen.value = true
  else if (tab === 'create-project') isCreateProjectOpen.value = true
  else currentTab.value = tab
}
const openReplays = executionId => { replayExecutionId.value = String(executionId || ''); currentTab.value = 'contract' }
const isMockMode = ref(false)
const isSettingsOpen = ref(false)
const projects = ref([])
const selectedProjectId = ref('')
const environments = ref([])
const selectedEnvironmentId = ref('')
const systemOverview = ref({})
const tasks = ref([])
const activeTask = ref(null)
const emptyHistory = () => ({query:'',total:0,hasMore:false,nextCursor:null,busy:false,error:''})
const taskHistory = ref(emptyHistory())
const reuseDraft = ref(null)
let historyVersion = 0
const loading = ref(false)
const actionLoading = ref(false)
const pageError = ref('')
const liveUpdateError = ref('')
const initialLoading = ref(false)
const contextRevision = ref(0)
let taskRevision = 0
let initialRevision = 0
let streamCleanup = null
let pollingTimer = null
const clone = value => JSON.parse(JSON.stringify(value))
const errorText = formatApiError

const isCreateProjectOpen = ref(false)
const newProjectForm = ref({ code: '', name: '', description: '' })
const isCreatingProject = ref(false)
const projectCodeError = ref('')
const isCreateEnvironmentOpen = ref(false)
const emptyEnv = () => ({ name: '', baseUrl: '', allowedMethods: 'GET,POST,PUT,DELETE', allowPrivateNetwork: true, defaultEnvironment: false })
const newEnvForm = ref(emptyEnv())
const isCreatingEnv = ref(false)
const currentEnvironment = computed(() => environments.value.find(e => String(e.id) === selectedEnvironmentId.value))
const surfaceKey = computed(() => `${isMockMode.value}:${selectedProjectId.value}:${contextRevision.value}`)

const stopTaskLiveUpdates = () => {
  liveUpdateError.value = ''
  streamCleanup?.()
  streamCleanup = null
  if (pollingTimer) clearInterval(pollingTimer)
  pollingTimer = null
}
const clearProject = () => {
  replayExecutionId.value = ''
  stopTaskLiveUpdates()
  taskRevision++
  environments.value = []
  selectedEnvironmentId.value = ''
  tasks.value = []
  taskHistory.value = emptyHistory(); historyVersion++; reuseDraft.value = null
  activeTask.value = null
}

// 上下文序号阻止旧请求在项目或模式切换后覆盖新状态。
const loadProjectDetails = async (projectId) => {
  const revision = ++contextRevision.value
  clearProject()
  if (!projectId) return
  if (isMockMode.value) {
    environments.value = clone(MOCK_ENVIRONMENTS.filter(e => String(e.projectId) === String(projectId)))
    selectedEnvironmentId.value = String(environments.value.find(e => e.defaultEnvironment)?.id || environments.value[0]?.id || '')
    tasks.value = clone(MOCK_TASKS_LIST.filter(t => String(t.projectId) === String(projectId)))
    taskHistory.value.total = tasks.value.length
    if (tasks.value.length) await loadTaskDetails(projectId, tasks.value[0].id)
    return
  }
  try {
    taskHistory.value.busy = true
    const [envs, history] = await Promise.all([api.listEnvironments(projectId), api.getTaskHistory(projectId)])
    if (revision !== contextRevision.value || selectedProjectId.value !== String(projectId)) return
    environments.value = envs || []
    const savedEnvironment = workspaceState.getContext().environments[projectId]
    selectedEnvironmentId.value = String(environments.value.find(e => String(e.id) === savedEnvironment)?.id || environments.value.find(e => e.defaultEnvironment)?.id || environments.value[0]?.id || '')
    workspaceState.saveContext(projectId,selectedEnvironmentId.value)
    tasks.value = history.items || []
    taskHistory.value = {...emptyHistory(),total:Number(history.total),hasMore:history.hasMore,nextCursor:history.nextCursor}
    if (tasks.value.length) await loadTaskDetails(projectId, tasks.value[0].id)
  } catch (error) {
    if (revision === contextRevision.value) pageError.value = errorText(error)
  } finally { if (revision === contextRevision.value) taskHistory.value.busy = false }
}

const loadTaskHistory = async (query = taskHistory.value.query, more = false) => {
  if (!selectedProjectId.value || taskHistory.value.busy) return
  const revision = contextRevision.value, version = ++historyVersion, projectId = selectedProjectId.value
  taskHistory.value.busy = true; taskHistory.value.error = ''
  try {
    const cursor = more ? taskHistory.value.nextCursor : null
    const page = isMockMode.value
      ? {items:MOCK_TASKS_LIST.filter(task => String(task.projectId) === projectId && task.goal.toLowerCase().includes(query.toLowerCase())),hasMore:false,nextCursor:null}
      : await api.getTaskHistory(projectId,{query,beforeId:cursor})
    if (revision !== contextRevision.value || version !== historyVersion) return
    const rows = more ? [...tasks.value,...page.items] : page.items
    tasks.value = [...new Map(rows.map(task => [String(task.id),task])).values()]
    taskHistory.value = {query,total:Number(page.total ?? page.items.length),hasMore:page.hasMore,nextCursor:page.nextCursor,busy:false,error:''}
  } catch (error) { if (revision === contextRevision.value && version === historyVersion) taskHistory.value.error = errorText(error) }
  finally { if (revision === contextRevision.value && version === historyVersion) taskHistory.value.busy = false }
}

const loadInitialData = async () => {
  const initialVersion = ++initialRevision
  const revision = ++contextRevision.value
  clearProject()
  projects.value = []
  selectedProjectId.value = ''
  systemOverview.value = {}
  pageError.value = ''
  initialLoading.value = true
  try {
    const [overview, list] = isMockMode.value
      ? [clone(MOCK_SYSTEM_OVERVIEW), clone(MOCK_PROJECTS)]
      : await Promise.all([api.getSystemOverview(), api.listProjects()])
    if (revision !== contextRevision.value) return
    systemOverview.value = overview || {}
    projects.value = list || []
    const savedProject = !isMockMode.value && workspaceState.getContext().projectId
    selectedProjectId.value = String(projects.value.find(project => String(project.id) === savedProject)?.id || projects.value[0]?.id || '')
    await loadProjectDetails(selectedProjectId.value)
  } catch (error) {
    if (revision === contextRevision.value) pageError.value = errorText(error)
  } finally {
    if (initialVersion === initialRevision) initialLoading.value = false
  }
}

// 每次事件都重新读取完整详情，终态仅在结果同步后结束订阅。
const startTaskLiveUpdates = (projectId, taskId) => {
  stopTaskLiveUpdates()
  const revision = contextRevision.value
  const version = taskRevision
  let cursor = 0
  let running = false
  let queued = false
  const valid = () => !isMockMode.value && revision === contextRevision.value && version === taskRevision && String(activeTask.value?.id) === String(taskId)
  const refresh = async () => {
    if (!valid()) return
    queued = true
    if (running) return
    running = true
    try {
      while (queued && valid()) {
        queued = false
        const latest = await api.getAgentTask(projectId, taskId)
        if (!valid()) return
        liveUpdateError.value = ''
        activeTask.value = latest
        tasks.value = tasks.value.map(task => String(task.id) === String(taskId) ? latest : task)
        if (isTerminalTask(latest)) { stopTaskLiveUpdates(); return }
      }
    } catch (error) {
      if (valid()) liveUpdateError.value = errorText(error)
    } finally { running = false }
  }
  try { streamCleanup = api.subscribeTaskStream(projectId, taskId, cursor, event => {
    const sequence = Number(event.sequenceNo)
    if (Number.isFinite(sequence) && sequence <= cursor) return
    if (Number.isFinite(sequence)) cursor = sequence
    refresh()
  }, () => refresh()) } catch (error) { if (valid()) liveUpdateError.value = errorText(error) }
  // 定期校准用于事件遗漏或重连期间的兜底，不依赖故障才启动。
  pollingTimer = setInterval(refresh, 4000)
}

const loadTaskDetails = async (projectId, taskId) => {
  stopTaskLiveUpdates()
  const version = ++taskRevision
  const revision = contextRevision.value
  activeTask.value = null
  if (!taskId) return
  try {
    const task = isMockMode.value ? clone(MOCK_TASK_MAP[String(taskId)] || tasks.value.find(t => String(t.id) === String(taskId)) || null) : await api.getAgentTask(projectId, taskId)
    if (revision !== contextRevision.value || version !== taskRevision) return
    activeTask.value = task
    if (task && !isMockMode.value && !isTerminalTask(task)) startTaskLiveUpdates(projectId, taskId)
  } catch (error) {
    if (revision === contextRevision.value && version === taskRevision) pageError.value = errorText(error)
  }
}
const handleSelectProject = async id => {
  selectedProjectId.value = String(id)
  pageError.value = ''
  await loadProjectDetails(id)
}
const handleSelectEnvironment = id => {
  selectedEnvironmentId.value = String(id)
  if (!isMockMode.value) workspaceState.saveContext(selectedProjectId.value,selectedEnvironmentId.value)
}
const handleReuseGoal = () => {
  if (isMockMode.value || !activeTask.value || activeTask.value.status === 'NEEDS_REVIEW') return
  reuseDraft.value = {goal:activeTask.value.goal,version:Date.now()}
  const environmentId = String(activeTask.value.environmentId || '')
  if (environments.value.some(env => String(env.id) === environmentId)) handleSelectEnvironment(environmentId)
}
const handleToggleMockMode = async () => {
  isSettingsOpen.value = false
  isMockMode.value = !isMockMode.value
  await loadInitialData()
}
const refreshProjects = async preferredId => {
  const revision = contextRevision.value
  if (!isMockMode.value) {
    const list = await api.listProjects()
    if (revision !== contextRevision.value) return
    projects.value = list || []
  }
  const selected = projects.value.find(p => String(p.id) === String(preferredId || selectedProjectId.value)) || projects.value[0]
  selectedProjectId.value = String(selected?.id || '')
  await loadProjectDetails(selectedProjectId.value)
}
const refreshOverview = async () => {
  readinessRevision.value++
  if (!isMockMode.value) {
    const revision = contextRevision.value
    try { const overview = await api.getSystemOverview(); if (revision === contextRevision.value) systemOverview.value = overview }
    catch (error) { if (revision === contextRevision.value) pageError.value = errorText(error) }
  }
}

const handleCreateTask = async (goal, initialVariables = {}) => {
  if (isMockMode.value) { pageError.value = '预览仅供查看，请切换到后端直连后创建任务'; return false }
  if (loading.value || initialLoading.value) return false
  if (!selectedProjectId.value || !selectedEnvironmentId.value) { pageError.value = '请先选择项目并创建或选择执行环境'; return false }
  loading.value = true
  pageError.value = ''
  const projectId = selectedProjectId.value
  const environmentId = selectedEnvironmentId.value
  const revision = contextRevision.value
  try {
    const readiness = await api.getReadiness(projectId,environmentId)
    if (revision !== contextRevision.value || environmentId !== selectedEnvironmentId.value) return false
    if (!readiness.ready) {
      pageError.value = readiness.checks.filter(item => item.status === 'BLOCKED').map(item => item.message).join('；')
      readinessRevision.value++
      return false
    }
    const task = await api.createAgentTask(projectId,{goal,initialVariables,environmentId})
    if (revision === contextRevision.value) { await loadTaskHistory(''); await loadTaskDetails(projectId,task.id) }
    return true
  } catch (error) {
    if (revision === contextRevision.value) pageError.value = errorText(error)
    return false
  } finally { loading.value = false }
}
const taskAction = async (taskId, operation) => {
  if (isMockMode.value) { pageError.value = '预览任务不会执行操作，请切换到后端直连'; return }
  if (actionLoading.value || String(activeTask.value?.id) !== String(taskId)) return
  actionLoading.value = true
  pageError.value = ''
  const projectId = selectedProjectId.value
  const revision = contextRevision.value
  const version = taskRevision
  try {
    await operation(projectId)
    if (revision === contextRevision.value && version === taskRevision) await loadTaskDetails(projectId,taskId)
  } catch (error) { if (revision === contextRevision.value) pageError.value = errorText(error) }
  finally { actionLoading.value = false }
}
const handleConfirmApprove = (id,decision) => taskAction(id, project => api.confirmPlan(project,id,decision))
const handleConfirmReject = handleConfirmApprove
const handleModifyPlan = (id,instruction) => taskAction(id, project => api.modifyPlan(project,id,instruction))
const handleCancelTask = id => taskAction(id, project => api.cancelTask(project,id))

const handleCreateProjectSubmit = async () => {
  if (isCreatingProject.value || isMockMode.value) return
  projectCodeError.value = ''
  const {code: rawCode, name: rawName, description} = newProjectForm.value
  const code = rawCode.trim(), name = rawName.trim()
  if (!name || !/^[a-z][a-z0-9-]*$/.test(code)) { projectCodeError.value = '填写项目名称；编码以小写字母开头，仅包含小写字母、数字和短横线'; return }
  isCreatingProject.value = true
  const revision = contextRevision.value
  try {
    const project = await api.createProject({code,name,description})
    if (revision !== contextRevision.value) return
    await refreshProjects(project.id)
    isCreateProjectOpen.value = false
    newProjectForm.value = {code:'',name:'',description:''}
  } catch (error) { projectCodeError.value = errorText(error) }
  finally { isCreatingProject.value = false }
}
const handleCreateEnvironmentSubmit = async () => {
  if (isCreatingEnv.value || isMockMode.value || !selectedProjectId.value) return
  isCreatingEnv.value = true
  const revision = contextRevision.value
  const projectId = selectedProjectId.value
  try {
    const data = {...newEnvForm.value,name:newEnvForm.value.name.trim(),baseUrl:newEnvForm.value.baseUrl.trim()}
    const environment = await api.createEnvironment(projectId,data)
    if (revision !== contextRevision.value || projectId !== selectedProjectId.value) return
    await loadProjectDetails(projectId)
    handleSelectEnvironment(environment.id)
    isCreateEnvironmentOpen.value = false
    newEnvForm.value = emptyEnv()
  } catch (error) { pageError.value = errorText(error) }
  finally { isCreatingEnv.value = false }
}
onMounted(loadInitialData)
onUnmounted(stopTaskLiveUpdates)
</script>
<template>
  <div class="h-screen overflow-hidden flex flex-col bg-white dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 font-sans transition-colors duration-150">

    <!-- 顶部水平导航栏 -->
    <AppHeader
      :current-tab="currentTab"
      :projects="projects"
      :selected-project-id="selectedProjectId"
      :environments="environments"
      :selected-environment-id="selectedEnvironmentId"
      :system-overview="systemOverview"
      :is-mock-mode="isMockMode"
      @update:current-tab="(t) => currentTab = t"
      @select-project="handleSelectProject"
      @select-environment="handleSelectEnvironment"
      @open-settings="isSettingsOpen = true"
      @toggle-mock-mode="handleToggleMockMode"
      @open-create-project="isCreateProjectOpen = true"
      @open-create-environment="isCreateEnvironmentOpen = true"
    />

    <div v-if="pageError || liveUpdateError" role="alert" class="px-5 py-3 border-b border-red-200 bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300 text-sm flex items-center justify-between gap-4">
      <span>{{ pageError || liveUpdateError }}</span><button class="underline shrink-0" @click="loadInitialData">重新加载</button>
    </div>
    <p v-if="initialLoading" role="status" class="px-5 py-2 text-sm text-zinc-500">正在读取后端数据…</p>
    <!-- 功能视图保持桌面导航风格 -->
    <div class="flex-1 flex flex-col min-h-0 overflow-auto">

      <!-- 视图 1：任务工作台 (Workbench) -->
      <div v-if="currentTab === 'workbench'" class="flex-1 flex flex-col min-h-0">
      <WorkspaceReadiness :project-id="selectedProjectId" :environment-id="selectedEnvironmentId" :is-mock-mode="isMockMode" :revision="readinessRevision" @navigate="navigate" />
      <WorkbenchView
        v-if="currentTab === 'workbench'"
        :project-id="isMockMode ? `preview:${selectedProjectId}` : selectedProjectId"
        :history-state="taskHistory"
        :reuse-draft="reuseDraft"
        :tasks="tasks"
        :active-task="activeTask"
        :current-environment="currentEnvironment"
        :submit-goal="handleCreateTask"
        :loading="loading || initialLoading"
        :is-mock-mode="isMockMode"
        :action-loading="actionLoading"
        @select-task="(id) => loadTaskDetails(selectedProjectId, id)"
        @confirm-approve="handleConfirmApprove"
        @confirm-reject="handleConfirmReject"
        @modify-plan="handleModifyPlan"
        @cancel-task="handleCancelTask"
        @reuse-goal="handleReuseGoal"
        @open-settings="isSettingsOpen = true"
        @open-projects="currentTab = 'projects'"
        @search-history="query => loadTaskHistory(query)"
        @load-more-history="loadTaskHistory(taskHistory.query,true)"
      />
      </div>

      <!-- 视图 2：接口文档与依赖 (OpenAPI) -->
      <OpenApiView
        :key="surfaceKey"
        v-else-if="currentTab === 'openapi'"
        :project-id="selectedProjectId"
        :is-mock-mode="isMockMode"
      />

      <!-- 视图 3：测试报告与导出 (Reports) -->
      <ReportsView
        :key="surfaceKey"
        v-else-if="currentTab === 'reports'"
        :project-id="selectedProjectId"
        :is-mock-mode="isMockMode"
        @open-replays="openReplays"
      />

      <ManagementView v-else-if="currentTab === 'projects'" :key="surfaceKey" :projects="projects" :project-id="selectedProjectId" :environments="environments" :is-mock-mode="isMockMode" @updated="refreshProjects" />
      <ContractView v-else-if="currentTab === 'contract'" :key="surfaceKey" :project-id="selectedProjectId" :environment-id="selectedEnvironmentId" :is-mock-mode="isMockMode" :execution-id="replayExecutionId" />
      <!-- 业务知识库与检索 -->
      <KnowledgeView
        :key="surfaceKey"
        v-else-if="currentTab === 'knowledge'"
        :project-id="selectedProjectId"
        :knowledge-enabled="systemOverview.knowledgeEnabled"
        :is-mock-mode="isMockMode"
      />

    </div>

    <!-- 系统与治理设置抽屉 -->
    <SettingsDrawer
      :is-open="isSettingsOpen"
      :system-overview="systemOverview"
      :selected-project-id="selectedProjectId"
      @close="isSettingsOpen = false"
      @config-updated="refreshOverview"
      :is-mock-mode="isMockMode"
    />

    <!-- 新建项目轻量模态 -->
    <SimpleModal
      :is-open="isCreateProjectOpen"
      title="新建测试项目"
      @close="isCreateProjectOpen = false"
    >
      <form class="space-y-3" @submit.prevent="handleCreateProjectSubmit">
        <div>
          <label class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" for="creation-field-1">
            项目编码 (Project Code) *
          </label>
          <input
            v-model="newProjectForm.code"
            type="text"
            required
            placeholder="例如: payment-service"
            class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-750 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 font-mono text-xs focus-ring" maxlength="64" id="creation-field-1" />
          <span v-if="projectCodeError" class="text-red-500 text-xs mt-1 block">
            {{ projectCodeError }}
          </span>
          <span v-else class="text-zinc-400 text-xs mt-0.5 block">
            小写字母开头，仅允许小写字母、数字和短横线
          </span>
        </div>

        <div>
          <label class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" for="creation-field-2">
            项目名称 (Project Name) *
          </label>
          <input
            v-model="newProjectForm.name"
            type="text"
            required
            placeholder="例如: 统一支付回调服务"
            class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-750 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 text-xs focus-ring" maxlength="128" id="creation-field-2" />
        </div>

        <div>
          <label class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" for="creation-field-3">
            项目描述 (Description)
          </label>
          <textarea
            v-model="newProjectForm.description"
            rows="2"
            placeholder="描述被测服务核心功能..."
            class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-750 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 text-xs focus-ring resize-none" maxlength="500" id="creation-field-3"></textarea>
        </div>

        <div class="pt-2 flex justify-end gap-2">
          <button
            type="button"
            class="btn"
            @click="isCreateProjectOpen = false"
          >
            取消
          </button>
          <button
            type="submit"
            class="btn-primary"
            :disabled="isCreatingProject"
          >
            {{ isCreatingProject ? '创建中...' : '确认创建' }}
          </button>
        </div>
      </form>
    </SimpleModal>

    <!-- 新建环境轻量模态 -->
    <SimpleModal
      :is-open="isCreateEnvironmentOpen"
      title="新建执行环境"
      @close="isCreateEnvironmentOpen = false"
    >
      <form class="space-y-3" @submit.prevent="handleCreateEnvironmentSubmit">
        <div>
          <label class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" for="creation-field-4">
            环境名称 (Name) *
          </label>
          <input
            v-model="newEnvForm.name"
            type="text"
            required
            placeholder="例如: 测试沙箱环境"
            class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-750 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 text-xs focus-ring" maxlength="64" id="creation-field-4" />
        </div>

        <div>
          <label class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" for="creation-field-5">
            Base URL *
          </label>
          <input
            v-model="newEnvForm.baseUrl"
            type="text"
            required
            placeholder="http://127.0.0.1:8082 或 https://api.staging.net"
            class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-750 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 font-mono text-xs focus-ring" maxlength="512" id="creation-field-5" />
        </div>

        <div>
          <label class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" for="creation-field-6">
            允许的 HTTP 方法
          </label>
          <input
            v-model="newEnvForm.allowedMethods"
            type="text"
            placeholder="GET,POST,PUT,DELETE"
            class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-750 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 font-mono text-xs focus-ring" maxlength="128" id="creation-field-6" />
        </div>

        <div class="space-y-2 pt-1">
          <label class="flex items-center gap-2 cursor-pointer">
            <input
              v-model="newEnvForm.allowPrivateNetwork"
              type="checkbox"
              class="rounded text-brand-600 focus:ring-brand-500 w-4 h-4"
            />
            <span class="text-zinc-700 dark:text-zinc-300 text-xs">允许内网私有地址 (SSRF 白名单保护)</span>
          </label>

          <label class="flex items-center gap-2 cursor-pointer">
            <input
              v-model="newEnvForm.defaultEnvironment"
              type="checkbox"
              class="rounded text-brand-600 focus:ring-brand-500 w-4 h-4"
            />
            <span class="text-zinc-700 dark:text-zinc-300 text-xs">设为此项目的默认执行环境</span>
          </label>
        </div>

        <div class="pt-3 flex justify-end gap-2 border-t border-zinc-100 dark:border-zinc-800">
          <button
            type="button"
            class="btn"
            @click="isCreateEnvironmentOpen = false"
          >
            取消
          </button>
          <button
            type="submit"
            class="btn-primary"
            :disabled="isCreatingEnv"
          >
            {{ isCreatingEnv ? '创建中...' : '确认创建' }}
          </button>
        </div>
      </form>
    </SimpleModal>

  </div>
</template>
