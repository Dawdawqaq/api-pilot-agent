/**
 * ApiPilot 统一 API 客户端
 * 严格对齐 docs/api.md 中的现有接口：
 * 1. 响应结构 { code, message, data, requestId, timestamp }，code === 'SUCCESS' 判定为成功
 * 2. ID / Long 统一以不透明字符串保存比较，禁止 Number(id)
 * 3. 错误码与 requestId 完整保留并抛出
 */

export class ApiError extends Error {
  constructor(code, message, requestId, httpStatus, raw) {
    super(message || '请求失败')
    this.name = 'ApiError'
    this.code = code
    this.requestId = requestId
    this.httpStatus = httpStatus
    this.raw = raw
  }
}

// 页面统一保留业务错误码和请求追踪标识，便于定位后端失败。
export function formatApiError(error) {
  const message = error?.message || '请求失败，请重试'
  const code = error?.code ? `（${error.code}）` : ''
  const trace = error?.requestId ? `（请求标识：${error.requestId}）` : ''
  return `${message}${code}${trace}`
}

const BASE_URL = '' // 同源或由 Vite dev proxy 转发

async function request(url, options = {}) {
  const headers = {
    Accept: 'application/json',
    ...(options.headers || {})
  }

  // 非 FormData 默认设置 Content-Type 为 application/json
  if (!(options.body instanceof FormData) && !headers['Content-Type'] && options.method && options.method !== 'GET') {
    headers['Content-Type'] = 'application/json'
  }

  let response
  try {
    response = await fetch(`${BASE_URL}${url}`, {
      ...options,
      headers
    })
  } catch (netErr) {
    throw new ApiError('NETWORK_ERROR', '网络连接不可用或后端服务未启动', null, 0, netErr)
  }

  // 针对 XML 响应（如 JUnit 导出）直接返回文本
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/xml') || contentType.includes('text/xml')) {
    if (!response.ok) {
      throw new ApiError('XML_EXPORT_FAILED', `导出失败: HTTP ${response.status}`, null, response.status, null)
    }
    return await response.text()
  }

  // 普通 JSON 响应
  let resJson = null
  try {
    resJson = await response.json()
  } catch (parseErr) {
    if (!response.ok) {
      throw new ApiError('HTTP_ERROR', `服务返回错误 HTTP ${response.status}`, null, response.status, null)
    }
    throw new ApiError('INVALID_RESPONSE', '服务响应不是有效 JSON，请检查代理与后端地址', null, response.status, null)
  }

  // 判定后端 ApiResponse 契约
  if (resJson && typeof resJson === 'object' && 'code' in resJson) {
    if (!response.ok || resJson.code !== 'SUCCESS') {
      throw new ApiError(
        resJson.code || 'UNKNOWN_BIZ_ERROR',
        resJson.message || '业务操作未成功',
        resJson.requestId,
        response.status,
        resJson
      )
    }
    return resJson.data
  }

  // 非 ApiResponse 包装的直接响应 (如 Actuator)
  if (!response.ok) {
    throw new ApiError('HTTP_ERROR', `请求异常 HTTP ${response.status}`, null, response.status, resJson)
  }
  return resJson
}

