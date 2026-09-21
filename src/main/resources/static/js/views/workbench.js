/**
 * Agent Task Workbench View
 * Core interface: natural language task submission, SSE real-time streaming,
 * step tree inspection, human-in-the-loop danger confirmation, and multi-turn plan revision.
 */
import { Api } from '../api.js';
import { Utils } from '../utils.js';
import { AgentStreamClient } from '../sse.js';

export const WorkbenchView = {
  streamClient: null,
  activeTask: null,
  eventsLog: [],
  terminalRefreshStarted: false,

  destroy() {
    if (this.streamClient) {
      this.streamClient.disconnect();
      this.streamClient = null;
    }
  },

  async render(container, state, navigate) {
    container.innerHTML = `
      <div class="workbench-layout">
        <!-- Left Column: Task Creator & History -->
        <div class="workbench-sidebar">
          <!-- Create Task Card -->
          <div class="card">
            <div class="card-header">
              <div class="flex items-center gap-2">
                ${Utils.getIcon('terminal', 16)}
                <h3 style="font-size: 14px; font-weight:600;">下发 Agent 测试目标</h3>
              </div>
              <span class="badge" style="background:rgba(56,189,248,0.15); color:var(--accent-light);">自然语言驱动</span>
            </div>
            <div class="card-body">
              <form id="form-create-task" class="flex flex-col gap-3">
                <div class="form-group" style="margin-bottom: 0;">
                  <label class="form-label" for="task-goal">
                    测试意图与目标 <span style="color:var(--color-danger)">*</span>
                  </label>
                  <textarea class="form-textarea" id="task-goal" required rows="3" placeholder="例如：查询文章列表，验证返回 200，并检查响应数据包含列表..."></textarea>
                </div>

                <div class="form-group" style="margin-bottom: 0;">
                  <label class="form-label flex justify-between" for="task-variables">
                    <span>初始变量 (加密入库)</span>
                    <span style="font-weight: normal; color: var(--text-muted); font-size: 11px;">JSON 对象</span>
                  </label>
                  <textarea class="form-textarea code-editor" id="task-variables" rows="2" placeholder='{"token": "demo_jwt_xxx", "userId": 1001}'></textarea>
                </div>

                <!-- Stub Plan Hint Toggle -->
                <div>
                  <button type="button" class="btn btn-ghost btn-sm" id="btn-toggle-hint" style="padding-left:0; font-size:12px; color:var(--text-muted);">
                    ${Utils.getIcon('settings', 13)} 高级：指定强类型计划 (Stub 调试)
                  </button>
                  <div id="plan-hint-wrapper" style="display: none; margin-top: 6px;">
                    <textarea class="form-textarea code-editor" id="task-plan-hint" rows="3" placeholder='[{"name":"Get Posts","method":"GET","path":"/api/posts"}]'></textarea>
                  </div>
                </div>

                <button type="submit" class="btn btn-primary" id="btn-submit-task" style="width: 100%; margin-top: 4px;">
                  ${Utils.getIcon('play', 15)} 启动 Agent 规划与执行
                </button>
              </form>
            </div>
          </div>

          <!-- Recent Tasks for Fast Switch -->
          <div class="card" style="flex: 1; display: flex; flex-direction: column; min-height: 260px;">
            <div class="card-header">
              <h4 style="font-size: 13px; font-weight:600;">项目近期任务</h4>
              <button class="btn btn-ghost btn-sm" id="btn-refresh-task-list" title="刷新列表">
                ${Utils.getIcon('refresh-cw', 13)}
              </button>
            </div>
            <div class="card-body" style="padding: 0; overflow-y: auto; flex: 1;">
              <div id="task-history-list" class="flex flex-col">
                <div style="padding: 24px; text-align: center; color: var(--text-muted); font-size: 12px;">加载中...</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Right Column: Real-Time Execution Inspector & Plan Steps -->
        <div class="workbench-main">
          <!-- Active Task Status Bar -->
          <div class="card" id="task-status-bar">
            <div class="card-header flex justify-between items-center flex-wrap gap-2">
              <div class="flex items-center gap-3">
                <span class="badge" style="background: rgba(255,255,255,0.06); font-family: var(--font-mono); font-size: 12px;" id="wb-task-id">
                  未选择任务
                </span>
                <span class="status-badge" id="wb-task-status">-</span>
                <span style="font-size: 12px; color: var(--text-muted);" id="wb-task-steps">-</span>
              </div>
              <div class="flex items-center gap-2">
                <button class="btn btn-danger btn-sm" id="btn-cancel-task" style="display: none;">
                  ${Utils.getIcon('x', 14)} 终止任务
                </button>
                <button class="btn btn-secondary btn-sm" id="btn-view-report" style="display: none;">
                  ${Utils.getIcon('file-text', 14)} 查看测试报告
                </button>
              </div>
            </div>

            <!-- Goal Display -->
            <div class="card-body" style="padding: var(--space-3) var(--space-4); border-bottom: 1px solid var(--border-subtle); background: rgba(0,0,0,0.15);">
              <div class="flex items-center gap-2">
                <span style="font-size: 12px; color: var(--text-muted); flex-shrink: 0;">当前目标:</span>
                <div id="wb-task-goal" style="font-size: 13px; font-weight: 500; color: var(--text-primary);" class="truncate">-</div>
              </div>
            </div>

            <!-- Danger Operation Confirmation Box (Inline Banner) -->
            <div id="danger-confirm-box" class="danger-panel" style="display: none; margin: var(--space-3) var(--space-4);">
              <div class="danger-panel-header">
                ${Utils.getIcon('alert-triangle', 18)}
                <span>安全策略警示：包含危险写/删除操作，需要人工核验确认</span>
              </div>
              <p style="font-size: 13px; color: #fecdd3; margin-bottom: 8px;">
                即将执行的请求为写操作。为了保障数据安全，大模型无法直接跳过确认；必须在确认后程序执行器才会继续。
              </p>
              <div class="flex items-center gap-2 flex-wrap" style="margin-bottom: 12px; font-size: 12px;">
                <span style="color: var(--text-muted);">计划哈希摘要:</span>
                <span class="hash-pill" id="confirm-plan-hash">-</span>
                <span id="confirm-countdown" style="color: var(--color-warning); font-size: 12px; font-weight: 600;"></span>
              </div>
              <div class="flex flex-col gap-2">
                <input class="form-input" id="confirm-decision-note" placeholder="可选核验备注信息（如：确认允许写入）" style="font-size: 12px; background: rgba(0,0,0,0.3);">
                <div class="flex gap-2 justify-end" style="margin-top: 4px;">
                  <button class="btn btn-ghost btn-sm" id="btn-reject-confirm" style="color: var(--color-danger);">
                    ${Utils.getIcon('x', 14)} 拒绝执行并终止
                  </button>
                  <button class="btn btn-danger btn-sm" id="btn-approve-confirm">
                    ${Utils.getIcon('check-circle', 14)} 批准并继续执行
                  </button>
                </div>
              </div>
            </div>

            <!-- Multi-Turn Plan Modification Bar -->
            <div id="plan-modify-box" style="display: none; padding: var(--space-3) var(--space-4); background: rgba(2, 132, 199, 0.08); border-bottom: 1px solid var(--border-subtle);">
              <div class="flex items-center gap-2">
                <span style="font-size: 12px; font-weight: 600; color: var(--accent-light); flex-shrink: 0;">
                  多轮对话修正:
                </span>
                <input class="form-input" id="input-modify-instruction" placeholder="例如：不要删除数据，改为查询详情；或者调整提取参数..." style="font-size: 12px;">
                <button class="btn btn-secondary btn-sm" id="btn-submit-modify">
                  ${Utils.getIcon('refresh-cw', 13)} 调整计划
                </button>
              </div>
            </div>
          </div>

          <!-- Steps Tree Card -->
          <div class="card" style="flex: 1; display: flex; flex-direction: column;">
            <div class="card-header">
              <div class="flex items-center gap-2">
                ${Utils.getIcon('layers', 16)}
                <h4 style="font-size: 14px; font-weight:600;">执行计划步骤 (Plan Steps)</h4>
              </div>
              <div class="flex items-center gap-2">
                <span id="plan-steps-count" style="font-size: 12px; color: var(--text-muted);">0 步骤</span>
              </div>
            </div>
            <div class="card-body" style="padding: var(--space-4); overflow-y: auto; flex: 1;">
              <div id="plan-steps-tree" class="step-tree">
                <div style="padding: 32px; text-align: center; color: var(--text-muted); font-size: 13px;">
                  暂无执行计划。启动任务后，Agent 将根据 OpenAPI Schema 进行候选选择并生成步骤。
                </div>
              </div>
            </div>
          </div>

          <!-- Real-Time Event & Tool Traces Console -->
          <div class="card" style="height: 240px; display: flex; flex-direction: column;">
            <div class="card-header" style="padding: 8px 16px;">
              <div class="flex items-center gap-2">
                ${Utils.getIcon('terminal', 14)}
                <h5 style="font-size: 12px; font-weight:600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted);">
                  实时事件与工具调用轨迹 (SSE Live Trace)
                </h5>
              </div>
              <div class="flex items-center gap-2">
                <span class="badge-dot" id="sse-live-dot" style="background: var(--text-disabled);"></span>
                <span id="sse-status-label" style="font-size: 11px; color: var(--text-muted);">离线</span>
              </div>
            </div>
            <div class="card-body" style="padding: 8px 12px; overflow-y: auto; flex: 1; background: #060911; font-family: var(--font-mono); font-size: 12px; line-height: 1.6;" id="event-log-console">
              <div class="event-log-empty" style="color: var(--text-disabled); font-style: italic;">等待任务开始...</div>
            </div>
          </div>
        </div>
      </div>
    `;

    // Handle prefill from OpenAPI or other views
    if (state.agentPrefillGoal) {
      container.querySelector('#task-goal').value = state.agentPrefillGoal;
      state.agentPrefillGoal = null;
    }

    // Toggle Stub hint
    const toggleHintBtn = container.querySelector('#btn-toggle-hint');
    const hintWrapper = container.querySelector('#plan-hint-wrapper');
    toggleHintBtn.onclick = () => {
      const isHidden = hintWrapper.style.display === 'none';
      hintWrapper.style.display = isHidden ? 'block' : 'none';
    };

    // Form Submit
    const form = container.querySelector('#form-create-task');
    form.onsubmit = async (e) => {
      e.preventDefault();
      await this.handleCreateTask(container, state);
    };

    // Task Actions
    container.querySelector('#btn-cancel-task').onclick = async () => {
      if (!this.activeTask) return;
      if (!confirm('确定要终止当前 Agent 任务吗？')) return;
      try {
        await Api.agent.cancel(state.currentProject.id, this.activeTask.id);
        Utils.showToast('已下发任务取消指令', 'info');
      } catch (err) {
        Utils.showToast(err.message, 'error');
      }
    };

    container.querySelector('#btn-view-report').onclick = () => {
      navigate('reports');
    };

    // Dangerous Confirmation Handlers
    container.querySelector('#btn-approve-confirm').onclick = async () => {
      await this.handleConfirmation(true, container, state);
    };

    container.querySelector('#btn-reject-confirm').onclick = async () => {
      await this.handleConfirmation(false, container, state);
    };

    // Multi-turn modify handler
    container.querySelector('#btn-submit-modify').onclick = async () => {
      await this.handleModifyPlan(container, state);
    };

    container.querySelector('#btn-refresh-task-list').onclick = () => this.loadTaskHistory(container, state);

    // Initial load
    await this.loadTaskHistory(container, state);

    // If an active task ID is passed in state, load it
    if (state.activeTaskId) {
      await this.selectTask(state.activeTaskId, container, state);
    }
  },

  async handleCreateTask(container, state) {
    if (!state.currentProject) {
      Utils.showToast('请先选择一个项目', 'warning');
      return;
    }

    const goal = container.querySelector('#task-goal').value.trim();
    if (!goal) {
      Utils.showToast('请输入测试目标', 'warning');
      return;
    }

    let initialVariables = null;
    const varText = container.querySelector('#task-variables').value.trim();
    if (varText) {
      try {
        initialVariables = JSON.parse(varText);
      } catch {
        Utils.showToast('初始变量必须是合法的 JSON 对象', 'warning');
        return;
      }
    }

    let planHint = null;
    const hintText = container.querySelector('#task-plan-hint').value.trim();
    if (hintText) {
      try {
        planHint = JSON.parse(hintText);
      } catch {
        Utils.showToast('强类型计划提示必须是合法的 JSON 数组', 'warning');
        return;
      }
    }

    const submitBtn = container.querySelector('#btn-submit-task');
    submitBtn.disabled = true;
    submitBtn.innerHTML = `<span class="spinner"></span> 正在提交任务...`;

    try {
      const payload = {
        goal,
        initialVariables,
        planHint,
        environmentId: state.currentEnvironment ? state.currentEnvironment.id : null
      };

      const task = await Api.agent.create(state.currentProject.id, payload);
      Utils.showToast(`任务 #${task.id} 创建成功，Agent 已启动`, 'success');

      // Clear variables input for safety
      container.querySelector('#task-variables').value = '';

      await this.loadTaskHistory(container, state);
      await this.selectTask(task.id, container, state);
    } catch (err) {
      Utils.showToast(`创建失败: ${err.message}`, 'error');
    } finally {
      submitBtn.disabled = false;
      submitBtn.innerHTML = `${Utils.getIcon('play', 15)} 启动 Agent 规划与执行`;
    }
  },

  async selectTask(taskId, container, state) {
    if (this.streamClient) {
      this.streamClient.disconnect();
      this.streamClient = null;
    }
    this.terminalRefreshStarted = false;

    try {
      const task = await Api.agent.get(state.currentProject.id, taskId);
      this.activeTask = task;
      state.activeTaskId = taskId;
      this.updateTaskView(container, state);

      // 连接 SSE 以恢复历史轨迹并继续接收实时事件。
      this.connectSse(container, state);
    } catch (err) {
      console.error('获取任务详情失败：', err);
    }
  },

  connectSse(container, state) {
    if (!this.activeTask) return;

    const liveDot = container.querySelector('#sse-live-dot');
    const liveLabel = container.querySelector('#sse-status-label');
    const consoleEl = container.querySelector('#event-log-console');

    if (liveDot) liveDot.style.background = 'var(--text-disabled)';
    if (liveLabel) liveLabel.textContent = 'SSE 连接中';

    this.streamClient = new AgentStreamClient(state.currentProject.id, this.activeTask.id);

    this.streamClient.onOpen(() => {
      if (liveDot) liveDot.style.background = 'var(--color-success)';
      if (liveLabel) liveLabel.textContent = 'SSE 已连接';
    });

    this.streamClient.onEvent((eventData) => {
      this.appendConsoleLog(consoleEl, eventData);

      // Update Task status if state changed
      if (this.activeTask) {
        this.activeTask.status = eventData.state;
      }

      // If payload contains plan, update plan
      if (eventData.eventType === 'PLAN_CREATED' || eventData.eventType === 'PLAN_REVISED') {
        if (eventData.payload && eventData.payload.plan) {
          this.activeTask.plan = eventData.payload.plan;
        }
      }

      // If confirmation required
      if (eventData.eventType === 'CONFIRMATION_REQUIRED') {
        if (eventData.payload) {
          this.activeTask.confirmation = eventData.payload;
        }
      }

      this.updateTaskView(container, state);

      const terminalStates = ['SUCCEEDED', 'FAILED', 'NEEDS_REVIEW', 'CANCELLED'];
      const terminalEvents = ['TASK_COMPLETED', 'TASK_CANCELLED', 'MANUAL_REVIEW_REQUIRED'];
      if (terminalStates.includes(eventData.state) && terminalEvents.includes(eventData.eventType)) {
        if (liveDot) liveDot.style.background = 'var(--color-success)';
        if (liveLabel) liveLabel.textContent = eventData.state === 'SUCCEEDED' ? '执行完成' : '执行已结束';
        void this.refreshTaskAfterTerminal(container, state);
      }
    });

    this.streamClient.onError(() => {
      if (this.activeTask && ['SUCCEEDED', 'FAILED', 'NEEDS_REVIEW', 'CANCELLED'].includes(this.activeTask.status)) {
        return;
      }
      if (liveDot) liveDot.style.background = 'var(--color-warning)';
      if (liveLabel) liveLabel.textContent = 'SSE 连接重试中';
    });

    this.streamClient.connect();
  },

  appendConsoleLog(consoleEl, eventData) {
    if (!consoleEl) return;
    consoleEl.querySelector('.event-log-empty')?.remove();
    const time = Utils.formatTime(eventData.createdAt);
    const line = document.createElement('div');
    line.style.borderBottom = '1px dashed rgba(255,255,255,0.05)';
    line.style.padding = '2px 0';

    let color = '#38bdf8';
    if (eventData.eventType.includes('FAILED')) color = '#f43f5e';
    else if (eventData.eventType.includes('COMPLETED') || eventData.eventType.includes('SUCCEEDED')) color = '#10b981';
    else if (eventData.eventType.includes('CONFIRMATION')) color = '#f59e0b';

    line.innerHTML = `
      <span style="color:var(--text-disabled)">[${time}]</span>
      <span style="color:${color}; font-weight:600;">${eventData.eventType}</span>
      <span style="color:var(--text-muted); font-size:11px;">(${eventData.state})</span>
      <span style="color:#cbd5e1; margin-left:4px;">${this.formatEventPayload(eventData)}</span>
    `;
    consoleEl.appendChild(line);
    consoleEl.scrollTop = consoleEl.scrollHeight;
  },

  async refreshTaskAfterTerminal(container, state) {
    if (this.terminalRefreshStarted || !this.activeTask) return;
    this.terminalRefreshStarted = true;

    try {
      const task = await Api.agent.get(state.currentProject.id, this.activeTask.id);
      this.activeTask = task;
      this.updateTaskView(container, state);
      await this.loadTaskHistory(container, state);
    } catch (err) {
      console.error('刷新任务终态失败：', err);
    }
  },

  formatEventPayload(event) {
    if (!event.payload) return '';
    if (typeof event.payload === 'string') return Utils.escapeHtml(event.payload);
    if (event.payload.objective) return Utils.escapeHtml(event.payload.objective);
    if (event.payload.toolName) return `工具: ${event.payload.toolName}`;
    if (event.payload.errorMessage) return `错误: ${Utils.escapeHtml(event.payload.errorMessage)}`;
    return JSON.stringify(event.payload).slice(0, 100);
  },

  updateTaskView(container, state) {
    const task = this.activeTask;
    if (!task) return;

    // Header info
    const idEl = container.querySelector('#wb-task-id');
    const statusEl = container.querySelector('#wb-task-status');
    const stepsEl = container.querySelector('#wb-task-steps');
    const goalEl = container.querySelector('#wb-task-goal');
    const cancelBtn = container.querySelector('#btn-cancel-task');
    const reportBtn = container.querySelector('#btn-view-report');

    if (idEl) idEl.textContent = `任务 #${task.id}`;
    if (statusEl) {
      statusEl.className = `status-badge status-${task.status}`;
      statusEl.textContent = task.status;
    }
    if (stepsEl) {
      const planCount = task.plan ? task.plan.length : 0;
      stepsEl.textContent = `当前步: ${task.currentStep || 0} / ${planCount} (工具调用: ${task.toolCallCount || 0})`;
    }
    if (goalEl) goalEl.textContent = task.goal;

    const isTerminal = ['SUCCEEDED', 'FAILED', 'NEEDS_REVIEW', 'CANCELLED'].includes(task.status);
    if (cancelBtn) cancelBtn.style.display = isTerminal ? 'none' : 'inline-flex';
    if (reportBtn) reportBtn.style.display = isTerminal ? 'inline-flex' : 'none';

    // Danger Confirmation Box
    const confirmBox = container.querySelector('#danger-confirm-box');
    const modifyBox = container.querySelector('#plan-modify-box');

    if (task.status === 'WAITING_CONFIRMATION' && task.confirmation) {
      confirmBox.style.display = 'block';
      modifyBox.style.display = 'block';

      const hashEl = container.querySelector('#confirm-plan-hash');
      if (hashEl) hashEl.textContent = task.confirmation.planHash || '-';

      const countdownEl = container.querySelector('#confirm-countdown');
      if (countdownEl && task.confirmation.expiresAt) {
        const exp = new Date(task.confirmation.expiresAt).toLocaleTimeString();
        countdownEl.textContent = `有效期至 ${exp}`;
      }
    } else {
      confirmBox.style.display = 'none';
      modifyBox.style.display = 'none';
    }

    // Render Steps Tree
    this.renderPlanSteps(container, task);
  },

  renderPlanSteps(container, task) {
    const tree = container.querySelector('#plan-steps-tree');
    const countEl = container.querySelector('#plan-steps-count');
    if (!tree) return;

    const steps = task.plan || [];
    if (countEl) countEl.textContent = `${steps.length} 步骤`;

    if (steps.length === 0) {
      tree.innerHTML = `
        <div style="padding: 32px; text-align: center; color: var(--text-muted); font-size: 13px;">
          ${task.status === 'PLANNING' ? 'Agent 正在进行 OpenAPI 候选检索与逻辑推理...' : '暂无执行计划'}
        </div>
      `;
      return;
    }

    tree.innerHTML = steps.map((step, idx) => {
      const stepIndex = step.index !== undefined ? step.index : idx + 1;
      const isCurrent = task.currentStep === stepIndex;
      const isPast = task.currentStep > stepIndex;
      const req = step.request || {};
      const method = (req.method || 'GET').toUpperCase();
      const dangerous = ['DELETE', 'PUT', 'PATCH'].includes(method);

      let nodeClass = '';
      if (isPast) nodeClass = 'success';
      else if (isCurrent) nodeClass = 'active';

      return `
        <div class="step-node ${nodeClass}">
          <div class="step-node-header">
            <div class="step-node-marker">${stepIndex}</div>
            <span class="method-badge method-${method}">${method}</span>
            <span class="code-font" style="font-weight:600; font-size:13px; color:var(--text-primary);">${Utils.escapeHtml(req.path || '-')}</span>
            ${dangerous ? `<span class="badge" style="background:var(--color-danger-bg); color:var(--color-danger); font-size:11px;">危险操作</span>` : ''}
          </div>
          <div class="step-node-card">
            <div style="font-weight: 500; font-size: 13px; margin-bottom: 6px;">
              ${Utils.escapeHtml(step.objective || req.name || 'API 调用步骤')}
            </div>

            <!-- Details toggle -->
            <details style="font-size: 12px; color: var(--text-secondary);">
              <summary style="cursor: pointer; color: var(--accent-light); margin-bottom: 4px;">查看请求与断言规则</summary>
              <div style="margin-top: 6px; display: flex; flex-direction: column; gap: 4px;">
                ${req.queryParams ? `<div><span style="color:var(--text-muted)">Query:</span> <code>${Utils.escapeHtml(JSON.stringify(req.queryParams))}</code></div>` : ''}
                ${req.headers ? `<div><span style="color:var(--text-muted)">Headers:</span> <code>${Utils.escapeHtml(JSON.stringify(req.headers))}</code></div>` : ''}
                ${req.body ? `<div><span style="color:var(--text-muted)">Body:</span> <pre class="json-viewer" style="margin-top:2px;">${Utils.highlightJson(req.body)}</pre></div>` : ''}
                ${req.extractors && req.extractors.length > 0 ? `<div><span style="color:var(--text-muted)">变量提取:</span> <code>${Utils.escapeHtml(JSON.stringify(req.extractors))}</code></div>` : ''}
                ${req.assertions && req.assertions.length > 0 ? `<div><span style="color:var(--text-muted)">断言预期:</span> <code>${Utils.escapeHtml(JSON.stringify(req.assertions))}</code></div>` : ''}
              </div>
            </details>
          </div>
        </div>
      `;
    }).join('');
  },

  async handleConfirmation(approved, container, state) {
    if (!this.activeTask || !this.activeTask.confirmation) return;

    const note = container.querySelector('#confirm-decision-note').value.trim();
    const planHash = this.activeTask.confirmation.planHash;

    const approveBtn = container.querySelector('#btn-approve-confirm');
    const rejectBtn = container.querySelector('#btn-reject-confirm');
    approveBtn.disabled = true;
    rejectBtn.disabled = true;

    try {
      await Api.agent.confirm(state.currentProject.id, this.activeTask.id, {
        approved,
        note,
        planHash
      });
      Utils.showToast(approved ? '已批准操作，Agent 继续执行' : '已拒绝操作，任务终止', approved ? 'success' : 'info');
      await this.selectTask(this.activeTask.id, container, state);
    } catch (err) {
      Utils.showToast(`确认提交失败: ${err.message}`, 'error');
      approveBtn.disabled = false;
      rejectBtn.disabled = false;
    }
  },

  async handleModifyPlan(container, state) {
    if (!this.activeTask) return;
    const input = container.querySelector('#input-modify-instruction');
    const instruction = input.value.trim();
    if (!instruction) {
      Utils.showToast('请输入修改指令', 'warning');
      return;
    }

    const btn = container.querySelector('#btn-submit-modify');
    btn.disabled = true;
    btn.innerHTML = `<span class="spinner"></span> 正在调整...`;

    try {
      await Api.agent.modify(state.currentProject.id, this.activeTask.id, { instruction });
      Utils.showToast('计划调整指令已提交，模型正在重构执行计划', 'success');
      input.value = '';
    } catch (err) {
      Utils.showToast(`调整失败: ${err.message}`, 'error');
    } finally {
      btn.disabled = false;
      btn.innerHTML = `${Utils.getIcon('refresh-cw', 13)} 调整计划`;
    }
  },

  async loadTaskHistory(container, state) {
    if (!state.currentProject) return;
    const listEl = container.querySelector('#task-history-list');
    try {
      const tasks = await Api.agent.list(state.currentProject.id, 20);
      if (tasks.length === 0) {
        listEl.innerHTML = `
          <div style="padding: 24px; text-align: center; color: var(--text-muted); font-size: 12px;">
            暂无历史任务
          </div>
        `;
        return;
      }

      listEl.innerHTML = tasks.map(t => {
        const isSelected = this.activeTask && this.activeTask.id === t.id;
        return `
          <div class="task-history-item" data-id="${t.id}" style="padding: 10px 14px; border-bottom: 1px solid var(--border-subtle); cursor: pointer; background: ${isSelected ? 'var(--bg-surface-hover)' : 'transparent'}; transition: background var(--transition-fast);">
            <div class="flex justify-between items-center" style="margin-bottom: 2px;">
              <span style="font-family: var(--font-mono); font-size: 11px; color: var(--text-muted);">#${t.id}</span>
              <span class="status-badge status-${t.status}" style="font-size: 10px; padding: 1px 6px;">${t.status}</span>
            </div>
            <div class="truncate" style="font-size: 12px; font-weight: 500; color: var(--text-primary);" title="${Utils.escapeHtml(t.goal)}">
              ${Utils.escapeHtml(t.goal)}
            </div>
            <div style="font-size: 11px; color: var(--text-muted); margin-top: 2px;">
              ${Utils.formatTime(t.createdAt)} · ${t.currentStep || 0} 步
            </div>
          </div>
        `;
      }).join('');

      listEl.querySelectorAll('.task-history-item').forEach(el => {
        el.onclick = () => {
          const tid = el.getAttribute('data-id');
          this.selectTask(tid, container, state);
        };
      });

    } catch (err) {
      console.error('加载任务历史失败：', err);
    }
  }
};
