<script setup>
import EmbeddingSettings from './EmbeddingSettings.vue'
import { ref, onMounted, onUnmounted } from 'vue'
import { Upload, Trash2, RotateCw, Loader2, Search } from 'lucide-vue-next'
import { api, formatApiError } from '../../api/client'
import { MOCK_KNOWLEDGE_DOCUMENTS, MOCK_RETRIEVAL_RESULTS } from '../../api/mockData'
const props = defineProps({projectId:{type:String,required:true},knowledgeEnabled:{type:Boolean,default:null},isMockMode:Boolean})
const documents = ref([]), retrievalResults = ref([]), searchQuery = ref(''), topK = ref(5)
const isSearching = ref(false), isUploading = ref(false), isLoading = ref(false), uploadError = ref(''), loadError = ref(''), searchError = ref(''), actionId = ref('')
let disposed = false, timer = null, loadVersion = 0
onUnmounted(() => { disposed = true; loadVersion++; clearTimeout(timer) })
const writable = () => !disposed && !props.isMockMode && props.knowledgeEnabled === true && Boolean(props.projectId)
const loadDocuments = async () => {
  if (disposed || isLoading.value || props.knowledgeEnabled !== true || !props.projectId) return
  clearTimeout(timer)
  const version = ++loadVersion
  isLoading.value = true; loadError.value = ''
  try {
    const list = props.isMockMode ? MOCK_KNOWLEDGE_DOCUMENTS : await api.listDocuments(props.projectId)
    if (disposed || version !== loadVersion) return
    documents.value = list || []
    if (!props.isMockMode && documents.value.some(document => document.status === 'PROCESSING')) timer = setTimeout(loadDocuments,2000)
  } catch (failure) { if (!disposed && version === loadVersion) loadError.value = formatApiError(failure) }
  finally { if (!disposed && version === loadVersion) isLoading.value = false }
}
const documentStatus = status => ({INDEXED:'已索引',FAILED:'索引失败',PROCESSING:'处理中'}[status] || '状态未知')
const docTone = status => status === 'INDEXED' ? 'status-ok' : status === 'FAILED' ? 'status-err' : status === 'PROCESSING' ? 'status-busy' : ''
const handleFileUpload = async event => {
  const file = event.target.files?.[0]
  if (!file || !writable() || isUploading.value) return
  isUploading.value = true; uploadError.value = ''
  try { await api.uploadDocument(props.projectId,file); if (!disposed) await loadDocuments() }
  catch (failure) {
    if (!disposed) {
      uploadError.value = formatApiError(failure)
      // 索引失败仍可能留下文档记录，刷新列表且保留本次操作的错误和 requestId。
      await loadDocuments()
    }
  }
  finally { isUploading.value = false; event.target.value = '' }
}
const handleRetry = async id => {
  if (!writable() || isLoading.value || actionId.value) return
  actionId.value = String(id); uploadError.value = ''
  try { await api.retryDocument(props.projectId,id); if (!disposed) await loadDocuments() }
  catch (failure) {
    if (!disposed) {
      uploadError.value = formatApiError(failure)
      await loadDocuments()
    }
  }
  finally { actionId.value = '' }
}
const handleDelete = async id => {
  if (!writable() || actionId.value || !confirm('确定删除该业务文档并移除其知识切片索引吗？')) return
  actionId.value = String(id); uploadError.value = ''
  try { await api.deleteDocument(props.projectId,id); if (!disposed) await loadDocuments() }
  catch (failure) { if (!disposed) uploadError.value = formatApiError(failure) }
  finally { actionId.value = '' }
}
const handleSearch = async () => {
  if (disposed || isSearching.value || !searchQuery.value.trim() || !props.projectId) return
  isSearching.value = true; searchError.value = ''; retrievalResults.value = []
  try {
    const result = props.isMockMode ? MOCK_RETRIEVAL_RESULTS : await api.searchKnowledge(props.projectId,searchQuery.value.trim(),topK.value)
    if (!disposed) retrievalResults.value = result || []
  } catch (failure) { if (!disposed) searchError.value = formatApiError(failure) }
  finally { if (!disposed) isSearching.value = false }
}
onMounted(loadDocuments)
</script>