export const api = {
  // 系统概览
  getSystemOverview() {
    return request('/api/v1/system/overview')
  },

  // LLM 配置
  getLlmConfig() {
    return request('/api/v1/system/llm')
  },
  saveLlmConfig(data) {
    return request('/api/v1/system/llm', {
      method: 'PUT',
      body: JSON.stringify(data)
    })
  },
  testLlmConnection(data) {
    return request('/api/v1/system/llm/test', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  },

  // 项目管理
  listProjects() {
    return request('/api/v1/projects')
  },
  createProject(data) {
    return request('/api/v1/projects', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  },
  deleteProject(projectId) {
    return request(`/api/v1/projects/${projectId}`, {
      method: 'DELETE'
    })
  },
  updateProject(projectId, data) {
    return request(`/api/v1/projects/${projectId}`, { method: 'PUT', body: JSON.stringify(data) })
  },

  // 环境管理
  listEnvironments(projectId) {
    return request(`/api/v1/projects/${projectId}/environments`)
  },
  createEnvironment(projectId, data) {
    return request(`/api/v1/projects/${projectId}/environments`, {
      method: 'POST',
      body: JSON.stringify(data)
    })
  },
  updateEnvironment(projectId, environmentId, data) {
    return request(`/api/v1/projects/${projectId}/environments/${environmentId}`, { method: 'PUT', body: JSON.stringify(data) })
  },
  deleteEnvironment(projectId, environmentId) {
    return request(`/api/v1/projects/${projectId}/environments/${environmentId}`, { method: 'DELETE' })
  },

  // 项目模型调用策略
  getModelPolicy(projectId) {
    return request(`/api/v1/projects/${projectId}/model-policy`)
  },
  saveModelPolicy(projectId, data) {
    return request(`/api/v1/projects/${projectId}/model-policy`, {
      method: 'PUT',
      body: JSON.stringify(data)
    })
  },

  // Agent 任务管理
  createAgentTask(projectId, data) {
    return request(`/api/v1/projects/${projectId}/agent-tasks`, {
      method: 'POST',
      body: JSON.stringify(data)
    })
  },
  getTaskHistory(projectId, {query = '', beforeId = '', limit = 20} = {}) {
    const params = new URLSearchParams({query,limit:String(limit)})
    if (beforeId) params.set('beforeId',beforeId)
    return request(`/api/v1/projects/${projectId}/agent-tasks/history?${params}`)
  },

  getReadiness(projectId, environmentId) {
    const params = new URLSearchParams()
    if (environmentId) params.set('environmentId',environmentId)
    return request(`/api/v1/projects/${projectId}/readiness?${params}`)
  },
  getEmbeddingConfig() { return request('/api/v1/system/embedding') },
  testEmbedding(data) { return request('/api/v1/system/embedding/test',{method:'POST',body:JSON.stringify(data)}) },
  rebuildEmbedding(data) { return request('/api/v1/system/embedding/rebuild',{method:'POST',body:JSON.stringify(data)}) },
  cleanUnusedIndexes() { return request('/api/v1/system/embedding/cleanup',{method:'POST'}) },
  getProjectData(projectId) { return request(`/api/v1/projects/${projectId}/data`) },
  previewCleanup(projectId, retentionDays) {
    return request(`/api/v1/projects/${projectId}/data/cleanup-preview?retentionDays=${encodeURIComponent(retentionDays)}`,{method:'POST'})
  },
  cleanProjectData(projectId, data) { return request(`/api/v1/projects/${projectId}/data/cleanup`,{method:'POST',body:JSON.stringify(data)}) },
  listRecycledProjects() { return request('/api/v1/projects/recycled') },
  restoreProject(projectId) { return request(`/api/v1/projects/${projectId}/restoration`,{method:'POST'}) },
  getAgentTask(projectId, taskId) {
    return request(`/api/v1/projects/${projectId}/agent-tasks/${taskId}`)
  },
  confirmPlan(projectId, taskId, decision) {
    // 确认决策必须携带当前计划哈希，避免批准过期版本。
    return request(`/api/v1/projects/${projectId}/agent-tasks/${taskId}/confirmation`, {
      method: 'POST',
      body: JSON.stringify(decision)
    })
  },
  modifyPlan(projectId, taskId, instruction) {
    // 修改指令由后端重新规划，不在前端伪造新计划。
    return request(`/api/v1/projects/${projectId}/agent-tasks/${taskId}/modify`, {
      method: 'POST',
      body: JSON.stringify({ instruction })
    })
  },
  cancelTask(projectId, taskId) {
    return request(`/api/v1/projects/${projectId}/agent-tasks/${taskId}/cancellation`, {
      method: 'POST'
    })
  },

  // OpenAPI 接口文档与依赖
  uploadOpenApi(projectId, file) {
    const formData = new FormData()
    formData.append('file', file)
    return request(`/api/v1/projects/${projectId}/openapi/imports`, {
      method: 'POST',
      body: formData
    })
  },
  listOpenApiImports(projectId) {
    return request(`/api/v1/projects/${projectId}/openapi/imports`)
  },
  retryOpenApiImport(projectId, importId) {
    return request(`/api/v1/projects/${projectId}/openapi/imports/${importId}/retry`, {
      method: 'POST'
    })
  },
  listEndpoints(projectId, importId = null) {
    const query = importId ? `?importId=${importId}` : ''
    return request(`/api/v1/projects/${projectId}/openapi/endpoints${query}`)
  },
  getDependencies(projectId) {
    return request(`/api/v1/projects/${projectId}/openapi/dependencies`)
  },

  // 测试报告与导出
  getReportHistory(projectId, {query = '', beforeId = '', limit = 20} = {}) {
    const params = new URLSearchParams({query,limit:String(limit)})
    if (beforeId) params.set('beforeId',beforeId)
    return request(`/api/v1/projects/${projectId}/reports/history?${params}`)
  },
  getReport(projectId, reportId) {
    return request(`/api/v1/projects/${projectId}/reports/${reportId}`)
  },
  exportJUnitXml(projectId, reportId) {
    return request(`/api/v1/projects/${projectId}/reports/${reportId}/junit.xml`, {
      headers: { Accept: 'application/xml, application/json' }
    })
  },

  // 契约测试与失败回放
  getNegativeCases(projectId, endpointId) {
    return request(`/api/v1/projects/${projectId}/contract-tests/negative-cases?endpointId=${endpointId}`)
  },
  getReplays(projectId, {executionId = '',query = '',beforeId = '',limit = 20} = {}) {
    const params = new URLSearchParams({query,limit:String(limit)})
    if (executionId) params.set('executionId',executionId)
    if (beforeId) params.set('beforeId',beforeId)
    return request(`/api/v1/projects/${projectId}/contract-tests/replays?${params}`)
  },
  getReplay(projectId, replayId) {
    return request(`/api/v1/projects/${projectId}/contract-tests/replays/${encodeURIComponent(replayId)}`)
  },
  replayFailure(projectId, replayId, environmentId) {
    return request(`/api/v1/projects/${projectId}/contract-tests/replays/${encodeURIComponent(replayId)}`, {
      method: 'POST',
      body: JSON.stringify({ environmentId })
    })
  },

  // 业务知识库 (可选)
  uploadDocument(projectId, file) {
    const formData = new FormData()
    formData.append('file', file)
    return request(`/api/v1/projects/${projectId}/documents`, {
      method: 'POST',
      body: formData
    })
  },
  listDocuments(projectId) {
    return request(`/api/v1/projects/${projectId}/documents`)
  },
  retryDocument(projectId, documentId) {
    return request(`/api/v1/projects/${projectId}/documents/${documentId}/retry`, {
      method: 'POST'
    })
  },
  deleteDocument(projectId, documentId) {
    return request(`/api/v1/projects/${projectId}/documents/${documentId}`, {
      method: 'DELETE'
    })
  },
  searchKnowledge(projectId, query, topK = 5) {
    return request(`/api/v1/projects/${projectId}/retrieval/search`, {
      method: 'POST',
      body: JSON.stringify({ query, topK })
    })
  },

  // 创建真实 SSE 订阅连接
  subscribeTaskStream(projectId, taskId, after = 0, onEvent, onError) {
    let cursor = Number(after) || 0
    let source = null, retryTimer = null, stopped = false, retryDelay = 1000
    const connect = () => {
      if (stopped) return
      const connection = new EventSource(`/api/v1/projects/${projectId}/agent-tasks/${taskId}/stream?after=${cursor}`)
      source = connection
      connection.onopen = () => { retryDelay = 1000 }
      connection.addEventListener('agent-event', event => {
        if (stopped || source !== connection) return
        let parsed
        try { parsed = JSON.parse(event.data) }
        catch (error) { onError?.(error); return }
        const sequence = Number(parsed.sequenceNo)
        if (Number.isFinite(sequence)) {
          if (sequence <= cursor) return
          cursor = sequence
        }
        onEvent?.(parsed, event.lastEventId)
      })
      connection.onerror = error => {
        if (stopped || source !== connection) return
        connection.close()
        source = null
        onError?.(error)
        // 显式携带已收到的事件游标重连，避免重放整个历史或依赖浏览器差异。
        if (!stopped) {
          retryTimer = setTimeout(connect, retryDelay)
          retryDelay = Math.min(retryDelay * 2, 10000)
        }
      }
    }
    const reconnectOnline = () => {
      if (stopped) return
      clearTimeout(retryTimer)
      source?.close()
      connect()
    }
    // 网络恢复后立即校准连接；销毁订阅时同时释放浏览器事件监听。
    if (typeof window !== 'undefined') window.addEventListener('online', reconnectOnline)
    connect()
    return () => {
      stopped = true
      clearTimeout(retryTimer)
      source?.close()
      if (typeof window !== 'undefined') window.removeEventListener('online', reconnectOnline)
    }
  }
}
