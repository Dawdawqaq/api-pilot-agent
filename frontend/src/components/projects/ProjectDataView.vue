<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api, formatApiError } from '../../api/client'
const props = defineProps({projectId:String,projectCode:String,isMockMode:Boolean})
const emit = defineEmits(['updated'])
const counts = ref(null), recycled = ref([]), days = ref(30), preview = ref(null), confirmation = ref('')
const busy = ref(false), error = ref(''), message = ref('')
const recycleQuery = ref('')
const filteredRecycled = computed(() => {
  const query = recycleQuery.value.trim().toLowerCase()
  return recycled.value.filter(project => `${project.name} ${project.code}`.toLowerCase().includes(query))
})
const cleanupBefore = computed(() => String(preview.value?.before || '').replace('T',' ').slice(0,16))
let disposed = false
onUnmounted(() => { disposed = true })
const names = {agent_task:'任务',test_report:'报告',api_execution:'执行记录',failure_replay_sample:'失败样本',openapi_import:'接口导入历史',knowledge_document:'文档记录',knowledge_chunk:'业务切片'}
async function load() {
  if (props.isMockMode || busy.value) return
  busy.value = true; error.value = ''
  try {
    const results = await Promise.allSettled([props.projectId ? api.getProjectData(props.projectId) : Promise.resolve(null),api.listRecycledProjects()])
    if (disposed) return
    if (results[0].status === 'fulfilled') counts.value = results[0].value
    if (results[1].status === 'fulfilled') recycled.value = results[1].value
    error.value = results.filter(result => result.status === 'rejected').map(result => formatApiError(result.reason)).join('；')
  } finally { if (!disposed) busy.value = false }
}
async function inspect() {
  if (props.isMockMode || busy.value || !props.projectId) return
  busy.value = true; error.value = ''; message.value = ''; preview.value = null; confirmation.value = ''
  try { const result = await api.previewCleanup(props.projectId,days.value); if (!disposed) preview.value = result }
  catch (failure) { if (!disposed) error.value = formatApiError(failure) }
  finally { if (!disposed) busy.value = false }
}
async function clean() {
  if (props.isMockMode || busy.value || !preview.value || confirmation.value !== props.projectCode) return
  busy.value = true; error.value = ''
  try {
    const result = await api.cleanProjectData(props.projectId,{previewId:preview.value.previewId,projectCode:confirmation.value})
    if (disposed) return
    preview.value = null; confirmation.value = ''
    message.value = `已清理 ${result.tasks} 个任务、${result.reports} 份报告和 ${result.executions} 次执行。`
    counts.value = await api.getProjectData(props.projectId)
    emit('updated',props.projectId)
  } catch (failure) { if (!disposed) { error.value = formatApiError(failure); preview.value = null } }
  finally { if (!disposed) busy.value = false }
}
async function restore(id) {
  if (props.isMockMode || busy.value) return
  busy.value = true; error.value = ''
  try { await api.restoreProject(id); if (!disposed) emit('updated',String(id)) }
  catch (failure) { if (!disposed) error.value = formatApiError(failure) }
  finally { if (!disposed) busy.value = false }
}
onMounted(load)
</script>
<template>
  <section class="border-t border-zinc-200 dark:border-zinc-800 pt-6 space-y-4" aria-labelledby="data-heading">
    <div class="flex justify-between items-center"><h2 id="data-heading" class="font-semibold">数据与保留</h2><button class="btn" :disabled="isMockMode || busy" @click="load">刷新统计</button></div>
    <p v-if="error" role="alert" class="text-red-600 dark:text-red-400">{{ error }}</p><p v-if="busy" role="status" class="meta">正在处理…</p><p v-if="message" role="status" class="status status-ok">{{ message }}</p>
    <dl v-if="counts" class="grid grid-cols-4 gap-x-6 gap-y-3"><div v-for="(name,key) in names" :key="key"><dt class="meta">{{ name }}</dt><dd class="mt-1 font-mono">{{ counts[key] }}</dd></div><div><dt class="meta">当前文档原始大小</dt><dd class="mt-1 font-mono">{{ (Number(counts.documentBytes) / 1024 / 1024).toFixed(2) }} MB</dd></div></dl>
    <p class="meta">数量包含留存的历史记录。文档大小来自文件记录，不能视为数据库、向量库或磁盘的实际占用；数据库清理后，文件大小也不一定立即缩小。</p>
    <div class="space-y-3"><h3 class="font-medium">清理旧历史</h3><p class="text-zinc-600 dark:text-zinc-400">保留接口资料、环境、业务文档、模型配置与待核验结果。清理删除旧终态任务、报告、执行记录及关联审计，无法在页面撤销。</p>
      <form class="flex gap-3 items-center" @submit.prevent="inspect"><label class="flex items-center gap-2">保留最近<input v-model.number="days" type="number" min="7" max="3650" required class="w-20 border border-zinc-200 dark:border-zinc-700 rounded-lg p-2 dark:bg-zinc-900" :disabled="isMockMode || busy" />天</label><button class="btn" :disabled="isMockMode || !projectId || busy">预览清理范围</button></form>
      <div v-if="preview" class="border-y border-zinc-200 dark:border-zinc-800 py-4 space-y-3"><p>将清理 {{ cleanupBefore }} 之前的 {{ preview.tasks }} 个任务、{{ preview.reports }} 份报告、{{ preview.executions }} 次执行。</p><p class="meta">{{ preview.message }}预览有效期 5 分钟。</p><label class="block">输入项目编码 <span class="font-mono">{{ projectCode }}</span> 确认<input v-model="confirmation" autocomplete="off" class="block mt-2 border border-zinc-200 dark:border-zinc-700 rounded-lg px-3 py-2 dark:bg-zinc-900" :disabled="busy" /></label><button class="btn" :disabled="busy || confirmation !== projectCode || !(preview.tasks || preview.reports || preview.executions)" @click="clean">确认永久清理本批历史</button></div>
    </div>
    <div class="space-y-2"><h3 class="font-medium">可恢复备份</h3><p class="text-zinc-600 dark:text-zinc-400">清理前建议停止应用，在项目终端执行完整备份。备份包含 DocHelper 数据库、原始文档和加密密钥文件；恢复后可重建向量索引。</p><pre class="font-mono text-xs whitespace-pre-wrap break-all">.\scripts\app.ps1 stop
