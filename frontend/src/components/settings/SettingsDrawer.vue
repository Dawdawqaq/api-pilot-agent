<script setup>
import { ref, watch, useId } from 'vue'
import { X, Cpu, Check, Loader2 } from 'lucide-vue-next'
import { api, formatApiError } from '../../api/client'
import { useDialog } from '../../composables/useDialog'
const props = defineProps({isOpen:Boolean,systemOverview:{type:Object,default:()=>({})},selectedProjectId:{type:String,default:''},isMockMode:Boolean})
const emit = defineEmits(['close','config-updated'])
const dialogRef = ref(null), titleId = useId()
useDialog(() => props.isOpen, dialogRef, () => emit('close'))
const activeTab = ref('llm')
const llmForm = ref({mode:'OFFLINE',provider:'DASHSCOPE',baseUrl:'',model:'',apiKey:''})
const policyForm = ref({externalModelAllowed:false,allowedProvider:'ANY',allowDocumentContent:false,allowSchemaContent:false,promptCharacterBudget:20000,endpointTopK:5})
const apiKeyConfigured = ref(false), persistentStorageReady = ref(false)
const llmReady = ref(false), policyReady = ref(false), isLoading = ref(false)
const loadError = ref(''), actionError = ref('')
const isTesting = ref(false), testResult = ref(null), isSaving = ref(false), saveSuccess = ref(false)
const isSavingPolicy = ref(false), savePolicySuccess = ref(false)
let draftRevision = 0
let revision = 0, loadedProject = '', saved = null
const errorText = formatApiError
const applyConfig = config => {
  saved = {...config}
  llmForm.value = {mode:config.mode,provider:config.provider,baseUrl:config.baseUrl || '',model:config.model || '',apiKey:''}
  apiKeyConfigured.value = Boolean(config.apiKeyConfigured)
  persistentStorageReady.value = Boolean(config.persistentStorageReady)
}
const setLlmMode = mode => {
  llmForm.value.mode = mode
  if (mode === 'OFFLINE') llmForm.value.apiKey = ''
}
const initData = async () => {
  const current = ++revision
  isLoading.value = false
  llmReady.value = false; policyReady.value = false; loadedProject = ''; saved = null
  llmForm.value.apiKey = ''; testResult.value = null; actionError.value = ''; loadError.value = ''
  saveSuccess.value = false; savePolicySuccess.value = false
  if (props.isMockMode) { loadError.value = '预览模式不读取或修改真实配置，也不测试外部连接。请切换到后端模式后使用。'; return }
  isLoading.value = true
  const project = props.selectedProjectId
  const results = await Promise.allSettled([api.getLlmConfig(), project ? api.getModelPolicy(project) : Promise.resolve(null)])
  if (current !== revision || !props.isOpen || props.isMockMode) return
  const [configuration, policy] = results
  if (configuration.status === 'fulfilled' && configuration.value) { applyConfig(configuration.value); llmReady.value = true }
  else loadError.value = `模型配置读取失败：${errorText(configuration.reason || {})}`
  if (policy.status === 'fulfilled' && policy.value) { policyForm.value = {...policy.value}; policyReady.value = true; loadedProject = project }
  else if (project) loadError.value += `${loadError.value ? '；' : ''}项目策略读取失败：${errorText(policy.reason || {})}`
  isLoading.value = false
}
watch(() => [props.isOpen,props.selectedProjectId,props.isMockMode], ([open]) => {
  if (open) initData()
  else { revision++; llmForm.value.apiKey = ''; testResult.value = null; isLoading.value = false }
})
watch(llmForm, () => { draftRevision++; testResult.value = null; saveSuccess.value = false }, {deep:true,flush:'sync'})
const draft = () => llmForm.value.mode === 'OFFLINE' && saved
  ? {...llmForm.value,provider:saved.provider,baseUrl:saved.baseUrl,model:saved.model,apiKey:''}
  : {...llmForm.value}
