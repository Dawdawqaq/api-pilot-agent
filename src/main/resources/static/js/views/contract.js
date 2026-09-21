/**
 * Contract Tests & Failure Replay View
 */
import { Api } from '../api.js';
import { Utils } from '../utils.js';

export const ContractView = {
  async render(container, state, navigate) {
    if (state.currentProject && (!state.endpoints || state.endpoints.length === 0)) {
      try {
        state.endpoints = await Api.openapi.listEndpoints(state.currentProject.id);
      } catch (err) {
        Utils.showToast(`接口目录加载失败: ${err.message}`, 'error');
      }
    }

    container.innerHTML = `
      <div class="flex flex-col gap-6" style="max-width: 1200px; margin: 0 auto;">
        <!-- Header -->
        <div class="flex justify-between items-center flex-wrap gap-4">
          <div>
            <h2>契约测试与负向用例</h2>
            <p style="font-size: 13px;">基于 OpenAPI Schema 自动推导缺参、类型不符、非法枚举等负向用例，并支持失败场景回放</p>
          </div>
        </div>

        <!-- Negative Cases Generator Card -->
        <div class="card">
          <div class="card-header flex justify-between items-center flex-wrap gap-3">
            <div class="flex items-center gap-2">
              ${Utils.getIcon('shield', 18)}
              <h3 style="font-size: 15px; font-weight: 600;">自动推导负向用例 (Negative Cases)</h3>
            </div>
            <div class="flex items-center gap-2">
              <select class="form-select" id="select-endpoint-negative" style="width: 320px; font-size: 12px; font-family: var(--font-mono);">
                <option value="">-- 选择接口以推导负向契约用例 --</option>
              </select>
              <button class="btn btn-primary btn-sm" id="btn-generate-negative">
                ${Utils.getIcon('play', 14)} 推导用例
              </button>
            </div>
          </div>
          <div class="card-body" style="padding: 0;">
            <div style="overflow-x: auto;">
              <table style="width: 100%; border-collapse: collapse; text-align: left; font-size: 13px;">
                <thead>
                  <tr style="border-bottom: 1px solid var(--border-subtle); color: var(--text-muted); font-size: 12px;">
                    <th style="padding: 12px 16px;">用例名称</th>
                    <th style="padding: 12px 16px;">变异类型 (Mutation)</th>
                    <th style="padding: 12px 16px;">目标参数</th>
                    <th style="padding: 12px 16px;">预期结果</th>
                  </tr>
                </thead>
                <tbody id="negative-cases-tbody">
                  <tr>
                    <td colspan="4" style="padding: 32px; text-align: center; color: var(--text-muted);">
                      请先选择上方接口并点击“推导用例”
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- Failure Replay Card -->
        <div class="card">
          <div class="card-header">
            <div class="flex items-center gap-2">
              ${Utils.getIcon('refresh-cw', 18)}
              <h3 style="font-size: 15px; font-weight: 600;">失败场景重放 (Failure Replay)</h3>
            </div>
            <span class="badge" style="background:rgba(255,255,255,0.06);">只读接口直接回放，写接口受人工确认保护</span>
          </div>
          <div class="card-body flex flex-col gap-3">
            <div class="flex items-center gap-2">
              <input class="form-input" id="replay-id-input" placeholder="输入失败回放记录 ID (Replay ID)" style="max-width: 300px; font-size: 13px;">
              <button class="btn btn-secondary btn-sm" id="btn-query-replay">
                ${Utils.getIcon('search', 14)} 查询回放
              </button>
              <button class="btn btn-primary btn-sm" id="btn-exec-replay">
                ${Utils.getIcon('play', 14)} 执行重放
              </button>
            </div>

            <div id="replay-result-box" style="display: none; margin-top: 8px;">
              <pre class="json-viewer" id="replay-result-viewer">-</pre>
            </div>
          </div>
        </div>
      </div>
    `;

    // Populate endpoints dropdown
    const select = container.querySelector('#select-endpoint-negative');
    if (state.endpoints && state.endpoints.length > 0) {
      state.endpoints.forEach(ep => {
        const opt = document.createElement('option');
        opt.value = ep.id;
        opt.textContent = `${ep.httpMethod} ${ep.path} (${ep.summary || '无标题'})`;
        select.appendChild(opt);
      });
    }

    container.querySelector('#btn-generate-negative').onclick = async () => {
      const epId = select.value;
      if (!epId) {
        Utils.showToast('请选择接口', 'warning');
        return;
      }

      const tbody = container.querySelector('#negative-cases-tbody');
      tbody.innerHTML = `<tr><td colspan="4" style="padding:24px; text-align:center;">正在生成契约变异用例...</td></tr>`;

      try {
        const cases = await Api.contract.getNegativeCases(state.currentProject.id, epId);
        if (cases.length === 0) {
          tbody.innerHTML = `<tr><td colspan="4" style="padding:24px; text-align:center; color:var(--text-muted)">该接口参数未定义强制校验或无法自动变异</td></tr>`;
          return;
        }

        tbody.innerHTML = cases.map(c => `
          <tr style="border-bottom: 1px solid var(--border-subtle);">
            <td style="padding: 12px 16px; font-weight: 600;">${Utils.escapeHtml(c.caseType)}</td>
            <td style="padding: 12px 16px;">
              <span class="badge" style="background:rgba(245,158,11,0.15); color:var(--color-warning);">${Utils.escapeHtml(c.mutation || 'PARAM_INVALID')}</span>
            </td>
            <td style="padding: 12px 16px; font-family: var(--font-mono); font-weight:600; color:var(--color-danger);">
              ${Utils.escapeHtml(c.target || '-')}
            </td>
            <td style="padding: 12px 16px; font-size: 12px; color: var(--text-secondary);">
              ${Utils.escapeHtml(c.expectedOutcome || '-')}
            </td>
          </tr>
        `).join('');
      } catch (err) {
        tbody.innerHTML = `<tr><td colspan="4" style="padding:24px; text-align:center; color:var(--color-danger)">推导失败: ${Utils.escapeHtml(err.message)}</td></tr>`;
      }
    };

    // Replay actions
    container.querySelector('#btn-query-replay').onclick = async () => {
      const rid = container.querySelector('#replay-id-input').value.trim();
      if (!rid) return Utils.showToast('请输入 Replay ID', 'warning');
      try {
        const replay = await Api.contract.getReplay(state.currentProject.id, rid);
        const box = container.querySelector('#replay-result-box');
        const viewer = container.querySelector('#replay-result-viewer');
        box.style.display = 'block';
        viewer.innerHTML = Utils.highlightJson(replay);
      } catch (err) {
        Utils.showToast(err.message, 'error');
      }
    };

    container.querySelector('#btn-exec-replay').onclick = async () => {
      const rid = container.querySelector('#replay-id-input').value.trim();
      if (!rid) return Utils.showToast('请输入 Replay ID', 'warning');
      try {
        const res = await Api.contract.replay(state.currentProject.id, rid, {
          environmentId: state.currentEnvironment ? state.currentEnvironment.id : null
        });
        Utils.showToast('回放执行完成', 'success');
        const box = container.querySelector('#replay-result-box');
        const viewer = container.querySelector('#replay-result-viewer');
        box.style.display = 'block';
        viewer.innerHTML = Utils.highlightJson(res);
      } catch (err) {
        Utils.showToast(`回放失败: ${err.message}`, 'error');
      }
    };
  }
};