<template>
  <div class="flex-1 flex min-h-0 overflow-hidden bg-white dark:bg-zinc-950">

    <!-- 边界情形 1：未启用知识库 -->
    <p v-if="knowledgeEnabled === null" role="status" class="m-auto text-sm text-zinc-500">知识库状态尚未读取，请先恢复后端连接。</p>
    <div v-else-if="!knowledgeEnabled" class="card m-auto max-w-md p-6 text-center space-y-2">
      <h3 class="text-sm font-medium text-zinc-900 dark:text-zinc-100">
        当前运行模式未启用业务知识库
      </h3>
      <p class="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed">
        系统概览标记知识库未启用，暂不提供文档索引与知识检索。核心接口测试可独立使用，规划能力以当前模型配置为准。
      </p>
    </div>

    <!-- 边界情形 2：已启用知识库 -->
    <template v-else>
      <!-- 左侧：文档管理列表 (宽 280px) -->
      <aside class="w-72 shrink-0 h-full border-r border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 flex flex-col select-none overflow-hidden">

        <div class="h-12 px-3 border-b border-zinc-200/80 dark:border-zinc-800 flex items-center justify-between gap-2 shrink-0">
          <span class="text-xs font-medium text-zinc-900 dark:text-zinc-100">
            知识库文档
          </span>

          <label class="btn cursor-pointer">
            <Loader2 v-if="isUploading" class="w-3.5 h-3.5 animate-spin" />
            <Upload v-else class="w-3.5 h-3.5" />
            <span>上传文档</span>
            <input
              type="file"
              accept=".pdf,.md,.markdown,.txt,.yaml,.yml"
              class="hidden"
              :disabled="isUploading || isLoading || isMockMode || !projectId"
              @change="handleFileUpload"
            />
          </label>
        </div>

        <div v-if="uploadError || loadError" role="alert" class="mx-3 mt-2 p-2 rounded-lg bg-red-50 dark:bg-red-950/60 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 text-xs">
          <p v-if="uploadError">{{ uploadError }}</p>
          <p v-if="loadError">{{ loadError }}</p>
        </div>

        <p v-if="isMockMode" class="px-3 py-2 meta">预览仅供查看，不执行上传、索引或删除。</p>
        <p v-if="isLoading" role="status" class="px-3 py-2 meta">正在读取文档状态…</p>
        <button v-if="loadError" class="mx-3 mb-2 text-xs underline" :disabled="isLoading" @click="loadDocuments">重试加载</button>
        <!-- 文档列表 -->
        <div class="flex-1 overflow-y-auto p-2 space-y-1">
          <div v-if="documents.length === 0" class="py-12 text-center text-xs text-zinc-400">
            暂无已导入知识库文档
          </div>

          <div
            v-for="doc in documents"
            :key="doc.id"
            class="px-2.5 py-2 rounded-lg hover:bg-zinc-100 dark:hover:bg-zinc-900 transition-colors text-xs space-y-1"
          >
            <div class="flex items-center justify-between gap-2">
              <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100 truncate flex-1">
                {{ doc.fileName }}
              </span>
              <span class="status shrink-0" :class="docTone(doc.status)">
                {{ documentStatus(doc.status) }}
              </span>
            </div>

            <p v-if="doc.errorMessage" class="text-red-600 dark:text-red-400 break-words">{{ doc.errorMessage }}</p>
            <div class="flex items-center justify-between meta font-mono">
              <span>{{ doc.chunkCount || 0 }} 个文本切片</span>
              <div class="flex items-center gap-2">
                <button
                  v-if="doc.status === 'FAILED'"
                  type="button"
                  class="hover:text-zinc-900 dark:hover:text-zinc-100"
                  :disabled="isMockMode || isLoading || !!actionId" aria-label="重试文档索引" @click="handleRetry(doc.id)"
                >
                  <RotateCw class="w-3.5 h-3.5" />
                </button>
                <button
                  type="button"
                  class="text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200 transition-colors"
                  :disabled="isMockMode || !!actionId" aria-label="删除文档" @click="handleDelete(doc.id)"
                >
                  <Trash2 class="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 右侧：混合检索验证控制台 -->
      <main class="flex-1 flex flex-col h-full overflow-hidden min-w-0">

        <EmbeddingSettings :is-mock-mode="isMockMode" />
        <!-- 检索调试顶栏 -->
        <div class="p-6 border-b border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-950 shrink-0 space-y-3">
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100 tracking-tight">
              知识库混合检索调试
            </h3>
            <span class="meta font-mono">
              关键词召回 + 当前嵌入模型
            </span>
          </div>

          <div class="flex items-center gap-3">
            <div class="relative flex-1">
              <Search class="w-4 h-4 text-zinc-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                v-model="searchQuery"
                type="text"
                placeholder="输入检索自然语言问题，例如：退款金额必须满足什么约束？"
                class="w-full pl-9 pr-3 py-1.5 text-xs rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100 placeholder-zinc-400 focus-ring"
                @keydown.enter.prevent="handleSearch"
              />
            </div>

            <button
              type="button"
              class="btn-primary"
              :disabled="isSearching || !searchQuery.trim()"
              @click="handleSearch"
            >
              <Loader2 v-if="isSearching" class="w-3.5 h-3.5 animate-spin" />
              <span>{{ isSearching ? '检索中...' : '测试检索' }}</span>
            </button>
          </div>
          <p v-if="searchError" role="alert" class="text-sm text-red-600 dark:text-red-400">{{ searchError }}</p>
        </div>

        <!-- 检索结果列表 -->
        <div class="flex-1 overflow-y-auto p-6">
          <div class="max-w-4xl mx-auto space-y-2">
            <div v-if="retrievalResults.length === 0" class="py-20 text-center text-xs text-zinc-400">
              输入问题后点击“测试检索”，查看真实混合检索返回的切片与引用
            </div>

            <div
              v-for="(res, idx) in retrievalResults"
              :key="idx"
              class="card p-4 text-xs space-y-2"
            >
              <div class="flex items-center justify-between gap-2">
                <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100 font-mono">
                  #{{ idx + 1 }} · {{ res.citation || res.sourceName }}
                </span>
                <span class="meta font-mono">
                  得分 {{ Number.isFinite(res.fusedScore) ? res.fusedScore.toFixed(3) : '未返回' }}
                </span>
              </div>

              <div v-if="res.section" class="meta font-sans">
                所属章节: {{ res.section }}
              </div>

              <p class="text-sm text-zinc-700 dark:text-zinc-300 leading-relaxed font-mono p-2.5 rounded-lg bg-zinc-50 dark:bg-zinc-950 border border-zinc-100 dark:border-zinc-850">
                {{ res.content }}
              </p>
            </div>
          </div>
        </div>

      </main>
    </template>

  </div>
</template>
