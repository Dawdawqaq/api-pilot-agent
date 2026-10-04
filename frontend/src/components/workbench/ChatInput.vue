<script setup>
import { ref, nextTick, useId, watch, onMounted } from 'vue'
import { workspaceState } from '../../domain/workspace-state'
import { ArrowUp, ChevronDown, Loader2 } from 'lucide-vue-next'

const props = defineProps({
  projectId: { type: String, default: '' },
  reuseDraft: { type: Object, default: null },
  readOnly: { type: Boolean, default: false },
  submitGoal: { type: Function, required: true },
  environmentName: {
    type: String,
    default: '未指定环境'
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const text = ref('')
const initialVariables = ref('{}')
const initialVariablesOpen = ref(false)
const variablesId = useId()
const inputError = ref('')
const composing = ref(false)
const submitting = ref(false)
const textareaRef = ref(null)
const draftStorageAvailable = ref(workspaceState.persistenceAvailable)
let restoring = false

watch(() => props.projectId, id => {
  restoring = true
  const draft = workspaceState.getDraft(id)
  text.value = draft.goal; initialVariables.value = draft.variables; inputError.value = ''
  restoring = false
  nextTick(adjustHeight)
}, {immediate:true})
watch([text, initialVariables], () => {
  if (!restoring && !props.readOnly) {
    workspaceState.saveDraft(props.projectId,{goal:text.value,variables:initialVariables.value})
    draftStorageAvailable.value = workspaceState.persistenceAvailable
  }
}, {flush:'sync'})
watch(() => props.reuseDraft, draft => {
  if (!draft || props.readOnly) return
  text.value = draft.goal; initialVariables.value = '{}'
  inputError.value = ''
  nextTick(() => { adjustHeight(); textareaRef.value?.focus() })
})
onMounted(() => nextTick(adjustHeight))

function adjustHeight() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 180)}px`
}

const handleInput = () => {
  adjustHeight()
}

const handleKeydown = (e) => {
  // 支持 Enter 发送（无需 Ctrl，或者 Ctrl+Enter 发送，Shift+Enter 换行）
  if (e.isComposing || composing.value || e.keyCode === 229) return
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

const send = async () => {
  const val = text.value.trim()
  if (!val || props.readOnly || props.loading || submitting.value || composing.value) return
  inputError.value = ''
  let variables
  try {
    variables = JSON.parse(initialVariables.value || '{}')
    if (!variables || Array.isArray(variables) || typeof variables !== 'object') throw new Error('初始变量必须是 JSON 对象')
  } catch (error) { inputError.value = error.message; return }
  submitting.value = true
  const submittedProject = props.projectId, submittedText = text.value, submittedVariables = initialVariables.value
  try {
    const success = await props.submitGoal(val, variables)
    if (success) {
      const saved = workspaceState.getDraft(submittedProject)
      if (saved.goal === submittedText && saved.variables === submittedVariables) workspaceState.saveDraft(submittedProject,{goal:'',variables:submittedVariables})
      if (props.projectId === submittedProject && text.value === submittedText) text.value = ''
      await nextTick()
      adjustHeight()
    }
  } catch (error) { inputError.value = error.message || '发送失败，请重试' }
  finally { submitting.value = false }
}

</script>

<template>
  <div class="sticky bottom-0 z-30 w-full pt-2 pb-6 px-4 bg-gradient-to-t from-white via-white/95 to-transparent dark:from-zinc-950 dark:via-zinc-950/95">
    <div class="max-w-3xl mx-auto">

      <p v-if="inputError" role="alert" class="mb-2 text-xs text-red-600">{{ inputError }}</p>
      <p v-if="!readOnly && projectId" class="mb-2 meta">{{ draftStorageAvailable ? '目标草稿自动保存；初始变量仅在本次页面内保留。' : '本地存储不可用，目标与变量仅在本次页面内保留。' }}</p>
      <!-- 输入卡片容器 (Quiet Ledger: 规整 1px 细线边框、去扩散阴影) -->
      <div class="relative rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 transition-colors focus-within:border-zinc-400 dark:focus-within:border-zinc-600">

        <textarea
          ref="textareaRef"
          aria-label="测试目标" maxlength="2000"
          @compositionstart="composing = true" @compositionend="composing = false"
          v-model="text"
          rows="1"
          placeholder="输入自然语言测试目标（例如：对 GET /api/v1/orders 发起参数边界测试，断言 200 返回）"
          class="w-full px-4 pt-3.5 pb-2 text-sm bg-transparent text-zinc-900 dark:text-zinc-100 placeholder-zinc-400 dark:placeholder-zinc-500 resize-none outline-none max-h-48"
          :disabled="loading || readOnly"
          @input="handleInput"
          @keydown="handleKeydown"
        ></textarea>

        <!-- 底部元信息与操作栏 -->
        <div class="px-4 pb-2.5 flex items-center justify-between gap-3">
          <div class="flex min-w-0 flex-1 flex-wrap items-center gap-x-2 gap-y-1 meta">
            <span>环境:</span>
            <span class="font-mono text-zinc-600 dark:text-zinc-400 break-words">{{ environmentName }}</span>
            <button type="button" class="inline-flex shrink-0 items-center gap-1 rounded px-1 py-0.5 hover:text-zinc-900 dark:hover:text-zinc-100 focus-ring" :aria-expanded="initialVariablesOpen" :aria-controls="variablesId" @click="initialVariablesOpen = !initialVariablesOpen">
              初始变量（可选）
              <ChevronDown class="w-3.5 h-3.5 transition-transform" :class="initialVariablesOpen ? '' : '-rotate-90'" />
            </button>
          </div>

          <div class="flex shrink-0 items-center gap-2">
            <span class="text-xs text-zinc-500 dark:text-zinc-400 hidden sm:inline">
              Enter 发送 / Shift+Enter 换行
            </span>

            <button
              type="button" aria-label="提交测试目标"
              class="w-7 h-7 rounded-md flex items-center justify-center transition-all focus-ring"
              :class="text.trim() && !loading && !readOnly
                ? 'bg-zinc-900 text-white dark:bg-white dark:text-zinc-900 hover:bg-zinc-800 dark:hover:bg-zinc-100 active:scale-95'
                : 'bg-zinc-100 dark:bg-zinc-800 text-zinc-400 dark:text-zinc-600 cursor-not-allowed'"
              :disabled="!text.trim() || loading || readOnly || submitting"
              @click="send"
            >
              <Loader2 v-if="loading" class="w-3.5 h-3.5 animate-spin text-zinc-400" />
              <ArrowUp v-else class="w-3.5 h-3.5 stroke-[2.5]" />
            </button>
          </div>
        </div>

        <div v-show="initialVariablesOpen" :id="variablesId" class="border-t border-zinc-100 dark:border-zinc-800 px-4 pt-2 pb-3 text-xs text-zinc-600 dark:text-zinc-300">
          <label :for="`${variablesId}-input`" class="block mb-1">JSON 对象，供步骤之间传递变量</label>
          <textarea :id="`${variablesId}-input`" v-model="initialVariables" rows="2" class="w-full rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900 p-2 font-mono focus-ring" :disabled="loading || readOnly" />
        </div>

      </div>

    </div>
  </div>
</template>
