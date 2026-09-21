/**
 * DocHelper REST API Client
 * Automatically unwraps ApiResponse<T> and standardizes business exceptions
 */

export class ApiError extends Error {
  constructor(message, code = 'ERROR', status = 500, data = null) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.data = data;
  }
}

async function request(url, options = {}) {
  const defaultHeaders = {};
  if (!(options.body instanceof FormData)) {
    defaultHeaders['Content-Type'] = 'application/json';
  }

  const config = {
    ...options,
    headers: {
      ...defaultHeaders,
      ...(options.headers || {})
    }
  };

  try {
    const res = await fetch(url, config);

    // Handle XML responses (like JUnit export)
    const contentType = res.headers.get('content-type') || '';
    if (contentType.includes('application/xml') || contentType.includes('text/xml')) {
      const xmlText = await res.text();
      if (!res.ok) throw new ApiError(`导出失败 HTTP ${res.status}`, 'HTTP_ERROR', res.status);
      return xmlText;
    }

    let json;
    try {
      json = await res.json();
    } catch {
      if (!res.ok) throw new ApiError(`HTTP ${res.status}: ${res.statusText}`, 'HTTP_ERROR', res.status);
      return null;
    }

    if (!res.ok) {
      const errMsg = json?.message || `请求失败 (HTTP ${res.status})`;
      const errCode = json?.code || 'HTTP_ERROR';
      throw new ApiError(errMsg, errCode, res.status, json?.data);
    }

    // ApiResponse structure { code, message, data, requestId, timestamp }
    if (json && typeof json === 'object' && 'code' in json) {
      if (json.code === 'SUCCESS') {
        return json.data;
      } else {
        throw new ApiError(json.message || '业务操作失败', json.code, res.status, json.data);
      }
    }

    return json;
  } catch (err) {
    if (err instanceof ApiError) throw err;
    throw new ApiError(err.message || '网络连接异常，请检查后端服务', 'NETWORK_ERROR', 0);
  }
}

export const Api = {
  // System Info
  system: {
    getOverview: () => request('/api/v1/system/overview'),
    getHealth: () => request('/actuator/health')
  },

  // Projects
  projects: {
    list: () => request('/api/v1/projects'),
    get: (id) => request(`/api/v1/projects/${id}`),
    create: (data) => request('/api/v1/projects', { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data) => request(`/api/v1/projects/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id) => request(`/api/v1/projects/${id}`, { method: 'DELETE' })
  },

  // Environments
  environments: {
    list: (projectId) => request(`/api/v1/projects/${projectId}/environments`),
    create: (projectId, data) => request(`/api/v1/projects/${projectId}/environments`, { method: 'POST', body: JSON.stringify(data) }),
    update: (projectId, envId, data) => request(`/api/v1/projects/${projectId}/environments/${envId}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (projectId, envId) => request(`/api/v1/projects/${projectId}/environments/${envId}`, { method: 'DELETE' })
  },

  // OpenAPI Specs & Endpoints
  openapi: {
    upload: (projectId, file) => {
      const fd = new FormData();
      fd.append('file', file);
      return request(`/api/v1/projects/${projectId}/openapi/imports`, { method: 'POST', body: fd });
    },
    listImports: (projectId) => request(`/api/v1/projects/${projectId}/openapi/imports`),
    getImport: (projectId, importId) => request(`/api/v1/projects/${projectId}/openapi/imports/${importId}`),
    retryImport: (projectId, importId) => request(`/api/v1/projects/${projectId}/openapi/imports/${importId}/retry`, { method: 'POST' }),
    listEndpoints: (projectId, importId = null) => {
      const url = importId
        ? `/api/v1/projects/${projectId}/openapi/endpoints?importId=${importId}`
        : `/api/v1/projects/${projectId}/openapi/endpoints`;
      return request(url);
    },
    getEndpoint: (projectId, endpointId) => request(`/api/v1/projects/${projectId}/openapi/endpoints/${endpointId}`),
    getDependencies: (projectId) => request(`/api/v1/projects/${projectId}/openapi/dependencies`)
  },

  // Agent Tasks
  agent: {
    create: (projectId, data) => request(`/api/v1/projects/${projectId}/agent-tasks`, { method: 'POST', body: JSON.stringify(data) }),
    list: (projectId, limit = 20) => request(`/api/v1/projects/${projectId}/agent-tasks?limit=${limit}`),
    get: (projectId, taskId) => request(`/api/v1/projects/${projectId}/agent-tasks/${taskId}`),
    getEvents: (projectId, taskId, after = 0, limit = 100) => request(`/api/v1/projects/${projectId}/agent-tasks/${taskId}/events?after=${after}&limit=${limit}`),
    confirm: (projectId, taskId, data) => request(`/api/v1/projects/${projectId}/agent-tasks/${taskId}/confirmation`, { method: 'POST', body: JSON.stringify(data) }),
    modify: (projectId, taskId, data) => request(`/api/v1/projects/${projectId}/agent-tasks/${taskId}/modify`, { method: 'POST', body: JSON.stringify(data) }),
    cancel: (projectId, taskId) => request(`/api/v1/projects/${projectId}/agent-tasks/${taskId}/cancellation`, { method: 'POST' })
  },

  // Reports
  reports: {
    list: (projectId, limit = 20) => request(`/api/v1/projects/${projectId}/reports?limit=${limit}`),
    get: (projectId, reportId) => request(`/api/v1/projects/${projectId}/reports/${reportId}`),
    exportJUnitXml: (projectId, reportId) => request(`/api/v1/projects/${projectId}/reports/${reportId}/junit.xml`)
  },

  // Contract Tests
  contract: {
    getNegativeCases: (projectId, endpointId) => request(`/api/v1/projects/${projectId}/contract-tests/negative-cases?endpointId=${endpointId}`),
    getReplay: (projectId, replayId) => request(`/api/v1/projects/${projectId}/contract-tests/replays/${replayId}`),
    replay: (projectId, replayId, data) => request(`/api/v1/projects/${projectId}/contract-tests/replays/${replayId}`, { method: 'POST', body: JSON.stringify(data) })
  },

  // Model Governance Policy
  governance: {
    get: (projectId) => request(`/api/v1/projects/${projectId}/model-policy`),
    update: (projectId, data) => request(`/api/v1/projects/${projectId}/model-policy`, { method: 'PUT', body: JSON.stringify(data) })
  },

  // Knowledge Documents & Retrieval
  knowledge: {
    upload: (projectId, file) => {
      const fd = new FormData();
      fd.append('file', file);
      return request(`/api/v1/projects/${projectId}/documents`, { method: 'POST', body: fd });
    },
    list: (projectId) => request(`/api/v1/projects/${projectId}/documents`),
    get: (projectId, docId) => request(`/api/v1/projects/${projectId}/documents/${docId}`),
    retry: (projectId, docId) => request(`/api/v1/projects/${projectId}/documents/${docId}/retry`, { method: 'POST' }),
    delete: (projectId, docId) => request(`/api/v1/projects/${projectId}/documents/${docId}`, { method: 'DELETE' }),
    search: (projectId, query, topK = 5) => request(`/api/v1/projects/${projectId}/retrieval/search`, { method: 'POST', body: JSON.stringify({ query, topK }) })
  }
};
