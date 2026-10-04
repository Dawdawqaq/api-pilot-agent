<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Upload, Search, ChevronDown, ArrowRight, Loader2 } from 'lucide-vue-next'
import { api, formatApiError } from '../../api/client'
import { MOCK_OPENAPI_IMPORTS, MOCK_ENDPOINTS, MOCK_DEPENDENCY_EDGES } from '../../api/mockData'

const props = defineProps({projectId:{type:String,required:true},isMockMode:Boolean})
const imports = ref([]), endpoints = ref([]), dependencies = ref([])
const selectedImportId = ref(''), searchQuery = ref(''), selectedTag = ref(''), expandedEndpointId = ref('')
const isUploading = ref(false), isLoading = ref(false), retryingId = ref('')
const uploadError = ref(''), loadError = ref(''), activeSubTab = ref('endpoints')
let loadVersion = 0, endpointVersion = 0, disposed = false
onUnmounted(() => { disposed = true; loadVersion++; endpointVersion++ })

// 历史版本必须传 importId 查询，不能在当前版本端点上做本地过滤冒充历史数据。
const selectImport = async id => {
  selectedImportId.value = String(id || '')
  selectedTag.value = ''; expandedEndpointId.value = ''; endpoints.value = []
  const version = ++endpointVersion
  if (!id || !props.projectId) return
  if (props.isMockMode) { endpoints.value = MOCK_ENDPOINTS.filter(e => String(e.importId) === String(id)); return }
  try {
    const list = await api.listEndpoints(props.projectId,id)
    if (!disposed && version === endpointVersion) endpoints.value = list || []
  } catch (error) { if (!disposed && version === endpointVersion) loadError.value = formatApiError(error) }
}
const loadData = async preferredId => {
  if (isLoading.value || !props.projectId) return
  const version = ++loadVersion
  isLoading.value = true; loadError.value = ''
  try {
    if (props.isMockMode) {
      imports.value = MOCK_OPENAPI_IMPORTS; dependencies.value = MOCK_DEPENDENCY_EDGES
    } else {
      const [versions,edges] = await Promise.allSettled([api.listOpenApiImports(props.projectId),api.getDependencies(props.projectId)])
      if (disposed || version !== loadVersion) return
      const errors = []
      imports.value = versions.status === 'fulfilled' ? versions.value || [] : []
      dependencies.value = edges.status === 'fulfilled' ? edges.value || [] : []
      for (const result of [versions,edges]) if (result.status === 'rejected') errors.push(formatApiError(result.reason))
      loadError.value = errors.join('；')
    }
    const target = imports.value.find(imp => String(imp.id) === String(preferredId || selectedImportId.value)) || imports.value[0]
    await selectImport(target?.id)
  } finally { if (!disposed && version === loadVersion) isLoading.value = false }
}
const filteredEndpoints = computed(() => endpoints.value.filter(endpoint => {
  const query = searchQuery.value.trim().toLowerCase()
  return (!selectedTag.value || endpoint.tags?.includes(selectedTag.value)) && (!query || [endpoint.path,endpoint.summary,endpoint.operationId].some(value => value?.toLowerCase().includes(query)))
}))
const allTags = computed(() => [...new Set(endpoints.value.flatMap(endpoint => Array.isArray(endpoint.tags) ? endpoint.tags : []))])
const handleFileUpload = async event => {
  const file = event.target.files?.[0]
  if (!file || isUploading.value || props.isMockMode || !props.projectId) return
  uploadError.value = ''
  if (!/\.(json|yaml|yml)$/i.test(file.name)) { uploadError.value = '仅支持 JSON、YAML 和 YML 文件'; event.target.value = ''; return }
  isUploading.value = true
  try { const result = await api.uploadOpenApi(props.projectId,file); if (!disposed) await loadData(result.id) }
  catch (error) {
    if (!disposed) {
      uploadError.value = formatApiError(error)
      // 解析失败也可能已保存导入记录，重新读取才能立即提供真实重试入口。
      await loadData()
    }
  }
  finally { isUploading.value = false; event.target.value = '' }
}
const handleRetry = async id => {
  if (props.isMockMode || isLoading.value || retryingId.value || !props.projectId) return
  retryingId.value = String(id); uploadError.value = ''
  try { const result = await api.retryOpenApiImport(props.projectId,id); if (!disposed) await loadData(result.id) }
  catch (error) {
    if (!disposed) {
      uploadError.value = formatApiError(error)
      await loadData()
    }
  }
  finally { retryingId.value = '' }
}
const toggleEndpoint = id => { expandedEndpointId.value = expandedEndpointId.value === id ? '' : id }
onMounted(() => loadData())
</script>