.\scripts\backup.ps1 backup
.\scripts\app.ps1 start</pre><p class="meta">备份位置为 .local-notes/backups。恢复命令与校验步骤见 docs/personal-operations.md。</p></div>
    <div class="space-y-3"><h3 class="font-medium">项目回收站</h3><p class="meta">移入回收站只隐藏项目，数据与文件仍保留；编码继续保留。归档项目仍在项目列表，可通过编辑重新启用。</p><label v-if="recycled.length" class="flex gap-3 items-center">搜索回收站项目<input v-model="recycleQuery" type="search" class="border border-zinc-200 dark:border-zinc-700 rounded-lg px-3 py-2 dark:bg-zinc-900" placeholder="名称或项目编码" /><span class="meta">{{ filteredRecycled.length }} / {{ recycled.length }} 个项目</span></label><p v-if="!filteredRecycled.length" class="meta">{{ recycled.length ? '没有匹配的项目。' : '回收站为空。' }}</p><div class="max-h-64 overflow-y-auto"><div v-for="project in filteredRecycled" :key="project.id" class="flex justify-between items-center gap-4 border-b border-zinc-100 dark:border-zinc-800 py-2"><span>{{ project.name }} <span class="meta font-mono ml-2">{{ project.code }}</span></span><button class="btn shrink-0" :aria-label="`恢复项目 ${project.name}`" :disabled="busy || isMockMode" @click="restore(project.id)">恢复项目</button></div></div></div>
  </section>
</template>
