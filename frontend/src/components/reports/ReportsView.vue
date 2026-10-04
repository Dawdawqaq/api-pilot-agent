<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { Download, ChevronDown } from 'lucide-vue-next'
import { api, formatApiError } from '../../api/client'
import { MOCK_TEST_REPORTS, MOCK_REPORT_DETAIL } from '../../api/mockData'
import HistorySearch from '../common/HistorySearch.vue'
const props = defineProps({projectId:{type:String,required:true},isMockMode:Boolean})
const emit = defineEmits(['open-replays'])
const reports = ref([]), selectedReportId = ref(''), reportDetail = ref(null), expandedSteps = ref({})
const isExporting = ref(false), isLoading = ref(false), detailLoading = ref(false), error = ref('')
const historyQuery = ref(''), total = ref(0), nextCursor = ref(null), hasMore = ref(false)
let disposed = false, detailVersion = 0
onUnmounted(() => { disposed = true; detailVersion++ })
const getStatusBadge = status => ({text:{SUCCEEDED:'全部通过',NEEDS_REVIEW:'需人工核验',FAILED:'未通过',CANCELLED:'已取消'}[status] || '未知状态'})
const reportTone = status => ({SUCCEEDED:'status-ok',FAILED:'status-err',NEEDS_REVIEW:'status-warn'}[status] || '')
const loadReportDetail = async id => {
  const version = ++detailVersion
  selectedReportId.value = String(id); reportDetail.value = null; expandedSteps.value = {}; error.value = ''; detailLoading.value = true
  try {
    const detail = props.isMockMode
      ? String(id) === String(MOCK_REPORT_DETAIL.id) ? MOCK_REPORT_DETAIL : {...reports.value.find(report => String(report.id) === String(id)),steps:[]}
      : await api.getReport(props.projectId,id)
    if (disposed || version !== detailVersion) return
    reportDetail.value = detail
    if (detail?.steps?.length) expandedSteps.value = {[detail.steps[0].id]:true}
  } catch (failure) { if (!disposed && version === detailVersion) error.value = formatApiError(failure) }
  finally { if (!disposed && version === detailVersion) detailLoading.value = false }
}
const loadReports = async (query = historyQuery.value, more = false) => {
  if (isLoading.value || !props.projectId) return
  isLoading.value = true; error.value = ''
  if (!more) {
    detailVersion++; reports.value = []; reportDetail.value = null; selectedReportId.value = ''
    historyQuery.value = query
  }
  try {
    const page = props.isMockMode
      ? {items:MOCK_TEST_REPORTS.filter(report => report.title.toLowerCase().includes(query.toLowerCase())),hasMore:false,nextCursor:null}
      : await api.getReportHistory(props.projectId,{query,beforeId:more ? nextCursor.value : null})
    if (disposed) return
    const rows = more ? [...reports.value,...page.items] : page.items
    reports.value = [...new Map(rows.map(report => [String(report.id),report])).values()]
    historyQuery.value = query; total.value = Number(page.total ?? page.items.length)
    hasMore.value = page.hasMore; nextCursor.value = page.nextCursor
    const selected = reports.value.find(report => String(report.id) === selectedReportId.value) || reports.value[0]
    if (selected && (!more || !reportDetail.value)) await loadReportDetail(selected.id)
    else if (!selected) { selectedReportId.value = ''; reportDetail.value = null }
  } catch (failure) { if (!disposed) error.value = formatApiError(failure) }
  finally { if (!disposed) isLoading.value = false }
}
const handleExportJUnit = async () => {
  if (props.isMockMode || !selectedReportId.value || isExporting.value || detailLoading.value) return
  const id = selectedReportId.value
  isExporting.value = true; error.value = ''
  try {
    const content = await api.exportJUnitXml(props.projectId,id)
    if (disposed) return
    const url = URL.createObjectURL(new Blob([content],{type:'application/xml;charset=utf-8'}))
    const link = document.createElement('a'); link.href = url; link.download = `junit-report-${id}.xml`; link.click()
    setTimeout(() => URL.revokeObjectURL(url),1000)
  } catch (failure) { if (!disposed) error.value = formatApiError(failure) }
  finally { isExporting.value = false }
}
const toggleStep = id => { expandedSteps.value[id] = !expandedSteps.value[id] }
onMounted(() => loadReports())
</script>

