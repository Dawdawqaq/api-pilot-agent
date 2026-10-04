<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { api, formatApiError } from '../../api/client'
import Dropdown from '../common/Dropdown.vue'
import HistorySearch from '../common/HistorySearch.vue'
const props = defineProps({projectId:String,environmentId:String,isMockMode:Boolean,executionId:{type:String,default:''}})
const endpoints = ref([]), endpointId = ref(''), cases = ref([]), replay = ref(null), result = ref(null)
const samples = ref([]), query = ref(''), executionFilter = ref(props.executionId), total = ref(0), cursor = ref(null), more = ref(false)
const busy = ref(false), loading = ref(false), error = ref('')
let disposed = false
onUnmounted(() => { disposed = true })
const options = computed(() => endpoints.value.map(item => ({value:String(item.id),label:`${item.httpMethod} ${item.path}`})))
const method = computed(() => String(replay.value?.request?.method || '').toUpperCase())
const readOnly = computed(() => ['GET','HEAD','OPTIONS'].includes(method.value))
const run = async operation => {
  if (props.isMockMode || busy.value || !props.projectId) return
  error.value = ''; busy.value = true
  try { await operation() } catch (failure) { if (!disposed) error.value = formatApiError(failure) }
  finally { if (!disposed) busy.value = false }
}
async function load(search = query.value, append = false) {
  if (props.isMockMode || loading.value || !props.projectId) return
  loading.value = true; error.value = ''
  try {
    const page = await api.getReplays(props.projectId,{executionId:executionFilter.value,query:search,beforeId:append ? cursor.value : ''})
    if (disposed) return
    samples.value = append ? [...new Map([...samples.value,...page.items].map(item => [String(item.id),item])).values()] : page.items
    query.value = search; total.value = Number(page.total); cursor.value = page.nextCursor; more.value = page.hasMore
    if (!append) { replay.value = page.items[0] || null; result.value = null }
  } catch (failure) { if (!disposed) error.value = formatApiError(failure) }
  finally { if (!disposed) loading.value = false }
}
const generate = () => run(async () => { const rows = await api.getNegativeCases(props.projectId,endpointId.value); if (!disposed) cases.value = rows })
const select = sample => { if (busy.value) return; replay.value = sample; result.value = null; error.value = '' }
const execute = () => run(async () => {
  if (!readOnly.value || !props.environmentId) return
  result.value = null
  const response = await api.replayFailure(props.projectId,replay.value.id,props.environmentId)
  if (!disposed) result.value = response
})
const typeText = value => ({MISSING_REQUIRED:'缺少必填参数',WRONG_TYPE:'参数类型错误',BOUNDARY_LENGTH:'长度边界',INVALID_ENUM:'不符合枚举'}[value] || value)
onMounted(async () => {
  await load()
  await run(async () => { const rows = await api.listEndpoints(props.projectId); if (!disposed) endpoints.value = rows })
})
</script>
<template>
  <main class="max-w-6xl w-full mx-auto px-6 py-8 space-y-8 text-sm">
    <header><h1 class="text-lg font-semibold">契约与回放</h1><p class="mt-2 text-zinc-600 dark:text-zinc-400">从真实失败样本查看请求与断言，在当前所选环境重新验证只读请求。</p></header>
    <p v-if="isMockMode" role="status" class="meta">预览模式不读取真实样本或执行回放。</p>
    <p v-if="!projectId" class="meta">请先选择项目。</p>
    <p v-if="error" role="alert" class="text-red-600 dark:text-red-400">{{ error }}</p>
    <section class="space-y-3" aria-labelledby="replays-heading">
      <div class="flex justify-between gap-4 items-center"><h2 id="replays-heading" class="font-semibold">失败样本</h2><button class="btn" :disabled="isMockMode || loading || busy" @click="load()">刷新样本</button></div>
      <div v-if="executionFilter" class="flex items-center gap-3 meta"><span>来自执行 {{ executionFilter }}</span><button class="underline" :disabled="loading || busy" @click="executionFilter = ''; load()">查看当前项目全部样本</button></div>
      <div class="grid grid-cols-[18rem_minmax(0,1fr)] gap-6">
        <aside class="border-r border-zinc-200 dark:border-zinc-800 pr-4">
          <HistorySearch label="搜索失败原因" :query="query" :busy="loading || isMockMode" :total="total" :loaded="samples.length" @search="text => load(text)" />
          <p v-if="loading" role="status" class="py-3 meta">正在读取样本…</p>
          <p v-if="!loading && !samples.length" class="py-5 meta">{{ executionFilter ? '该执行没有保存失败回放样本。' : '当前项目暂无失败回放样本。' }}</p>
          <button v-for="sample in samples" :key="sample.id" class="block w-full text-left p-3 rounded-lg focus-ring hover:bg-zinc-100 dark:hover:bg-zinc-900" :class="{'bg-zinc-100 dark:bg-zinc-900':String(replay?.id) === String(sample.id)}" :disabled="busy" @click="select(sample)">
            <p class="line-clamp-2">{{ sample.errorSummary }}</p><p class="mt-1 meta font-mono">{{ sample.request?.method }} {{ sample.request?.path }}</p><p class="mt-1 meta">步骤 {{ sample.stepIndex + 1 }} · {{ sample.createdAt }}</p>
          </button>
          <button v-if="more" class="btn mt-3 w-full" :disabled="loading || busy" @click="load(query,true)">加载更多样本</button>
        </aside>
        <div v-if="replay" class="min-w-0 space-y-4">
          <div><h3 class="font-semibold">{{ replay.errorSummary }}</h3><p class="meta mt-2">样本 {{ replay.id }} · 执行 {{ replay.executionId }}</p></div>
          <p class="font-mono break-all">{{ replay.request?.method }} {{ replay.request?.path }}</p>
          <div v-if="replay.request?.assertions?.length"><h4 class="font-medium mb-2">原始断言</h4><ul class="space-y-1"><li v-for="(assertion,index) in replay.request.assertions" :key="index" class="font-mono text-sm break-all">{{ assertion.type }} {{ assertion.jsonPath || '' }} · 期望 {{ JSON.stringify(assertion.expectedValue) }}</li></ul></div>
          <details class="border-y border-zinc-200 dark:border-zinc-800 py-3"><summary class="cursor-pointer">脱敏请求参数</summary><pre class="mt-3 font-mono text-xs whitespace-pre-wrap break-all max-h-72 overflow-auto">{{ JSON.stringify(replay.request,null,2) }}</pre></details>
          <p class="meta">敏感运行数据有保存期限。回放会在当前环境重新发送一次请求，不会覆盖原失败记录。</p>
          <p v-if="!readOnly" class="text-amber-700 dark:text-amber-300">写请求请在工作台创建新任务并重新确认，避免重复写入。</p>
          <button class="btn-primary" :disabled="!environmentId || busy || isMockMode || !readOnly" @click="execute">{{ busy ? '正在回放…' : '在当前环境回放只读请求' }}</button>
          <section v-if="result" class="border-t border-zinc-200 dark:border-zinc-800 pt-4 space-y-3" aria-label="回放结果">
            <p :class="['status',result.status === 'SUCCEEDED' ? 'status-ok' : 'status-err']">{{ result.status === 'SUCCEEDED' ? '本次回放通过' : '本次回放未通过' }}</p><p class="meta">执行 {{ result.id }} · {{ result.durationMs }}ms</p><p v-if="result.errorMessage">{{ result.errorMessage }}</p>
            <div v-for="step in result.steps" :key="step.stepIndex" class="space-y-2"><p class="font-medium">{{ step.name }} · HTTP {{ step.responseStatus }}</p><p v-for="(assertion,index) in step.assertions" :key="index" :class="['status',assertion.passed ? 'status-ok' : 'status-err']">{{ assertion.message || `${assertion.type} ${assertion.jsonPath || ''}` }}</p><details><summary class="cursor-pointer meta">响应数据</summary><pre class="mt-2 text-xs whitespace-pre-wrap break-all max-h-60 overflow-auto">{{ JSON.stringify(step.responseBody,null,2) }}</pre></details></div>
          </section>
        </div>
      </div>
    </section>
    <section class="border-t border-zinc-200 dark:border-zinc-800 pt-6 space-y-4" aria-labelledby="negative-heading">
      <h2 id="negative-heading" class="font-semibold">契约负向用例建议</h2><p class="meta">根据当前接口定义生成建议，查询本身不会向目标服务发送测试请求。</p>
      <fieldset class="flex items-center gap-3" :disabled="isMockMode || busy"><span id="negative-endpoint-label">选择接口</span><Dropdown v-model="endpointId" :options="options" placeholder="请选择接口" aria-labelledby="negative-endpoint-label" /><button class="btn" :disabled="!endpointId" @click="generate">查询建议</button></fieldset>
      <table v-if="cases.length" class="w-full text-left"><thead><tr class="border-b border-zinc-200 dark:border-zinc-800"><th class="py-2">类型 / 参数</th><th>变更方式</th><th>预期结果</th></tr></thead><tbody><tr v-for="(item,index) in cases" :key="index" class="border-b border-zinc-100 dark:border-zinc-800"><td class="py-3 pr-4">{{ typeText(item.caseType) }}<span class="block meta font-mono">{{ item.target }}</span></td><td class="pr-4">{{ item.mutation }}</td><td>{{ item.expectedOutcome }}</td></tr></tbody></table>
    </section>
  </main>
</template>