const testConnection = async () => {
  if (props.isMockMode || !llmReady.value || isTesting.value || isSaving.value) return
  isTesting.value = true; actionError.value = ''; testResult.value = null
  const current = revision, version = draftRevision
  try { const result = await api.testLlmConnection(draft()); if (current === revision && version === draftRevision) testResult.value = result }
  catch (error) { if (current === revision && version === draftRevision) testResult.value = {success:false,message:errorText(error)} }
  finally { isTesting.value = false }
}
const saveLlm = async () => {
  if (props.isMockMode || !llmReady.value || isSaving.value || isTesting.value) return
  isSaving.value = true; actionError.value = ''
  const current = revision
  try {
    const config = await api.saveLlmConfig(draft())
    if (current === revision) { applyConfig(config); saveSuccess.value = true; testResult.value = null }
    emit('config-updated')
  } catch (error) { if (current === revision) actionError.value = errorText(error) }
  finally { isSaving.value = false }
}
const savePolicy = async () => {
  if (props.isMockMode || !policyReady.value || loadedProject !== props.selectedProjectId || isSavingPolicy.value) return
  isSavingPolicy.value = true; actionError.value = ''
  const current = revision, project = loadedProject
  try {
    const fields = ['externalModelAllowed','allowedProvider','allowDocumentContent','allowSchemaContent','promptCharacterBudget','endpointTopK']
    const payload = Object.fromEntries(fields.map(field => [field,policyForm.value[field]]))
    const policy = await api.saveModelPolicy(project,payload)
    if (current === revision) { policyForm.value = {...policy}; savePolicySuccess.value = true }
  } catch (error) { if (current === revision) actionError.value = errorText(error) }
  finally { isSavingPolicy.value = false }
}
</script>
<template>
  <div>
    <!-- 遮罩层 -->
    <transition
      enter-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition-opacity duration-150"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="isOpen"
        class="fixed inset-0 z-40 bg-zinc-950/40 backdrop-blur-xs"
        @click="emit('close')"
      />
    </transition>

    <!-- 抽屉主体 -->
    <transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="translate-x-full"
      enter-to-class="translate-x-0"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="translate-x-0"
      leave-to-class="translate-x-full"
    >
      <aside
        ref="dialogRef" role="dialog" aria-modal="true" :aria-labelledby="titleId" tabindex="-1"
        v-if="isOpen"
        class="fixed top-0 bottom-0 right-0 z-50 w-full max-w-md bg-white dark:bg-zinc-900 border-l border-zinc-200 dark:border-zinc-800 shadow-2xl flex flex-col"
      >
        <!-- 头部 -->
        <p v-if="loadError" role="alert" class="px-5 py-3 text-sm text-amber-700 dark:text-amber-300">{{ loadError }} <button v-if="!isMockMode" class="underline" @click="initData">重试读取</button></p>
        <p v-if="actionError" role="alert" class="px-5 py-3 text-sm text-red-600">{{ actionError }}</p>
        <p v-if="isLoading" role="status" class="px-5 py-2 text-sm">正在读取配置…</p>
        <div class="h-14 px-5 border-b border-zinc-200 dark:border-zinc-800 flex items-center justify-between">
          <div class="flex items-center gap-2 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            <Cpu class="w-4 h-4 text-brand-600 dark:text-brand-400" />
            <span><span :id="titleId">系统设置与规则治理</span></span>
          </div>
          <button
            type="button"
            class="p-1.5 rounded-lg text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
            @click="emit('close')" aria-label="关闭设置"
          >
            <X class="w-4 h-4" />
          </button>
        </div>

        <p class="px-5 pb-3 meta" data-active-model>
          当前生效模型 <span class="font-mono text-zinc-700 dark:text-zinc-300">{{ systemOverview.chatModel || '模型信息未加载' }}</span>
        </p>

        <!-- 选项卡切换 -->
        <div class="px-5 pt-3 border-b border-zinc-100 dark:border-zinc-800 flex gap-4 text-xs font-medium">
          <button
            type="button"
            class="pb-2.5 transition-colors border-b-2"
            :class="activeTab === 'llm'
              ? 'border-zinc-900 dark:border-zinc-100 text-zinc-900 dark:text-zinc-100'
              : 'border-transparent text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100'"
            @click="activeTab = 'llm'"
          >
            系统 LLM 配置 (应用级)
          </button>
          <button
            type="button"
            class="pb-2.5 transition-colors border-b-2"
            :class="activeTab === 'policy'
              ? 'border-zinc-900 dark:border-zinc-100 text-zinc-900 dark:text-zinc-100'
              : 'border-transparent text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100'"
            @click="activeTab = 'policy'"
          >
            项目调用策略 (项目级)
          </button>
        </div>

        <!-- 内容区域 -->
        <div class="flex-1 overflow-y-auto p-5 space-y-4 text-xs">

          <!-- Tab 1: 系统 LLM 配置 -->
          <fieldset v-if="activeTab === 'llm' && llmReady" :disabled="isSaving" class="space-y-4">
            <div>
              <span class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" id="llm-mode-label">
                运行模式 (Mode)
              </span>
              <div class="grid grid-cols-2 gap-2" role="group" aria-labelledby="llm-mode-label">
                <button
                  type="button"
                  class="py-1.5 px-3 rounded-lg border text-center transition-colors font-mono"
                  :class="llmForm.mode === 'API'
                    ? 'border-zinc-900 dark:border-zinc-100 bg-zinc-100 dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 font-medium'
                    : 'border-zinc-200 dark:border-zinc-700 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-50 dark:hover:bg-zinc-800'"
                  @click="setLlmMode('API')"
                  :aria-pressed="llmForm.mode === 'API'"
                >
                  API (在线供应商)
                </button>
                <button
                  type="button"
                  class="py-1.5 px-3 rounded-lg border text-center transition-colors font-mono"
                  :class="llmForm.mode === 'OFFLINE'
                    ? 'border-zinc-900 dark:border-zinc-100 bg-zinc-100 dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 font-medium'
                    : 'border-zinc-200 dark:border-zinc-700 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-50 dark:hover:bg-zinc-800'"
                  @click="setLlmMode('OFFLINE')"
                  :aria-pressed="llmForm.mode === 'OFFLINE'"
                >
                  OFFLINE (离线运行)
                </button>
              </div>
            </div>

            <div>
              <label class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" for="settings-provider">
                模型供应商 (Provider)
              </label>
              <select
                v-model="llmForm.provider"
                class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 focus-ring" id="settings-provider">
                <option value="DASHSCOPE">阿里百炼 (DASHSCOPE)</option>
                <option value="DEEPSEEK">深度求索 (DEEPSEEK)</option>
                <option value="OPENAI_COMPATIBLE">OpenAI 兼容协议 (OPENAI_COMPATIBLE)</option>
              </select>
            </div>

            <div>
              <label class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" for="settings-field-1">
                Base URL (可选)
              </label>
              <input
                v-model="llmForm.baseUrl"
                type="text"
                placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1"
                class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 placeholder-zinc-400 font-mono text-xs focus-ring"
              id="settings-field-1" />
            </div>

            <div>
              <label class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" for="settings-field-2">
                模型名称 (Model)
              </label>
              <input
                v-model="llmForm.model"
                type="text"
                placeholder="qwen-plus / deepseek-chat"
                class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 font-mono text-xs focus-ring"
              id="settings-field-2" />
            </div>

            <div>
              <div class="flex items-center justify-between mb-1">
                <label class="font-medium text-zinc-700 dark:text-zinc-300" for="settings-field-3">
                  API Key
                </label>
                <span v-if="apiKeyConfigured" class="status status-ok">
                  已配置密钥 (留空则保留)
                </span>
                <span v-else class="status status-warn">
                  未配置密钥 (API 模式需填写)
                </span>
              </div>
              <input
                v-model="llmForm.apiKey"
                type="password"
                placeholder="sk-..."
                class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 font-mono text-xs focus-ring"
              id="settings-field-3" />
            </div>

            <!-- 测试连通性反馈 -->
            <div v-if="testResult" class="card p-3 text-xs">
              <div class="flex items-center gap-2 font-medium">
                <span class="status" :class="testResult.success ? 'status-ok' : 'status-err'"></span>
                <span class="text-zinc-900 dark:text-zinc-100">{{ testResult.message }}</span>
              </div>
              <div v-if="testResult.durationMs" class="mt-1 meta font-mono">
                耗时: {{ testResult.durationMs }}ms
              </div>
            </div>

            <!-- 操作按钮组 -->
            <div class="pt-2 flex items-center justify-between gap-3">
              <button
                type="button"
                class="btn"
                :disabled="isTesting || isSaving || !llmReady || isMockMode"
                @click="testConnection"
              >
                <Loader2 v-if="isTesting" class="w-3.5 h-3.5 animate-spin" />
                <span>{{ isTesting ? '正在测试...' : '测试草稿连接' }}</span>
              </button>

              <button
                type="button"
                class="btn-primary flex-1 justify-center"
                :disabled="isSaving || isTesting || !llmReady || isMockMode"
                @click="saveLlm"
              >
                <Check v-if="saveSuccess" class="w-3.5 h-3.5" />
                <Loader2 v-else-if="isSaving" class="w-3.5 h-3.5 animate-spin" />
                <span>{{ saveSuccess ? '保存成功' : (isSaving ? '正在保存...' : '保存配置') }}</span>
              </button>
            </div>
          </fieldset>

          <!-- Tab 2: 项目治理规则 -->
          <div v-if="activeTab === 'policy' && policyReady" class="space-y-4">
            <div class="card p-3 text-xs space-y-3">
              <div class="flex items-center justify-between">
                <div>
                  <span class="font-medium text-zinc-800 dark:text-zinc-200">允许外部大模型</span>
                  <p class="meta mt-0.5">控制当前项目是否可以将 Schema / 目标发送给公网模型</p>
                </div>
                <input
                  v-model="policyForm.externalModelAllowed" aria-label="允许外部大模型"
                  type="checkbox"
                  class="rounded text-brand-600 focus:ring-brand-500 w-4 h-4 cursor-pointer"
                />
              </div>

              <div class="flex items-center justify-between pt-2 border-t border-zinc-100 dark:border-zinc-800">
                <div>
                  <span class="font-medium text-zinc-800 dark:text-zinc-200">允许携带接口契约 Schema</span>
                  <p class="meta mt-0.5">Prompt 中是否允许嵌入相关 API 参数定义与结构</p>
                </div>
                <input
                  v-model="policyForm.allowSchemaContent" aria-label="允许携带接口契约 Schema"
                  type="checkbox"
                  class="rounded text-brand-600 focus:ring-brand-500 w-4 h-4 cursor-pointer"
                />
              </div>

              <div class="flex items-center justify-between pt-2 border-t border-zinc-100 dark:border-zinc-800">
                <div>
                  <span class="font-medium text-zinc-800 dark:text-zinc-200">允许携带文档原文</span>
                  <p class="meta mt-0.5">Prompt 中是否允许嵌入检索到的业务文档片段</p>
                </div>
                <input
                  v-model="policyForm.allowDocumentContent" aria-label="允许携带文档原文"
                  type="checkbox"
                  class="rounded text-brand-600 focus:ring-brand-500 w-4 h-4 cursor-pointer"
                />
              </div>
            </div>

            <div>
              <label class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" for="settings-field-4">
                允许的模型供应商 (Allowed Provider)
              </label>
              <select
                v-model="policyForm.allowedProvider"
                class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 text-xs focus-ring"
               id="settings-field-4">
                <option value="ANY">全部供应商 (ANY)</option>
                <option value="DASHSCOPE">阿里百炼 (DASHSCOPE)</option>
                <option value="DEEPSEEK">深度求索 (DEEPSEEK)</option>
                <option value="OPENAI_COMPATIBLE">OpenAI 兼容协议 (OPENAI_COMPATIBLE)</option>
              </select>
            </div>

            <div>
              <label class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" for="settings-field-5">
                Prompt 字符预算上限 (Budget)
              </label>
              <input
                v-model.number="policyForm.promptCharacterBudget"
                type="number"
                min="2000"
                max="200000"
                step="1000"
                class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 font-mono text-xs focus-ring"
              id="settings-field-5" />
            </div>

            <div>
              <label class="block font-medium text-zinc-700 dark:text-zinc-300 mb-1" for="settings-field-6">
                接口召回 Top-K
              </label>
              <input
                v-model.number="policyForm.endpointTopK"
                type="number"
                min="1"
                max="50"
                class="w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 font-mono text-xs focus-ring"
              id="settings-field-6" />
            </div>

            <!-- 操作按钮组 -->
            <div class="pt-2">
              <button
                type="button"
                class="btn-primary w-full justify-center"
                :disabled="isSavingPolicy || !policyReady || isMockMode"
                @click="savePolicy"
              >
                <Check v-if="savePolicySuccess" class="w-3.5 h-3.5" />
                <Loader2 v-else-if="isSavingPolicy" class="w-3.5 h-3.5 animate-spin" />
                <span>{{ savePolicySuccess ? '策略保存成功' : (isSavingPolicy ? '正在保存...' : '保存项目策略') }}</span>
              </button>
            </div>
          </div>

        </div>

      </aside>
    </transition>
  </div>
</template>
