/**
 * Knowledge Base & Hybrid Retrieval View
 */
import { Api } from '../api.js';
import { Utils } from '../utils.js';

export const KnowledgeView = {
  async render(container, state, navigate) {
    const isKnowledgeEnabled = state.systemOverview?.knowledgeEnabled;

    container.innerHTML = `
      <div class="flex flex-col gap-6" style="max-width: 1200px; margin: 0 auto;">
        <div class="flex justify-between items-center flex-wrap gap-4">
          <div>
            <h2>业务知识库与混合检索</h2>
            <p style="font-size: 13px;">管理业务规则文档（Markdown / PDF / TXT），并通过 BM25 与向量 RRF 融合评测</p>
          </div>
          <span class="badge" style="background:${isKnowledgeEnabled ? 'var(--color-success-bg)' : 'rgba(255,255,255,0.06)'}; color:${isKnowledgeEnabled ? 'var(--color-success)' : 'var(--text-muted)'}; font-size:12px;">
            ${isKnowledgeEnabled ? '已连接 Qdrant 向量库' : '当前为轻量模式 (未开启向量检索)'}
          </span>
        </div>

        ${!isKnowledgeEnabled ? `
          <div class="card" style="background: rgba(2,132,199,0.06); border-color: rgba(56,189,248,0.25);">
            <div class="card-body flex items-center gap-3">
              <div style="color: var(--accent-light);">${Utils.getIcon('info', 24)}</div>
              <div style="font-size: 13px; color: var(--text-secondary);">
                当前后端运行在<strong>轻量核心模式</strong>下，优先使用 OpenAPI Schema 静态证据推导接口规划。
                如需开启 Qdrant 向量检索与 MinIO 业务文档解析，请在启动时加载完整知识库 Profile。
              </div>
            </div>
          </div>
        ` : ''}

        <!-- Upload Document Card -->
        <div class="card">
          <div class="card-header">
            <div class="flex items-center gap-2">
              ${Utils.getIcon('upload-cloud', 18)}
              <h3 style="font-size: 15px; font-weight:600;">上传业务文档</h3>
            </div>
            <span class="badge" style="background:rgba(255,255,255,0.06);">支持 MD, TXT, PDF</span>
          </div>
          <div class="card-body flex items-center gap-3">
            <input type="file" id="knowledge-file-input" accept=".md,.txt,.pdf" style="display: none;">
            <button class="btn btn-secondary btn-sm" id="btn-browse-doc" ${!isKnowledgeEnabled ? 'disabled' : ''}>
              ${Utils.getIcon('plus', 14)} 选择文档上传
            </button>
            <span style="font-size: 12px; color: var(--text-muted);">
              文档将被自动切分 Chunk 并生成向量嵌入
            </span>
          </div>
        </div>

        <!-- Documents Table -->
        <div class="card">
          <div class="card-header flex justify-between items-center">
            <h4 style="font-size: 14px; font-weight:600;">已导入文档列表</h4>
            <button class="btn btn-ghost btn-sm" id="btn-refresh-docs" ${!isKnowledgeEnabled ? 'disabled' : ''}>
              ${Utils.getIcon('refresh-cw', 13)}
            </button>
          </div>
          <div class="card-body" style="padding: 0;">
            <table style="width: 100%; border-collapse: collapse; text-align: left; font-size: 13px;">
              <thead>
                <tr style="border-bottom: 1px solid var(--border-subtle); color: var(--text-muted); font-size: 12px;">
                  <th style="padding: 12px 16px;">文档 ID</th>
                  <th style="padding: 12px 16px;">文件名称</th>
                  <th style="padding: 12px 16px;">状态</th>
                  <th style="padding: 12px 16px;">上传时间</th>
                  <th style="padding: 12px 16px; text-align: right;">操作</th>
                </tr>
              </thead>
              <tbody id="docs-tbody">
                <tr><td colspan="5" style="padding: 32px; text-align: center; color: var(--text-muted);">加载中...</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Hybrid Search Test -->
        <div class="card">
          <div class="card-header">
            <div class="flex items-center gap-2">
              ${Utils.getIcon('search', 18)}
              <h4 style="font-size: 14px; font-weight:600;">混合检索联调测试 (BM25 + RRF)</h4>
            </div>
          </div>
          <div class="card-body flex flex-col gap-3">
            <div class="flex gap-2">
              <input class="form-input" id="search-query-input" placeholder="输入业务关键词或自然语言问题进行检索测试..." style="flex: 1;">
              <button class="btn btn-primary" id="btn-run-search" ${!isKnowledgeEnabled ? 'disabled' : ''}>
                ${Utils.getIcon('search', 14)} 检索
              </button>
            </div>

            <div id="search-results-box" style="display: none; margin-top: 8px;">
              <pre class="json-viewer" id="search-results-viewer">-</pre>
            </div>
          </div>
        </div>
      </div>
    `;

    const fileInput = container.querySelector('#knowledge-file-input');
    const browseBtn = container.querySelector('#btn-browse-doc');
    browseBtn.onclick = () => fileInput.click();

    fileInput.onchange = async () => {
      if (fileInput.files && fileInput.files[0] && state.currentProject) {
        try {
          Utils.showToast('正在上传并向量化切分...', 'info');
          await Api.knowledge.upload(state.currentProject.id, fileInput.files[0]);
          Utils.showToast('文档上传成功', 'success');
          await this.loadDocs(container, state);
        } catch (err) {
          Utils.showToast(`上传失败: ${err.message}`, 'error');
        }
      }
    };

    container.querySelector('#btn-refresh-docs').onclick = () => {
      if (isKnowledgeEnabled) void this.loadDocs(container, state);
    };

    container.querySelector('#btn-run-search').onclick = async () => {
      const query = container.querySelector('#search-query-input').value.trim();
      if (!query || !state.currentProject) return;
      try {
        const results = await Api.knowledge.search(state.currentProject.id, query, 5);
        const box = container.querySelector('#search-results-box');
        const viewer = container.querySelector('#search-results-viewer');
        box.style.display = 'block';
        viewer.innerHTML = Utils.highlightJson(results);
      } catch (err) {
        Utils.showToast(`检索失败: ${err.message}`, 'error');
      }
    };

    if (isKnowledgeEnabled) {
      await this.loadDocs(container, state);
    } else {
      container.querySelector('#docs-tbody').innerHTML = `
        <tr><td colspan="5" style="padding:32px; text-align:center; color:var(--text-muted)">轻量模式未启用业务知识库</td></tr>
      `;
    }
  },

  async loadDocs(container, state) {
    if (!state.currentProject || !state.systemOverview?.knowledgeEnabled) return;
    const tbody = container.querySelector('#docs-tbody');
    try {
      const docs = await Api.knowledge.list(state.currentProject.id);
      if (docs.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" style="padding:32px; text-align:center; color:var(--text-muted)">暂未上传业务文档</td></tr>`;
        return;
      }

      tbody.innerHTML = docs.map(d => `
        <tr style="border-bottom: 1px solid var(--border-subtle);">
          <td style="padding: 12px 16px; font-family: var(--font-mono); color: var(--text-muted);">${d.id}</td>
          <td style="padding: 12px 16px; font-weight: 600;">${Utils.escapeHtml(d.fileName)}</td>
          <td style="padding: 12px 16px;">
            <span class="badge" style="background:var(--color-success-bg); color:var(--color-success);">${d.status}</span>
          </td>
          <td style="padding: 12px 16px; font-size: 12px; color: var(--text-muted);">${Utils.formatDateTime(d.createdAt)}</td>
          <td style="padding: 12px 16px; text-align: right;">
            <button class="btn btn-ghost btn-sm action-del-doc" style="color:var(--color-danger)" data-doc-id="${d.id}">
              ${Utils.getIcon('trash', 14)}
            </button>
          </td>
        </tr>
      `).join('');

      tbody.querySelectorAll('.action-del-doc').forEach(btn => {
        btn.onclick = async () => {
          const did = btn.getAttribute('data-doc-id');
          if (!confirm('确定删除该知识文档？')) return;
          try {
            await Api.knowledge.delete(state.currentProject.id, did);
            Utils.showToast('文档已删除', 'success');
            await this.loadDocs(container, state);
          } catch (err) {
            Utils.showToast(err.message, 'error');
          }
        };
      });

    } catch (err) {
      console.error('加载知识库文档失败：', err);
    }
  }
};
