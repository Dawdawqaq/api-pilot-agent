/**
 * OpenAPI Specification & Endpoints Explorer View
 */
import { Api } from '../api.js';
import { Utils } from '../utils.js';

export const OpenApiView = {
  async render(container, state, navigate) {
    container.innerHTML = `
      <div class="flex flex-col gap-6" style="max-width: 1200px; margin: 0 auto;">
        <!-- Top Action & Upload Card -->
        <div class="card">
          <div class="card-header">
            <div class="flex items-center gap-2">
              ${Utils.getIcon('upload-cloud', 18)}
              <h3 style="font-size: 15px; font-weight:600;">导入 OpenAPI / Swagger 接口规范</h3>
            </div>
            <span class="badge" style="background:rgba(255,255,255,0.06); color:var(--text-secondary);">支持 OpenAPI 3.x / Swagger 2.x (JSON & YAML)</span>
          </div>
          <div class="card-body">
            <div id="drop-zone" style="border: 2px dashed var(--border-default); border-radius: var(--radius-lg); padding: var(--space-6); text-align: center; cursor: pointer; transition: all var(--transition-fast);">
              <input type="file" id="openapi-file-input" accept=".json,.yaml,.yml" style="display: none;">
              <div class="flex flex-col items-center gap-2">
                <div style="color: var(--accent-light);">${Utils.getIcon('upload-cloud', 32)}</div>
                <div style="font-weight: 600; font-size: 14px;">点击或拖拽 OpenAPI 规范文件至此处</div>
                <p style="font-size: 12px; color: var(--text-muted);">文件将自动解析 Operation、请求路径、参数 Schema 与响应依赖</p>
                <button class="btn btn-secondary btn-sm" id="btn-browse-file" style="margin-top: 8px;">
                  选择本地文件
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Tabs: Endpoints Catalog vs Dependencies -->
        <div class="card">
          <div class="card-header flex justify-between items-center flex-wrap gap-3">
            <div class="flex items-center gap-2">
              <button class="btn btn-sm btn-secondary active-tab" id="tab-btn-endpoints">
                ${Utils.getIcon('layers', 14)} 接口目录 (<span id="endpoints-count">0</span>)
              </button>
              <button class="btn btn-sm btn-ghost" id="tab-btn-deps">
                ${Utils.getIcon('activity', 14)} 依赖拓扑候选 (<span id="deps-count">0</span>)
              </button>
              <button class="btn btn-sm btn-ghost" id="tab-btn-imports">
                ${Utils.getIcon('file-text', 14)} 导入历史
              </button>
            </div>

            <!-- Filters -->
            <div class="flex items-center gap-2 flex-wrap" id="endpoints-filter-bar">
              <select class="form-select" id="filter-method" style="width: 100px; padding: 4px 8px; font-size: 12px;">
                <option value="ALL">全部方法</option>
                <option value="GET">GET</option>
                <option value="POST">POST</option>
                <option value="PUT">PUT</option>
                <option value="PATCH">PATCH</option>
                <option value="DELETE">DELETE</option>
              </select>
              <input class="form-input" id="search-endpoint" placeholder="搜索路径或描述..." style="width: 200px; padding: 4px 8px; font-size: 12px;">
              <button class="btn btn-ghost btn-sm" id="btn-refresh-endpoints" title="刷新">
                ${Utils.getIcon('refresh-cw', 13)}
              </button>
            </div>
          </div>

          <div class="card-body" style="padding: 0;">
            <!-- Endpoints Table -->
            <div id="tab-content-endpoints">
              <div style="overflow-x: auto;">
                <table style="width: 100%; border-collapse: collapse; text-align: left; font-size: 13px;">
                  <thead>
                    <tr style="border-bottom: 1px solid var(--border-subtle); color: var(--text-muted); font-size: 12px;">
                      <th style="padding: 12px 16px; width: 90px;">方法</th>
                      <th style="padding: 12px 16px;">路径 (Path)</th>
                      <th style="padding: 12px 16px;">接口描述 / 摘要</th>
                      <th style="padding: 12px 16px;">标签 (Tags)</th>
                      <th style="padding: 12px 16px; text-align: right;">操作</th>
                    </tr>
                  </thead>
                  <tbody id="endpoints-tbody">
                    <tr><td colspan="5" style="padding: 32px; text-align: center; color: var(--text-muted);">正在加载接口目录...</td></tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Dependency Candidates Table -->
            <div id="tab-content-deps" style="display: none; padding: var(--space-4);">
              <div style="overflow-x: auto;">
                <table style="width: 100%; border-collapse: collapse; text-align: left; font-size: 13px;">
                  <thead>
                    <tr style="border-bottom: 1px solid var(--border-subtle); color: var(--text-muted); font-size: 12px;">
                      <th style="padding: 12px 16px;">生产者接口 (Source)</th>
                      <th style="padding: 12px 16px;">消费者接口 (Target)</th>
                      <th style="padding: 12px 16px;">参数传递映射 (Field Mapping)</th>
                      <th style="padding: 12px 16px;">置信度</th>
                    </tr>
                  </thead>
                  <tbody id="deps-tbody">
                    <tr><td colspan="4" style="padding: 32px; text-align: center; color: var(--text-muted);">正在推断接口依赖候选...</td></tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Imports History Table -->
            <div id="tab-content-imports" style="display: none; padding: var(--space-4);">
              <table style="width: 100%; border-collapse: collapse; text-align: left; font-size: 13px;">
                <thead>
                  <tr style="border-bottom: 1px solid var(--border-subtle); color: var(--text-muted); font-size: 12px;">
                    <th style="padding: 12px 16px;">导入 ID</th>
                    <th style="padding: 12px 16px;">文件名</th>
                    <th style="padding: 12px 16px;">规范版本</th>
                    <th style="padding: 12px 16px;">状态</th>
                    <th style="padding: 12px 16px;">解析接口数</th>
                    <th style="padding: 12px 16px;">导入时间</th>
                    <th style="padding: 12px 16px; text-align: right;">操作</th>
                  </tr>
                </thead>
                <tbody id="imports-tbody">
                  <tr><td colspan="7" style="padding: 32px; text-align: center; color: var(--text-muted);">正在加载导入记录...</td></tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <!-- Endpoint Detail Modal -->
      <div class="modal-backdrop" id="modal-endpoint-detail">
        <div class="modal-content" style="max-width: 760px;">
          <div class="modal-header">
            <div class="flex items-center gap-2">
              <span id="detail-method-badge" class="method-badge">GET</span>
              <h3 id="detail-path" style="font-family: var(--font-mono); font-size: 14px;">-</h3>
            </div>
            <button class="btn btn-ghost btn-icon btn-sm" id="btn-close-endpoint-modal">${Utils.getIcon('x', 16)}</button>
          </div>
          <div class="modal-body flex flex-col gap-4">
            <div>
              <span style="font-size: 12px; color: var(--text-muted);">概要说明：</span>
              <div id="detail-summary" style="font-size: 14px; font-weight: 500; margin-top: 2px;">-</div>
            </div>

            <div>
              <span style="font-size: 12px; font-weight: 600; color: var(--text-secondary);">请求参数 (Parameters)</span>
              <pre class="json-viewer" id="detail-params" style="margin-top: 4px;">无参数</pre>
            </div>

            <div>
              <span style="font-size: 12px; font-weight: 600; color: var(--text-secondary);">请求体 (RequestBody Schema)</span>
              <pre class="json-viewer" id="detail-body" style="margin-top: 4px;">无请求体</pre>
            </div>

            <div>
              <span style="font-size: 12px; font-weight: 600; color: var(--text-secondary);">响应定义 (Responses)</span>
              <pre class="json-viewer" id="detail-responses" style="margin-top: 4px;">-</pre>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" id="btn-close-endpoint-detail">关闭</button>
            <button class="btn btn-primary" id="btn-test-with-agent">
              ${Utils.getIcon('play', 14)} 在 Agent 中以此接口发起测试
            </button>
          </div>
        </div>
      </div>
    `;

    // Drop Zone file upload
    const dropZone = container.querySelector('#drop-zone');
    const fileInput = container.querySelector('#openapi-file-input');
    const browseBtn = container.querySelector('#btn-browse-file');

    browseBtn.onclick = (e) => {
      e.stopPropagation();
      fileInput.click();
    };

    dropZone.onclick = () => fileInput.click();

    dropZone.ondragover = (e) => {
      e.preventDefault();
      dropZone.style.borderColor = 'var(--accent)';
      dropZone.style.background = 'var(--accent-surface)';
    };

    dropZone.ondragleave = () => {
      dropZone.style.borderColor = 'var(--border-default)';
      dropZone.style.background = 'transparent';
    };

    dropZone.ondrop = (e) => {
      e.preventDefault();
      dropZone.style.borderColor = 'var(--border-default)';
      dropZone.style.background = 'transparent';
      if (e.dataTransfer.files && e.dataTransfer.files[0]) {
        this.uploadFile(e.dataTransfer.files[0], container, state);
      }
    };

    fileInput.onchange = () => {
      if (fileInput.files && fileInput.files[0]) {
        this.uploadFile(fileInput.files[0], container, state);
      }
    };

    // Tab Switchers
    const tabEndpoints = container.querySelector('#tab-btn-endpoints');
    const tabDeps = container.querySelector('#tab-btn-deps');
    const tabImports = container.querySelector('#tab-btn-imports');
    const filterBar = container.querySelector('#endpoints-filter-bar');

    const contentEndpoints = container.querySelector('#tab-content-endpoints');
    const contentDeps = container.querySelector('#tab-content-deps');
    const contentImports = container.querySelector('#tab-content-imports');

    const resetTabs = () => {
      [tabEndpoints, tabDeps, tabImports].forEach(b => {
        b.className = 'btn btn-sm btn-ghost';
      });
      [contentEndpoints, contentDeps, contentImports].forEach(c => {
        c.style.display = 'none';
      });
      filterBar.style.display = 'none';
    };

    tabEndpoints.onclick = () => {
      resetTabs();
      tabEndpoints.className = 'btn btn-sm btn-secondary active-tab';
      contentEndpoints.style.display = 'block';
      filterBar.style.display = 'flex';
    };

    tabDeps.onclick = () => {
      resetTabs();
      tabDeps.className = 'btn btn-sm btn-secondary active-tab';
      contentDeps.style.display = 'block';
      this.loadDependencies(container, state);
    };

    tabImports.onclick = () => {
      resetTabs();
      tabImports.className = 'btn btn-sm btn-secondary active-tab';
      contentImports.style.display = 'block';
      this.loadImports(container, state);
    };

    // Filter events
    container.querySelector('#filter-method').onchange = () => this.filterEndpoints(container, state);
    container.querySelector('#search-endpoint').oninput = () => this.filterEndpoints(container, state);
    container.querySelector('#btn-refresh-endpoints').onclick = () => this.loadEndpoints(container, state);

    // Modal close
    const modal = container.querySelector('#modal-endpoint-detail');
    container.querySelector('#btn-close-endpoint-modal').onclick = () => modal.classList.remove('active');
    container.querySelector('#btn-close-endpoint-detail').onclick = () => modal.classList.remove('active');

    await this.loadEndpoints(container, state);
  },

  async uploadFile(file, container, state) {
    if (!state.currentProject) {
      Utils.showToast('请先选择一个项目再导入规范', 'warning');
      return;
    }

    try {
      Utils.showToast(`正在上传并解析 ${file.name}...`, 'info');
      const res = await Api.openapi.upload(state.currentProject.id, file);
      Utils.showToast(`导入成功！共解析出 ${res.endpointCount || 0} 个接口`, 'success');
      await this.loadEndpoints(container, state);
    } catch (err) {
      Utils.showToast(`导入失败: ${err.message}`, 'error');
    }
  },

  async loadEndpoints(container, state) {
    if (!state.currentProject) {
      const tbody = container.querySelector('#endpoints-tbody');
      if (tbody) tbody.innerHTML = `<tr><td colspan="5" style="padding:32px; text-align:center; color:var(--text-muted)">请先在顶部选择项目</td></tr>`;
      return;
    }

    try {
      const endpoints = await Api.openapi.listEndpoints(state.currentProject.id);
      state.endpoints = endpoints;

      const countEl = container.querySelector('#endpoints-count');
      if (countEl) countEl.textContent = endpoints.length;

      this.filterEndpoints(container, state);
    } catch (err) {
      console.error('Failed to load endpoints:', err);
    }
  },

  filterEndpoints(container, state) {
    const endpoints = state.endpoints || [];
    const methodFilter = container.querySelector('#filter-method').value;
    const query = container.querySelector('#search-endpoint').value.trim().toLowerCase();

    const filtered = endpoints.filter(ep => {
      const method = (ep.httpMethod || '').toUpperCase();
      const matchMethod = methodFilter === 'ALL' || method === methodFilter;
      const matchQuery = !query ||
        ep.path.toLowerCase().includes(query) ||
        (ep.summary && ep.summary.toLowerCase().includes(query)) ||
        (ep.description && ep.description.toLowerCase().includes(query));
      return matchMethod && matchQuery;
    });

    const tbody = container.querySelector('#endpoints-tbody');
    if (!tbody) return;

    if (filtered.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" style="padding:32px; text-align:center; color:var(--text-muted)">无匹配的接口或暂未导入 OpenAPI 规范</td></tr>`;
      return;
    }

    tbody.innerHTML = filtered.map(ep => {
      const method = (ep.httpMethod || 'GET').toUpperCase();
      const tagsHtml = (Array.isArray(ep.tags) ? ep.tags : []).map(t => `<span class="badge" style="background:rgba(255,255,255,0.06); font-size:11px;">${Utils.escapeHtml(t)}</span>`).join(' ');
      return `
        <tr style="border-bottom: 1px solid var(--border-subtle); transition: background var(--transition-fast);" onmouseover="this.style.background='var(--bg-surface-hover)'" onmouseout="this.style.background='transparent'">
          <td style="padding: 10px 16px;">
            <span class="method-badge method-${method}">${method}</span>
          </td>
          <td style="padding: 10px 16px; font-family: var(--font-mono); font-size: 13px; color: var(--accent-light);">
            ${Utils.escapeHtml(ep.path)}
          </td>
          <td style="padding: 10px 16px; font-size: 13px;">
            ${Utils.escapeHtml(ep.summary || ep.description || '-')}
          </td>
          <td style="padding: 10px 16px;">
            <div class="flex gap-1 flex-wrap">${tagsHtml}</div>
          </td>
          <td style="padding: 10px 16px; text-align: right;">
            <button class="btn btn-ghost btn-sm action-inspect-endpoint" data-ep-id="${ep.id}">
              详情
            </button>
          </td>
        </tr>
      `;
    }).join('');

    tbody.querySelectorAll('.action-inspect-endpoint').forEach(btn => {
      btn.onclick = () => {
        const epid = btn.getAttribute('data-ep-id');
        const ep = endpoints.find(x => x.id === epid);
        if (ep) this.openEndpointModal(ep, container, state);
      };
    });
  },

  openEndpointModal(ep, container, state) {
    const modal = container.querySelector('#modal-endpoint-detail');
    const badge = container.querySelector('#detail-method-badge');
    const method = (ep.httpMethod || 'GET').toUpperCase();
    badge.className = `method-badge method-${method}`;
    badge.textContent = method;

    container.querySelector('#detail-path').textContent = ep.path;
    container.querySelector('#detail-summary').textContent = ep.summary || ep.description || '无详细描述';
    container.querySelector('#detail-params').innerHTML = Utils.highlightJson(ep.parameters);
    container.querySelector('#detail-body').innerHTML = Utils.highlightJson(ep.requestBody);
    container.querySelector('#detail-responses').innerHTML = Utils.highlightJson(ep.responses);

    container.querySelector('#btn-test-with-agent').onclick = () => {
      modal.classList.remove('active');
      state.agentPrefillGoal = `调用 ${method} ${ep.path} 验证响应状态码为 200，并检查响应结构符合契约。`;
      const workbenchTab = document.querySelector('[data-view="workbench"]');
      if (workbenchTab) workbenchTab.click();
    };

    modal.classList.add('active');
  },

  async loadDependencies(container, state) {
    if (!state.currentProject) return;
    const tbody = container.querySelector('#deps-tbody');
    try {
      const deps = await Api.openapi.getDependencies(state.currentProject.id);
      const countEl = container.querySelector('#deps-count');
      if (countEl) countEl.textContent = deps.length;

      if (deps.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" style="padding:32px; text-align:center; color:var(--text-muted)">未检测到跨接口参数依赖候选</td></tr>`;
        return;
      }

      tbody.innerHTML = deps.map(dep => {
        const producer = dep.producerOperationId || `#${dep.producerEndpointId}`;
        const consumer = dep.consumerOperationId || `#${dep.consumerEndpointId}`;

        return `
          <tr style="border-bottom: 1px solid var(--border-subtle);">
            <td style="padding: 12px 16px;">
              <span class="code-font">${Utils.escapeHtml(producer)}</span>
            </td>
            <td style="padding: 12px 16px;">
              <span class="code-font">${Utils.escapeHtml(consumer)}</span>
            </td>
            <td style="padding: 12px 16px; font-size: 12px;">
              <code>${Utils.escapeHtml(dep.sharedField || '-')}</code>
              ${dep.reason ? `<div style="color:var(--text-muted); margin-top:4px;">${Utils.escapeHtml(dep.reason)}</div>` : ''}
            </td>
            <td style="padding: 12px 16px; font-family: var(--font-mono); font-size: 12px; color: var(--accent-light);">
              ${((dep.confidence || 0) * 100).toFixed(0)}%
            </td>
          </tr>
        `;
      }).join('');
    } catch (err) {
      console.error('Failed to load dependencies:', err);
    }
  },

  async loadImports(container, state) {
    if (!state.currentProject) return;
    const tbody = container.querySelector('#imports-tbody');
    try {
      const imports = await Api.openapi.listImports(state.currentProject.id);
      if (imports.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" style="padding:32px; text-align:center; color:var(--text-muted)">暂无导入记录</td></tr>`;
        return;
      }

      tbody.innerHTML = imports.map(item => `
        <tr style="border-bottom: 1px solid var(--border-subtle);">
          <td style="padding: 12px 16px; font-family: var(--font-mono); color: var(--text-muted);">${item.id}</td>
          <td style="padding: 12px 16px; font-weight: 600;">${Utils.escapeHtml(item.fileName)}</td>
          <td style="padding: 12px 16px; font-size: 12px;">${Utils.escapeHtml(item.specificationVersion || '-')}</td>
          <td style="padding: 12px 16px;">
            <span class="badge" style="background:${item.status === 'SUCCEEDED' ? 'var(--color-success-bg)' : item.status === 'PROCESSING' ? 'rgba(245,158,11,0.15)' : 'var(--color-danger-bg)'}; color:${item.status === 'SUCCEEDED' ? 'var(--color-success)' : item.status === 'PROCESSING' ? 'var(--color-warning)' : 'var(--color-danger)'};">
              ${item.status}
            </span>
          </td>
          <td style="padding: 12px 16px; font-family: var(--font-mono);">${item.endpointCount || 0}</td>
          <td style="padding: 12px 16px; font-size: 12px; color: var(--text-muted);">${Utils.formatDateTime(item.createdAt)}</td>
          <td style="padding: 12px 16px; text-align: right;">
            ${item.status === 'FAILED' ? `
              <button class="btn btn-ghost btn-sm action-retry-import" data-imp-id="${item.id}">
                ${Utils.getIcon('refresh-cw', 13)} 重试
              </button>
            ` : '-'}
          </td>
        </tr>
      `).join('');

      tbody.querySelectorAll('.action-retry-import').forEach(btn => {
        btn.onclick = async () => {
          const impId = btn.getAttribute('data-imp-id');
          try {
            await Api.openapi.retryImport(state.currentProject.id, impId);
            Utils.showToast('重试解析已提交', 'info');
            await this.loadImports(container, state);
            await this.loadEndpoints(container, state);
          } catch (err) {
            Utils.showToast(err.message, 'error');
          }
        };
      });
    } catch (err) {
      console.error('Failed to load imports:', err);
    }
  }
};