<template>
  <div class="flex-1 flex min-h-0 overflow-hidden bg-white dark:bg-zinc-950">

    <!-- 左侧：OpenAPI 导入管理面板 (极简纯净，固定宽 280px) -->
    <aside class="w-72 shrink-0 h-full border-r border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 flex flex-col select-none overflow-hidden">

      <!-- 头部：上传按钮 -->
      <div class="h-12 px-3 border-b border-zinc-200/80 dark:border-zinc-800 flex items-center justify-between gap-2 shrink-0">
        <span class="text-xs font-medium text-zinc-900 dark:text-zinc-100">
          导入文档版本
        </span>

        <label class="btn cursor-pointer">
          <Loader2 v-if="isUploading" class="w-3.5 h-3.5 animate-spin" />
          <Upload v-else class="w-3.5 h-3.5" />
          <span>导入文件</span>
          <input
            type="file"
            accept=".json,.yaml,.yml"
            class="hidden"
            :disabled="isUploading || isLoading || isMockMode || !projectId"
            @change="handleFileUpload"
          />
        </label>
      </div>

      <!-- 上传错误提示 -->
      <div v-if="uploadError" role="alert" class="mx-3 mt-2 p-2 rounded-lg bg-red-50 dark:bg-red-950/60 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 text-xs">
        {{ uploadError }}
      </div>

      <p v-if="loadError" role="alert" class="px-3 py-2 text-xs text-red-600 dark:text-red-400">{{ loadError }} <button class="underline" :disabled="isLoading" @click="loadData()">重试加载</button></p>
      <p v-if="isMockMode" class="px-3 py-2 meta">预览仅供查看，请在后端直连模式导入文件。</p>
      <p v-if="isLoading" role="status" class="px-3 py-2 meta">正在读取接口资料…</p>
      <!-- 版本历史列表 -->
      <div class="flex-1 overflow-y-auto p-2 space-y-1">
        <div v-if="imports.length === 0" class="py-12 text-center text-xs text-zinc-400">
          暂无导入记录，请点击上方导入
        </div>
        <div
          v-for="imp in imports"
          :key="imp.id"
          class="w-full text-left px-2.5 py-2 rounded-lg transition-colors focus-ring"
          :class="String(imp.id) === String(selectedImportId)
            ? 'bg-zinc-200/70 dark:bg-zinc-800'
            : 'hover:bg-zinc-100 dark:hover:bg-zinc-900'"

        >
          <button type="button" class="w-full text-left" @click="selectImport(imp.id)">
          <div class="flex items-center justify-between gap-2">
            <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100 truncate">
              Rev #{{ imp.revisionNumber }} · {{ imp.fileName }}
            </span>
            <span class="status shrink-0" :class="imp.status === 'SUCCEEDED' ? 'status-ok' : 'status-err'">
              {{ imp.status === 'SUCCEEDED' ? '已就绪' : '失败' }}
            </span>
          </div>

          <div class="mt-1 flex items-center justify-between meta font-mono">
            <span>{{ imp.endpointCount || 0 }} 个端点</span>
            <span>{{ imp.createdAt?.split('T')[1] || imp.createdAt }}</span>
          </div>

          </button>

          <!-- 失败时重试 -->
          <div v-if="imp.status === 'FAILED'" class="mt-1.5 pt-1.5 border-t border-zinc-100 dark:border-zinc-800 flex items-center justify-between text-xs">
            <span class="text-red-600 dark:text-red-400 truncate max-w-[150px]">{{ imp.errorMessage }}</span>
            <button
              type="button"
              class="meta hover:text-zinc-900 dark:hover:text-zinc-100"
              :disabled="isMockMode || isLoading || !!retryingId" @click="handleRetry(imp.id)"
            >
              重试
            </button>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧：接口清单与依赖关系流 -->
    <main class="flex-1 flex flex-col h-full overflow-hidden min-w-0">

      <!-- 顶部过滤栏 -->
      <div class="h-12 px-6 border-b border-zinc-200 dark:border-zinc-800 flex items-center justify-between gap-4 shrink-0 bg-white dark:bg-zinc-950">
        <!-- 搜索输入框 -->
        <div class="relative flex-1 max-w-md">
          <Search class="w-4 h-4 text-zinc-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索接口路径、操作标识或描述..."
            class="w-full pl-9 pr-3 py-1.5 text-xs rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100 placeholder-zinc-400 focus-ring"
          />
        </div>

        <!-- 标签与拓扑切换 -->
        <div class="flex items-center gap-2">
          <!-- 标签筛选 -->
          <select
            v-if="allTags.length > 0"
            v-model="selectedTag"
            class="px-2.5 py-1.5 text-xs rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-700 dark:text-zinc-300 focus-ring"
          >
            <option value="">全部模块标签</option>
            <option v-for="t in allTags" :key="t" :value="t">{{ t }}</option>
          </select>

          <!-- 子 Tab 切换 -->
          <div class="flex rounded-lg border border-zinc-200 dark:border-zinc-700 p-0.5 text-xs">
            <button
              type="button"
              class="px-3 py-1 rounded-md transition-colors"
              :class="activeSubTab === 'endpoints' ? 'bg-zinc-200/70 dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 font-medium' : 'text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-200'"
              @click="activeSubTab = 'endpoints'"
            >
              接口清单 ({{ filteredEndpoints.length }})
            </button>
            <button
              type="button"
              class="px-3 py-1 rounded-md transition-colors"
              :class="activeSubTab === 'dependencies' ? 'bg-zinc-200/70 dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 font-medium' : 'text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-200'"
              @click="activeSubTab = 'dependencies'"
            >
              调用依赖 ({{ dependencies.length }})
            </button>
          </div>
        </div>
      </div>

      <!-- 主内容区域 -->
      <div class="flex-1 overflow-y-auto p-6">

        <!-- 模式 1：接口列表 -->
        <div v-if="activeSubTab === 'endpoints'" class="max-w-4xl mx-auto space-y-2">
          <div v-if="filteredEndpoints.length === 0" class="py-16 text-center text-xs text-zinc-400">
            未检索到匹配的接口端点
          </div>

          <div
            v-for="ep in filteredEndpoints"
            :key="ep.id"
            class="card overflow-hidden text-xs"
          >
            <!-- 接口标题行 -->
            <button type="button" :aria-expanded="expandedEndpointId === ep.id"
              class="w-full text-left px-4 py-2.5 flex items-center justify-between gap-3 hover:bg-zinc-50 dark:hover:bg-zinc-800/50 transition-colors focus-ring"
              @click="toggleEndpoint(ep.id)"
            >
              <div class="flex items-center gap-3 min-w-0">
                <span class="font-mono font-semibold text-xs w-14 shrink-0 text-zinc-700 dark:text-zinc-300 uppercase">
                  {{ ep.httpMethod }}
                </span>
                <span class="font-mono text-sm text-zinc-900 dark:text-zinc-100 font-medium truncate">
                  {{ ep.path }}
                </span>
                <span v-if="ep.summary" class="meta truncate hidden sm:inline">
                  — {{ ep.summary }}
                </span>
              </div>

              <div class="flex items-center gap-2 shrink-0">
                <span v-if="ep.operationId" class="meta font-mono hidden md:inline">
                  {{ ep.operationId }}
                </span>
                <ChevronDown class="w-4 h-4 text-zinc-400 transition-transform" :class="expandedEndpointId === ep.id ? '' : '-rotate-90'" />
              </div>
            </button>

            <!-- 内嵌展开：参数与请求体契约 -->
            <div v-if="expandedEndpointId === ep.id" class="px-4 py-3 border-t border-zinc-100 dark:border-zinc-800 space-y-3">
              <p v-if="ep.description" class="text-sm text-zinc-600 dark:text-zinc-300 leading-relaxed">
                {{ ep.description }}
              </p>

              <!-- 参数清单 -->
              <div v-if="ep.parameters && ep.parameters.length > 0">
                <div class="meta font-medium mb-1.5">请求参数:</div>
                <div class="rounded-lg border border-zinc-200 dark:border-zinc-800 overflow-hidden">
                  <table class="w-full text-left text-xs">
                    <thead class="bg-zinc-50 dark:bg-zinc-850 border-b border-zinc-200 dark:border-zinc-800 font-mono text-zinc-600 dark:text-zinc-400">
                      <tr>
                        <th class="px-3 py-1.5">参数名</th>
                        <th class="px-3 py-1.5">位置</th>
                        <th class="px-3 py-1.5">必填</th>
                        <th class="px-3 py-1.5">说明</th>
                      </tr>
                    </thead>
                    <tbody class="divide-y divide-zinc-100 dark:divide-zinc-800">
                      <tr v-for="param in ep.parameters" :key="param.name" class="font-mono">
                        <td class="px-3 py-1.5 font-medium text-zinc-900 dark:text-zinc-100">{{ param.name }}</td>
                        <td class="px-3 py-1.5 text-zinc-500">{{ param.location }}</td>
                        <td class="px-3 py-1.5">
                          <span v-if="param.required" class="text-zinc-900 dark:text-zinc-100 font-bold">是</span>
                          <span v-else class="text-zinc-400">否</span>
                        </td>
                        <td class="px-3 py-1.5 text-zinc-600 dark:text-zinc-300 font-sans">{{ param.description || '—' }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <!-- 请求体 Schema -->
              <div v-if="ep.requestBody">
                <div class="meta font-medium mb-1">请求体 (Request Body Schema):</div>
                <pre class="p-2.5 rounded-lg bg-zinc-50 dark:bg-zinc-950 text-zinc-800 dark:text-zinc-200 font-mono text-xs overflow-x-auto border border-zinc-200 dark:border-zinc-800">{{ JSON.stringify(ep.requestBody, null, 2) }}</pre>
              </div>

              <!-- 响应状态 -->
              <div v-if="ep.responses">
                <div class="meta font-medium mb-1">响应定义:</div>
                <pre class="p-2.5 rounded-lg bg-zinc-50 dark:bg-zinc-950 text-zinc-800 dark:text-zinc-200 font-mono text-xs overflow-x-auto border border-zinc-200 dark:border-zinc-800">{{ JSON.stringify(ep.responses, null, 2) }}</pre>
              </div>
            </div>

          </div>
        </div>

        <!-- 模式 2：依赖拓扑流 -->
        <div v-else class="max-w-4xl mx-auto space-y-2">
          <div v-if="dependencies.length === 0" class="py-16 text-center text-xs text-zinc-400">
            暂无当前生效版本的端点依赖候选
          </div>

          <p class="meta">以下候选基于当前生效版本，与所选历史版本无关。</p>
          <div
            v-for="(edge, idx) in dependencies"
            :key="idx"
            class="card p-4 text-xs space-y-2"
          >
            <div class="flex items-center gap-2 font-mono text-sm">
              <span class="text-zinc-900 dark:text-zinc-100 font-medium">
                {{ edge.producerOperationId }}
              </span>
              <ArrowRight class="w-3.5 h-3.5 text-zinc-400 shrink-0" />
              <span class="text-zinc-900 dark:text-zinc-100 font-medium">
                {{ edge.consumerOperationId }}
              </span>
              <span class="ml-auto meta font-mono">
                置信度 {{ Math.round(edge.confidence * 100) }}%
              </span>
            </div>

            <div class="flex items-center gap-2 meta font-mono">
              <span class="text-zinc-500">传递字段:</span>
              <span class="text-zinc-800 dark:text-zinc-200">{{ edge.sharedField }}</span>
            </div>

            <p class="text-sm text-zinc-600 dark:text-zinc-400 leading-relaxed">
              {{ edge.reason }}
            </p>
          </div>
        </div>

      </div>

    </main>

  </div>
</template>
