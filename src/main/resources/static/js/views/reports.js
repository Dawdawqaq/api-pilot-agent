/**
 * Test Reports & Audit View
 */
import { Api } from '../api.js';
import { Utils } from '../utils.js';

export const ReportsView = {
  activeReport: null,

  async render(container, state, navigate) {
    this.activeReport = null;
    container.innerHTML = `
      <div class="flex flex-col gap-6" style="max-width: 1200px; margin: 0 auto;">
        <!-- Header -->
        <div class="flex justify-between items-center flex-wrap gap-4">
          <div>
            <h2>测试报告与执行审计</h2>
            <p style="font-size: 13px;">查看持久化测试产物、脱敏报文、Schema 契约校验与断言审计记录</p>
          </div>
          <div class="flex items-center gap-2">
            <button class="btn btn-secondary btn-sm" id="btn-refresh-reports">
              ${Utils.getIcon('refresh-cw', 14)} 刷新报告
            </button>
          </div>
        </div>

        <div style="display: grid; grid-template-columns: 340px 1fr; gap: var(--space-4);">
          <!-- Left: Reports History List -->
          <div class="card" style="display: flex; flex-direction: column; height: calc(100vh - 160px);">
            <div class="card-header">
              <h4 style="font-size: 13px; font-weight:600;">历史报告列表</h4>
              <span id="reports-count" class="badge" style="background:rgba(255,255,255,0.06);">0</span>
            </div>
            <div class="card-body" style="padding: 0; overflow-y: auto; flex: 1;">
              <div id="reports-list-container" class="flex flex-col">
                <div style="padding: 32px; text-align: center; color: var(--text-muted); font-size: 13px;">加载中...</div>
              </div>
            </div>
          </div>

          <!-- Right: Report Detail Drilldown -->
          <div class="card" style="display: flex; flex-direction: column; height: calc(100vh - 160px); overflow-y: auto;" id="report-detail-card">
            <div id="report-detail-placeholder" style="padding: 64px 32px; text-align: center; color: var(--text-muted);">
              ${Utils.getIcon('file-text', 36)}
              <p style="margin-top: 12px; font-size: 14px;">请在左侧列表中选择一份测试报告查看详细审计明细</p>
            </div>

            <div id="report-detail-content" style="display: none;" class="flex flex-col gap-4">
              <!-- Report Detail Header -->
              <div class="card-header flex justify-between items-center flex-wrap gap-3">
                <div class="flex items-center gap-3">
                  <span class="badge" id="rd-status-badge">SUCCESS</span>
                  <h3 id="rd-title" style="font-size: 16px; font-weight: 700;">-</h3>
                </div>
                <div class="flex items-center gap-2">
                  <button class="btn btn-secondary btn-sm" id="btn-copy-markdown">
                    ${Utils.getIcon('copy', 13)} 复制 Markdown
                  </button>
                  <button class="btn btn-primary btn-sm" id="btn-export-junit">
                    ${Utils.getIcon('download', 13)} 导出 JUnit XML
                  </button>
                </div>
              </div>

              <div class="card-body flex flex-col gap-4">
                <!-- Summary Metrics Bar -->
                <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--space-3); background: var(--bg-surface-elevated); padding: var(--space-3); border-radius: var(--radius-md);">
                  <div>
                    <span style="font-size: 11px; color: var(--text-muted);">测试步骤</span>
                    <div style="font-size: 16px; font-weight: 700; font-family: var(--font-mono);" id="rd-steps-stat">-</div>
                  </div>
                  <div>
                    <span style="font-size: 11px; color: var(--text-muted);">执行耗时</span>
                    <div style="font-size: 16px; font-weight: 700; font-family: var(--font-mono);" id="rd-duration">-</div>
                  </div>
                  <div>
                    <span style="font-size: 11px; color: var(--text-muted);">工具调用</span>
                    <div style="font-size: 16px; font-weight: 700; font-family: var(--font-mono);" id="rd-tools">-</div>
                  </div>
                  <div>
                    <span style="font-size: 11px; color: var(--text-muted);">生成时间</span>
                    <div style="font-size: 13px; font-weight: 500;" id="rd-created-at">-</div>
                  </div>
                </div>

                <!-- Text Summary & Evidence -->
                <div class="flex flex-col gap-2">
                  <div style="font-size: 13px; line-height: 1.5; color: var(--text-primary); padding: var(--space-3); background: rgba(0,0,0,0.2); border-radius: var(--radius-md); border-left: 3px solid var(--accent);" id="rd-summary">
                    -
                  </div>

                  <div id="rd-evidence-box" style="display: none; font-size: 12px; color: var(--text-secondary);">
                    <span style="font-weight: 600; color: var(--text-muted);">规划依据 (Evidence Citations):</span>
                    <ul id="rd-evidence-list" style="margin-left: 18px; margin-top: 4px;"></ul>
                  </div>
                </div>

                <!-- Step Results List -->
                <div class="flex flex-col gap-2">
                  <h4 style="font-size: 14px; font-weight:600;">步骤报文与断言明细</h4>
                  <div id="rd-steps-container" class="flex flex-col gap-3"></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    `;

    container.querySelector('#btn-refresh-reports').onclick = () => this.loadReports(container, state);

    container.querySelector('#btn-export-junit').onclick = async () => {
      if (!this.activeReport || !state.currentProject) return;
      try {
        const xml = await Api.reports.exportJUnitXml(state.currentProject.id, this.activeReport.id);
        const blob = new Blob([xml], { type: 'application/xml' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `junit-report-${this.activeReport.id}.xml`;
        a.click();
        URL.revokeObjectURL(url);
        Utils.showToast('JUnit XML 文件已下载', 'success');
      } catch (err) {
        Utils.showToast(`导出失败: ${err.message}`, 'error');
      }
    };

    container.querySelector('#btn-copy-markdown').onclick = () => {
      if (!this.activeReport) return;
      const r = this.activeReport;
      const md = `## 测试报告: ${r.title}\n\n- 状态: ${r.status}\n- 步骤: ${r.passedSteps}/${r.totalSteps} 通过\n- 耗时: ${Utils.formatDuration(r.durationMs)}\n\n### 概要\n${r.summary || '无'}\n`;
      Utils.copyToClipboard(md, 'Markdown 摘要已复制');
    };

    await this.loadReports(container, state);
  },

  async loadReports(container, state) {
    if (!state.currentProject) return;
    const listEl = container.querySelector('#reports-list-container');
    const countEl = container.querySelector('#reports-count');

    try {
      const reports = await Api.reports.list(state.currentProject.id, 25);
      if (countEl) countEl.textContent = reports.length;

      if (reports.length === 0) {
        listEl.innerHTML = `
          <div style="padding: 32px; text-align: center; color: var(--text-muted); font-size: 13px;">
            当前项目暂无测试报告。完成一次 Agent 任务后即可生成。
          </div>
        `;
        return;
      }

      listEl.innerHTML = reports.map(r => {
        const isPassed = r.status === 'SUCCEEDED' || r.status === 'PASSED';
        const isSelected = this.activeReport && this.activeReport.id === r.id;
        return `
          <div class="report-item" data-id="${r.id}" style="padding: 12px 14px; border-bottom: 1px solid var(--border-subtle); cursor: pointer; background: ${isSelected ? 'var(--bg-surface-hover)' : 'transparent'}; transition: background var(--transition-fast);">
            <div class="flex justify-between items-center" style="margin-bottom: 4px;">
              <span class="badge" style="background:${isPassed ? 'var(--color-success-bg)' : 'var(--color-danger-bg)'}; color:${isPassed ? 'var(--color-success)' : 'var(--color-danger)'}; font-size:10px;">
                ${r.status}
              </span>
              <span style="font-size: 11px; color: var(--text-muted);">${Utils.formatTime(r.createdAt)}</span>
            </div>
            <div class="truncate" style="font-weight: 600; font-size: 13px; color: var(--text-primary);">
              ${Utils.escapeHtml(r.title || `测试报告 #${r.id}`)}
            </div>
            <div class="flex justify-between items-center" style="font-size: 11px; color: var(--text-muted); margin-top: 4px;">
              <span>${r.passedSteps} / ${r.totalSteps} 步通过</span>
              <span class="tabular-nums">${Utils.formatDuration(r.durationMs)}</span>
            </div>
          </div>
        `;
      }).join('');

      listEl.querySelectorAll('.report-item').forEach(el => {
        el.onclick = () => {
          const rid = el.getAttribute('data-id');
          this.selectReport(rid, container, state);
        };
      });

      // Auto select first report if none selected
      if (reports.length > 0 && !this.activeReport) {
        await this.selectReport(reports[0].id, container, state);
      }
    } catch (err) {
      console.error('Failed to load reports:', err);
    }
  },

  async selectReport(reportId, container, state) {
    try {
      const fullReport = await Api.reports.get(state.currentProject.id, reportId);
      this.activeReport = fullReport;

      const placeholder = container.querySelector('#report-detail-placeholder');
      const content = container.querySelector('#report-detail-content');
      if (placeholder) placeholder.style.display = 'none';
      if (content) content.style.display = 'flex';

      // Header
      const titleEl = container.querySelector('#rd-title');
      const badgeEl = container.querySelector('#rd-status-badge');
      const stepsStatEl = container.querySelector('#rd-steps-stat');
      const durationEl = container.querySelector('#rd-duration');
      const toolsEl = container.querySelector('#rd-tools');
      const createdEl = container.querySelector('#rd-created-at');
      const summaryEl = container.querySelector('#rd-summary');

      if (titleEl) titleEl.textContent = fullReport.title || `测试报告 #${fullReport.id}`;
      if (badgeEl) {
        const isPassed = fullReport.status === 'SUCCEEDED' || fullReport.status === 'PASSED';
        badgeEl.textContent = fullReport.status;
        badgeEl.style.background = isPassed ? 'var(--color-success-bg)' : 'var(--color-danger-bg)';
        badgeEl.style.color = isPassed ? 'var(--color-success)' : 'var(--color-danger)';
      }
      if (stepsStatEl) stepsStatEl.textContent = `${fullReport.passedSteps} / ${fullReport.totalSteps} 通过`;
      if (durationEl) durationEl.textContent = Utils.formatDuration(fullReport.durationMs);
      if (toolsEl) toolsEl.textContent = `${fullReport.totalToolCalls || 0} 次`;
      if (createdEl) createdEl.textContent = Utils.formatDateTime(fullReport.createdAt);
      if (summaryEl) summaryEl.textContent = fullReport.summary || '报告无附加总结说明。';

      // Evidence citations
      const evidenceBox = container.querySelector('#rd-evidence-box');
      const evidenceList = container.querySelector('#rd-evidence-list');
      if (fullReport.evidenceCitations && fullReport.evidenceCitations.length > 0) {
        evidenceBox.style.display = 'block';
        evidenceList.innerHTML = fullReport.evidenceCitations.map(c => `<li>${Utils.escapeHtml(c)}</li>`).join('');
      } else {
        evidenceBox.style.display = 'none';
      }

      // Steps
      const stepsContainer = container.querySelector('#rd-steps-container');
      if (!stepsContainer) return;

      const steps = fullReport.steps || [];
      if (steps.length === 0) {
        stepsContainer.innerHTML = `<div style="padding:16px; color:var(--text-muted); font-size:12px;">暂无单步审计明细</div>`;
        return;
      }

      stepsContainer.innerHTML = steps.map(s => {
        const isStepSuccess = !!s.success;
        const method = (s.httpMethod || 'GET').toUpperCase();
        const stepStatus = isStepSuccess ? 'PASSED' : 'FAILED';
        const assertions = Array.isArray(s.assertions) ? s.assertions : [];
        const passedAssertions = assertions.filter(assertion => assertion.passed).length;
        let requestPath = s.requestUrl || '-';
        try {
          const url = new URL(s.requestUrl);
          requestPath = `${url.pathname}${url.search}`;
        } catch {
          // 相对路径或缺失地址直接按原值展示。
        }

        return `
          <div class="card" style="border-left: 3px solid ${isStepSuccess ? 'var(--color-success)' : 'var(--color-danger)'};">
            <div class="card-header flex justify-between items-center" style="padding: 8px 12px;">
              <div class="flex items-center gap-2">
                <span class="badge" style="background:rgba(255,255,255,0.06); font-family:var(--font-mono); font-size:11px;">#${s.stepIndex}</span>
                <span class="method-badge method-${method}">${method}</span>
                <span class="code-font" style="font-weight:600; font-size:13px;">${Utils.escapeHtml(requestPath)}</span>
              </div>
              <div class="flex items-center gap-2">
                <span class="tabular-nums" style="font-size:12px; color:var(--text-muted);">${Utils.formatDuration(s.durationMs)}</span>
                <span class="badge" style="background:${isStepSuccess ? 'var(--color-success-bg)' : 'var(--color-danger-bg)'}; color:${isStepSuccess ? 'var(--color-success)' : 'var(--color-danger)'};">
                  ${stepStatus}
                </span>
              </div>
            </div>
            <div class="card-body flex flex-col gap-2" style="padding: 10px 14px;">
              <div style="font-size: 13px; font-weight: 500;">${Utils.escapeHtml(s.stepName || '步骤调用')}</div>

              <!-- Response Status & Headers -->
              <div class="flex items-center gap-3" style="font-size: 12px;">
                <span>HTTP 响应状态码: <strong class="tabular-nums" style="color:${s.responseStatus >= 200 && s.responseStatus < 300 ? 'var(--color-success)' : 'var(--color-danger)'}">${s.responseStatus || '-'}</strong></span>
                <span>断言结果: <strong style="color:${passedAssertions === assertions.length ? 'var(--color-success)' : 'var(--color-danger)'}">${passedAssertions} / ${assertions.length} 通过</strong></span>
              </div>

              <!-- Accordion for Request & Response Payload (Masked) -->
              <details style="font-size: 12px; margin-top: 4px;">
                <summary style="cursor: pointer; color: var(--accent-light);">展开脱敏请求/响应报文及断言</summary>
                <div class="flex flex-col gap-2" style="margin-top: 6px;">
                  <div>
                    <span style="color:var(--text-muted)">请求地址 (Request URL):</span>
                    <pre class="json-viewer" style="max-height:80px;">${Utils.escapeHtml(s.requestUrl || '-')}</pre>
                  </div>

                  ${s.requestHeaders ? `
                    <div>
                      <span style="color:var(--text-muted)">脱敏请求头 (Request Headers):</span>
                      <pre class="json-viewer" style="max-height:160px;">${Utils.highlightJson(s.requestHeaders)}</pre>
                    </div>
                  ` : ''}

                  ${s.requestBody ? `
                    <div>
                      <span style="color:var(--text-muted)">请求体 (Request Body):</span>
                      <pre class="json-viewer" style="max-height:160px;">${Utils.highlightJson(s.requestBody)}</pre>
                    </div>
                  ` : ''}

                  <div>
                    <span style="color:var(--text-muted)">响应头 (Response Headers):</span>
                    <pre class="json-viewer" style="max-height:160px;">${Utils.highlightJson(s.responseHeaders)}</pre>
                  </div>

                  <div>
                    <span style="color:var(--text-muted)">脱敏响应体 (Response Body):</span>
                    <pre class="json-viewer" style="max-height:200px;">${Utils.highlightJson(s.responseBody)}</pre>
                  </div>

                  ${s.assertions ? `
                    <div>
                      <span style="color:var(--text-muted)">断言判断结果 (Assertions):</span>
                      <pre class="json-viewer" style="max-height:160px;">${Utils.highlightJson(s.assertions)}</pre>
                    </div>
                  ` : ''}

                  ${s.errorMessage ? `
                    <div style="color:var(--color-danger); padding:6px; background:var(--color-danger-bg); border-radius:var(--radius-sm);">
                      错误信息: ${Utils.escapeHtml(s.errorMessage)}
                    </div>
                  ` : ''}
                </div>
              </details>
            </div>
          </div>
        `;
      }).join('');

    } catch (err) {
      console.error('Failed to select report:', err);
    }
  }
};