<template>
  <div class="flex-1 flex min-h-0 overflow-hidden bg-white dark:bg-zinc-950">

    <!-- 左侧：报告列表 (宽 280px) -->
    <aside class="w-72 shrink-0 h-full border-r border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 flex flex-col select-none overflow-hidden">

      <div class="h-12 px-3 border-b border-zinc-200/80 dark:border-zinc-800 flex items-center justify-between shrink-0">
        <span class="text-xs font-medium text-zinc-900 dark:text-zinc-100">
          测试执行报告
        </span>
        <span class="meta font-mono">
          共 {{ total }} 份
        </span>
      </div>

      <HistorySearch label="搜索报告标题" :query="historyQuery" :busy="isLoading" :total="total" :loaded="reports.length" @search="query => loadReports(query)" />

      <p v-if="error" role="alert" class="px-3 py-2 text-xs text-red-600 dark:text-red-400">{{ error }} <button class="underline" :disabled="isLoading" @click="loadReports()">重试加载</button></p>
      <p v-if="isLoading || detailLoading" role="status" class="px-3 py-2 meta">正在读取报告…</p>
      <div class="flex-1 overflow-y-auto p-2 space-y-1">
        <div v-if="reports.length === 0" class="py-12 text-center text-xs text-zinc-400">
          {{ historyQuery ? '没有匹配的报告' : '暂无已归档测试报告' }}
        </div>

        <button
          v-for="rep in reports"
          :key="rep.id"
          type="button"
          class="w-full text-left px-2.5 py-2 rounded-lg transition-colors focus-ring"
          :class="String(rep.id) === String(selectedReportId)
            ? 'bg-zinc-200/70 dark:bg-zinc-800'
            : 'hover:bg-zinc-100 dark:hover:bg-zinc-900'"
          @click="loadReportDetail(rep.id)"
        >
          <div class="flex items-center justify-between gap-2">
            <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100 truncate">
              {{ rep.title }}
            </span>
            <span class="status shrink-0" :class="reportTone(rep.status)">
              {{ getStatusBadge(rep.status).text }}
            </span>
          </div>

          <div class="mt-1 flex items-center justify-between meta font-mono">
            <span>通过 {{ rep.passedSteps }}/{{ rep.totalSteps }} 步</span>
            <span>{{ rep.durationMs }}ms</span>
          </div>
        </button>
        <button v-if="hasMore" class="btn w-full" :disabled="isLoading" @click="loadReports(historyQuery,true)">{{ isLoading ? '正在读取…' : '加载更多报告' }}</button>
      </div>
    </aside>

    <!-- 右侧：报告详情与下载 -->
    <main class="flex-1 flex flex-col h-full overflow-hidden min-w-0">

      <!-- 报告头部信息与操作 -->
      <div v-if="reportDetail" class="p-6 border-b border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-950 shrink-0 flex items-start justify-between gap-4">
        <div class="space-y-1.5 max-w-3xl">
          <div class="flex items-center gap-3">
            <span class="status" :class="reportTone(reportDetail.status)">
              {{ getStatusBadge(reportDetail.status).text }}
            </span>
            <span class="meta font-mono">
              Report #{{ reportDetail.id }} · Task #{{ reportDetail.taskId }} · 总耗时 {{ reportDetail.durationMs }}ms
            </span>
          </div>

          <h2 class="text-base font-semibold text-zinc-900 dark:text-zinc-100 tracking-tight leading-snug">
            {{ reportDetail.title }}
          </h2>

          <p class="text-sm text-zinc-600 dark:text-zinc-400 leading-relaxed">
            {{ reportDetail.summary }}
          </p>

          <!-- 证据引用 -->
          <div v-if="reportDetail.evidenceCitations && reportDetail.evidenceCitations.length > 0" class="flex flex-wrap items-center gap-2 pt-1 text-xs">
            <span class="meta">依据引用:</span>
            <span
              v-for="(cite, idx) in reportDetail.evidenceCitations"
              :key="idx"
              class="meta font-mono"
            >
              {{ cite }}
            </span>
          </div>
        </div>

        <div class="flex flex-col items-end gap-2 shrink-0">
        <button v-if="reportDetail.executionId && reportDetail.status !== 'SUCCEEDED'" class="btn" :disabled="isMockMode" @click="emit('open-replays',reportDetail.executionId)">查看失败回放</button>
        <!-- 一键导出真实 JUnit XML -->
        <button
          type="button"
          class="btn-primary"
          :disabled="isMockMode || isExporting || detailLoading"
          @click="handleExportJUnit"
        >
          <Download class="w-3.5 h-3.5" />
          <span>{{ isExporting ? '导出中...' : '导出 JUnit XML' }}</span>
        </button>
        </div>
      </div>

      <!-- 步骤执行详情流 -->
      <div v-if="reportDetail" class="flex-1 overflow-y-auto p-6">
        <div class="max-w-4xl mx-auto space-y-3">
          <div class="text-sm font-medium text-zinc-900 dark:text-zinc-100 mb-2">
            执行步骤与断言明细 <span class="meta">· 共 {{ reportDetail.steps?.length || 0 }} 步</span>
          </div>

          <div
            v-for="step in reportDetail.steps"
            :key="step.id"
            class="card overflow-hidden text-xs"
          >
            <!-- 步骤概览行 -->
            <button type="button" :aria-expanded="expandedSteps[step.id]"
              class="w-full text-left px-4 py-2.5 flex items-center justify-between gap-3 hover:bg-zinc-50 dark:hover:bg-zinc-800/50 transition-colors focus-ring"
              @click="toggleStep(step.id)"
            >
              <div class="flex items-center gap-3 min-w-0">
                <span class="status" :class="step.success ? 'status-ok' : 'status-err'"></span>

                <span class="meta font-mono">
                  步骤 {{ reportDetail.steps.indexOf(step) + 1 }}
                </span>

                <span class="font-mono font-semibold text-xs text-zinc-700 dark:text-zinc-300 uppercase">
                  {{ step.httpMethod }}
                </span>

                <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100 truncate">
                  {{ step.stepName }}
                </span>

                <span class="meta font-mono truncate hidden md:inline">
                  {{ step.requestUrl }}
                </span>
              </div>

              <div class="flex items-center gap-3 meta font-mono shrink-0">
                <span v-if="step.responseStatus">
                  HTTP {{ step.responseStatus }}
                </span>
                <span>{{ step.durationMs }}ms</span>
                <ChevronDown class="w-4 h-4 text-zinc-400 transition-transform" :class="expandedSteps[step.id] ? '' : '-rotate-90'" />
              </div>
            </button>

            <!-- 内嵌展开：请求参数、响应体与断言 -->
            <div v-if="expandedSteps[step.id]" class="px-4 py-3 border-t border-zinc-100 dark:border-zinc-800 space-y-3">
              <!-- 断言列表 -->
              <div v-if="step.assertions && step.assertions.length > 0" class="space-y-1.5">
                <div class="meta font-medium">断言校验:</div>
                <div class="space-y-1">
                  <div
                    v-for="(ast, idx) in step.assertions"
                    :key="idx"
                    class="flex items-center gap-2 text-xs font-mono text-zinc-700 dark:text-zinc-300"
                  >
                    <span class="status" :class="ast.passed ? 'status-ok' : 'status-err'"></span>
                    <span class="text-zinc-500 font-medium">[{{ ast.type }}]</span>
                    <span class="break-words">{{ ast.message || ast.type }} · 预期 {{ ast.expected }} · 实际 {{ ast.actual }}</span>
                  </div>
                </div>
              </div>

              <!-- 抓包 Payload 预览 -->
              <div class="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs font-mono">
                <div>
                  <span class="meta block mb-1">Request Payload</span>
                  <pre class="p-2.5 rounded-lg bg-zinc-50 dark:bg-zinc-950 text-zinc-800 dark:text-zinc-200 overflow-x-auto max-h-48 border border-zinc-200 dark:border-zinc-800">{{ JSON.stringify({ url: step.requestUrl, headers: step.requestHeaders, body: step.requestBody }, null, 2) }}</pre>
                </div>
                <div>
                  <span class="meta block mb-1">Response Body</span>
                  <pre class="p-2.5 rounded-lg bg-zinc-50 dark:bg-zinc-950 text-zinc-800 dark:text-zinc-200 overflow-x-auto max-h-48 border border-zinc-200 dark:border-zinc-800">{{ JSON.stringify(step.responseBody, null, 2) }}</pre>
                </div>
              </div>
            </div>

          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="m-auto py-24 text-center text-xs text-zinc-400">
        请在左侧选择测试报告查看详情
      </div>

    </main>

  </div>
</template>
