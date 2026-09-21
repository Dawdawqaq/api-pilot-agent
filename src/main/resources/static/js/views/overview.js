/**
 * Overview & Dashboard View
 */
import { Api } from '../api.js';
import { Utils } from '../utils.js';

export const OverviewView = {
  async render(container, state, navigate) {
    container.innerHTML = `
      <div class="flex flex-col gap-6" style="max-width: 1200px; margin: 0 auto;">
        <!-- Header Banner -->
        <div class="flex justify-between items-center flex-wrap gap-4" style="background: linear-gradient(90deg, rgba(2,132,199,0.12) 0%, rgba(15,23,42,0.6) 100%); border: 1px solid var(--border-default); border-radius: var(--radius-xl); padding: var(--space-6);">
          <div class="flex flex-col gap-2">
            <div class="flex items-center gap-2">
              <span class="badge" style="background:rgba(56,189,248,0.2); color:var(--accent-light); font-size:11px;">Agent 智能中枢</span>
              <span class="mode-tag" id="overview-mode-tag">检测中...</span>
            </div>
            <h1 style="font-size:24px; font-weight:700;">欢迎使用 DocHelper (ApiPilot)</h1>
            <p style="font-size:14px; color:var(--text-secondary); max-width:640px;">
              面向开发者的 REST API 调用链自动化测试 Agent。通过自然语言目标驱动大模型规划，由本地程序执行器完成受控调用、Schema 契约校验与覆盖率报告。
            </p>
          </div>
          <div class="flex items-center gap-3">
            <button class="btn btn-primary" id="btn-quick-new-task">
              ${Utils.getIcon('play', 16)} 新建测试任务
            </button>
            <button class="btn btn-secondary" id="btn-quick-openapi">
              ${Utils.getIcon('upload-cloud', 16)} 导入 OpenAPI
            </button>
          </div>
        </div>

        <!-- Metric Cards -->
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: var(--space-4);">
          <div class="stat-card">
            <span class="stat-label">生效模型</span>
            <div class="stat-value" style="font-size: 18px; color: var(--accent-light);" id="stat-model">-</div>
            <span class="stat-meta" id="stat-profiles">-</span>
          </div>

          <div class="stat-card">
            <span class="stat-label">当前项目</span>
            <div class="stat-value" style="font-size: 18px;" id="stat-current-project">-</div>
            <span class="stat-meta" id="stat-env">-</span>
          </div>

          <div class="stat-card">
            <span class="stat-label">任务总数</span>
            <div class="stat-value" id="stat-total-tasks">0</div>
            <span class="stat-meta">历史测试执行任务</span>
          </div>

          <div class="stat-card">
            <span class="stat-label">任务成功率</span>
            <div class="stat-value" style="color: var(--color-success);" id="stat-success-rate">-</div>
            <span class="stat-meta" id="stat-success-count">0 次终态任务</span>
          </div>
        </div>

        <!-- Recent Tasks & Quick Actions -->
        <div class="card">
          <div class="card-header">
            <div class="flex items-center gap-2">
              ${Utils.getIcon('activity', 18)}
              <h3 style="font-size: 15px; font-weight:600;">近期 Agent 任务历史</h3>
            </div>
            <button class="btn btn-ghost btn-sm" id="btn-refresh-overview">
              ${Utils.getIcon('refresh-cw', 14)} 刷新
            </button>
          </div>
          <div class="card-body" style="padding: 0;">
            <div style="overflow-x: auto;">
              <table style="width: 100%; border-collapse: collapse; text-align: left; font-size: 13px;">
                <thead>
                  <tr style="border-bottom: 1px solid var(--border-subtle); color: var(--text-muted); font-size: 12px;">
                    <th style="padding: 12px 16px;">任务 ID</th>
                    <th style="padding: 12px 16px;">自然语言目标</th>
                    <th style="padding: 12px 16px;">状态</th>
                    <th style="padding: 12px 16px;">步骤 / 工具</th>
                    <th style="padding: 12px 16px;">创建时间</th>
                    <th style="padding: 12px 16px; text-align: right;">操作</th>
                  </tr>
                </thead>
                <tbody id="overview-tasks-tbody">
                  <tr>
                    <td colspan="6" style="padding: 32px; text-align: center; color: var(--text-muted);">
                      正在拉取近期任务...
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    `;

    // Bind Quick Action Buttons
    container.querySelector('#btn-quick-new-task').onclick = () => navigate('workbench');
    container.querySelector('#btn-quick-openapi').onclick = () => navigate('openapi');
    container.querySelector('#btn-refresh-overview').onclick = () => this.loadData(container, state, navigate);

    await this.loadData(container, state, navigate);
  },

  async loadData(container, state, navigate) {
    // 1. Fetch System Overview
    try {
      const overview = await Api.system.getOverview();
      state.systemOverview = overview;
      const modelEl = container.querySelector('#stat-model');
      if (modelEl) modelEl.textContent = overview.chatModel || '默认模型';

      const profileEl = container.querySelector('#stat-profiles');
      if (profileEl) {
        profileEl.textContent = overview.knowledgeEnabled ? '知识库增强模式' : '轻量核心模式';
      }

      const modeTag = container.querySelector('#overview-mode-tag');
      if (modeTag) {
        modeTag.textContent = overview.knowledgeEnabled ? '混合知识库模式 (Qdrant)' : '轻量核心模式 (OpenAPI)';
        modeTag.style.color = overview.knowledgeEnabled ? 'var(--accent-light)' : 'var(--color-success)';
      }
    } catch (err) {
      console.error('Failed to load system overview:', err);
    }

    // 2. Fetch Project & Env
    const projEl = container.querySelector('#stat-current-project');
    const envEl = container.querySelector('#stat-env');
    if (state.currentProject) {
      if (projEl) projEl.textContent = state.currentProject.name;
      if (envEl) envEl.textContent = state.currentEnvironment ? `环境: ${state.currentEnvironment.name}` : '未选择环境';
    } else {
      if (projEl) projEl.textContent = '未选择项目';
      if (envEl) envEl.textContent = '-';
    }

    // 3. Fetch Tasks for Current Project
    if (!state.currentProject) {
      const tbody = container.querySelector('#overview-tasks-tbody');
      if (tbody) {
        tbody.innerHTML = `
          <tr>
            <td colspan="6" style="padding: 32px; text-align: center; color: var(--text-muted);">
              请先在顶部创建或选择一个项目
            </td>
          </tr>
        `;
      }
      return;
    }

    try {
      const tasks = await Api.agent.list(state.currentProject.id, 15);
      const totalEl = container.querySelector('#stat-total-tasks');
      const rateEl = container.querySelector('#stat-success-rate');
      const countEl = container.querySelector('#stat-success-count');

      if (totalEl) totalEl.textContent = tasks.length;

      // Calculate success rate among completed terminal tasks
      const completedTasks = tasks.filter(t => ['SUCCEEDED', 'FAILED', 'CANCELLED', 'NEEDS_REVIEW'].includes(t.status));
      const succeededTasks = tasks.filter(t => t.status === 'SUCCEEDED');
      if (completedTasks.length > 0) {
        const rate = ((succeededTasks.length / completedTasks.length) * 100).toFixed(0);
        if (rateEl) rateEl.textContent = `${rate}%`;
        if (countEl) countEl.textContent = `${succeededTasks.length} / ${completedTasks.length} 终态任务成功`;
      } else {
        if (rateEl) rateEl.textContent = '-';
        if (countEl) countEl.textContent = '暂无终态任务';
      }

      const tbody = container.querySelector('#overview-tasks-tbody');
      if (!tbody) return;

      if (tasks.length === 0) {
        tbody.innerHTML = `
          <tr>
            <td colspan="6" style="padding: 32px; text-align: center; color: var(--text-muted);">
              当前项目暂无任务，点击“新建测试任务”开始体验 Agent 规划执行
            </td>
          </tr>
        `;
        return;
      }

      tbody.innerHTML = tasks.map(task => {
        return `
          <tr style="border-bottom: 1px solid var(--border-subtle); transition: background var(--transition-fast);" onmouseover="this.style.background='var(--bg-surface-hover)'" onmouseout="this.style.background='transparent'">
            <td style="padding: 12px 16px; font-family: var(--font-mono); font-size: 12px; color: var(--text-muted);">
              ${task.id}
            </td>
            <td style="padding: 12px 16px; max-width: 320px;" class="truncate" title="${Utils.escapeHtml(task.goal)}">
              ${Utils.escapeHtml(task.goal)}
            </td>
            <td style="padding: 12px 16px;">
              <span class="status-badge status-${task.status}">${task.status}</span>
            </td>
            <td style="padding: 12px 16px; font-family: var(--font-mono); font-size: 12px;">
              ${task.currentStep} / ${task.plan ? task.plan.length : 0} 步 (${task.toolCallCount} 工具)
            </td>
            <td style="padding: 12px 16px; color: var(--text-secondary); font-size: 12px;">
              ${Utils.formatDateTime(task.createdAt)}
            </td>
            <td style="padding: 12px 16px; text-align: right;">
              <button class="btn btn-ghost btn-sm action-inspect-task" data-task-id="${task.id}">
                ${Utils.getIcon('terminal', 13)} 查看工作台
              </button>
            </td>
          </tr>
        `;
      }).join('');

      tbody.querySelectorAll('.action-inspect-task').forEach(btn => {
        btn.onclick = () => {
          state.activeTaskId = btn.getAttribute('data-task-id');
          navigate('workbench');
        };
      });

    } catch (err) {
      console.error('Failed to load tasks in overview:', err);
    }
  }
};
