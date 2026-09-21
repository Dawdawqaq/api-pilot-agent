/**
 * Projects & Environments Management View
 */
import { Api } from '../api.js';
import { Utils } from '../utils.js';

export const ProjectsView = {
  async render(container, state, navigate, reloadGlobalProjects) {
    container.innerHTML = `
      <div class="flex flex-col gap-6" style="max-width: 1200px; margin: 0 auto;">
        <!-- Header -->
        <div class="flex justify-between items-center flex-wrap gap-4">
          <div>
            <h2>项目与执行环境配置</h2>
            <p style="font-size: 13px;">管理被测目标系统、服务基地址与 HTTP 权限策略</p>
          </div>
          <button class="btn btn-primary" id="btn-create-project">
            ${Utils.getIcon('plus', 16)} 创建新项目
          </button>
        </div>

        <!-- Project Cards Grid -->
        <div id="projects-grid" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: var(--space-4);">
          <div style="padding: 40px; text-align: center; color: var(--text-muted);">加载中...</div>
        </div>

        <!-- Environments Section for Active Project -->
        <div class="card" id="environments-card">
          <div class="card-header">
            <div class="flex items-center gap-2">
              ${Utils.getIcon('layers', 18)}
              <h3 style="font-size: 15px; font-weight:600;">
                <span id="env-project-title">当前项目</span> 的执行环境
              </h3>
            </div>
            <button class="btn btn-primary btn-sm" id="btn-create-env">
              ${Utils.getIcon('plus', 14)} 添加环境
            </button>
          </div>
          <div class="card-body" style="padding: 0;">
            <div style="overflow-x: auto;">
              <table style="width: 100%; border-collapse: collapse; text-align: left; font-size: 13px;">
                <thead>
                  <tr style="border-bottom: 1px solid var(--border-subtle); color: var(--text-muted); font-size: 12px;">
                    <th style="padding: 12px 16px;">环境名称</th>
                    <th style="padding: 12px 16px;">服务基地址 (Base URL)</th>
                    <th style="padding: 12px 16px;">允许 HTTP 方法</th>
                    <th style="padding: 12px 16px;">网络边界</th>
                    <th style="padding: 12px 16px; text-align: right;">操作</th>
                  </tr>
                </thead>
                <tbody id="environments-tbody">
                  <tr>
                    <td colspan="5" style="padding: 24px; text-align: center; color: var(--text-muted);">
                      选择项目后查看其运行环境
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <!-- Create/Edit Project Modal -->
      <div class="modal-backdrop" id="modal-project">
        <div class="modal-content">
          <div class="modal-header">
            <h3 id="modal-project-title">创建新项目</h3>
            <button class="btn btn-ghost btn-icon btn-sm" id="btn-close-project-modal">${Utils.getIcon('x', 16)}</button>
          </div>
          <div class="modal-body">
            <form id="form-project">
              <input type="hidden" id="project-edit-id" value="">
              <div class="form-group">
                <label class="form-label" for="proj-code">项目编码 <span style="color:var(--color-danger)">*</span></label>
                <input class="form-input code-font" id="proj-code" required maxlength="64" pattern="[a-z][a-z0-9-]*" placeholder="例如：sample-order-api">
                <span class="form-helper">以小写字母开头，只能包含小写字母、数字和短横线；创建后不可修改</span>
              </div>
              <div class="form-group">
                <label class="form-label" for="proj-name">项目名称 <span style="color:var(--color-danger)">*</span></label>
                <input class="form-input" id="proj-name" required placeholder="例如：订单服务 API">
              </div>
              <div class="form-group">
                <label class="form-label" for="proj-desc">项目描述</label>
                <textarea class="form-textarea" id="proj-desc" placeholder="简要说明系统背景与测试目标..."></textarea>
              </div>
            </form>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" id="btn-cancel-project">取消</button>
            <button class="btn btn-primary" id="btn-save-project">保存</button>
          </div>
        </div>
      </div>

      <!-- Create/Edit Environment Modal -->
      <div class="modal-backdrop" id="modal-env">
        <div class="modal-content">
          <div class="modal-header">
            <h3 id="modal-env-title">配置环境</h3>
            <button class="btn btn-ghost btn-icon btn-sm" id="btn-close-env-modal">${Utils.getIcon('x', 16)}</button>
          </div>
          <div class="modal-body">
            <form id="form-env">
              <input type="hidden" id="env-edit-id" value="">
              <div class="form-group">
                <label class="form-label" for="env-name">环境名称 <span style="color:var(--color-danger)">*</span></label>
                <input class="form-input" id="env-name" required placeholder="例如：Local Dev / Docker Test">
              </div>
              <div class="form-group">
                <label class="form-label" for="env-url">基地址 (Base URL) <span style="color:var(--color-danger)">*</span></label>
                <input class="form-input code-font" id="env-url" required placeholder="http://localhost:8080">
                <span class="form-helper">所有 OpenAPI 相对路径将自动以此基地址发送请求</span>
              </div>
              <div class="form-group">
                <label class="form-label">允许的 HTTP 方法 (安全白名单)</label>
                <div class="flex gap-4 flex-wrap" style="padding: 6px 0;">
                  <label class="flex items-center gap-1" style="font-size:13px;"><input type="checkbox" name="env-method" value="GET" checked> GET</label>
                  <label class="flex items-center gap-1" style="font-size:13px;"><input type="checkbox" name="env-method" value="POST" checked> POST</label>
                  <label class="flex items-center gap-1" style="font-size:13px;"><input type="checkbox" name="env-method" value="PUT"> PUT</label>
                  <label class="flex items-center gap-1" style="font-size:13px;"><input type="checkbox" name="env-method" value="PATCH"> PATCH</label>
                  <label class="flex items-center gap-1" style="font-size:13px;"><input type="checkbox" name="env-method" value="DELETE"> DELETE</label>
                </div>
              </div>
              <div class="form-group">
                <label class="flex items-center gap-2" style="font-size: 13px; cursor: pointer;">
                  <input type="checkbox" id="env-allow-private"> 允许访问私网或本机环回地址
                </label>
                <span class="form-helper">仅在明确测试本地或内网服务时开启</span>
              </div>
              <div class="form-group">
                <label class="flex items-center gap-2" style="font-size: 13px; cursor: pointer;">
                  <input type="checkbox" id="env-default"> 设为当前项目的默认执行环境
                </label>
              </div>
            </form>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" id="btn-cancel-env">取消</button>
            <button class="btn btn-primary" id="btn-save-env">保存</button>
          </div>
        </div>
      </div>
    `;

    // Modal controls
    const projectModal = container.querySelector('#modal-project');
    const envModal = container.querySelector('#modal-env');

    container.querySelector('#btn-create-project').onclick = () => {
      container.querySelector('#modal-project-title').textContent = '创建新项目';
      container.querySelector('#project-edit-id').value = '';
      container.querySelector('#proj-code').value = '';
      container.querySelector('#proj-code').readOnly = false;
      container.querySelector('#proj-name').value = '';
      container.querySelector('#proj-desc').value = '';
      projectModal.classList.add('active');
    };

    container.querySelector('#btn-close-project-modal').onclick = () => projectModal.classList.remove('active');
    container.querySelector('#btn-cancel-project').onclick = () => projectModal.classList.remove('active');

    container.querySelector('#btn-save-project').onclick = async () => {
      const id = container.querySelector('#project-edit-id').value;
      const code = container.querySelector('#proj-code').value.trim();
      const name = container.querySelector('#proj-name').value.trim();
      const description = container.querySelector('#proj-desc').value.trim();

      if (!code || !name) {
        Utils.showToast('项目编码和项目名称不能为空', 'warning');
        return;
      }
      if (!/^[a-z][a-z0-9-]*$/.test(code)) {
        Utils.showToast('项目编码格式不正确', 'warning');
        return;
      }

      try {
        if (id) {
          const existing = state.projects.find(project => project.id === id);
          await Api.projects.update(id, {
            name,
            description,
            status: existing?.status || 'ACTIVE'
          });
          Utils.showToast('项目已更新', 'success');
        } else {
          const newProj = await Api.projects.create({ code, name, description });
          Utils.showToast('项目创建成功', 'success');
          state.currentProject = newProj;
        }
        projectModal.classList.remove('active');
        if (reloadGlobalProjects) await reloadGlobalProjects();
        await this.loadData(container, state, navigate);
      } catch (err) {
        Utils.showToast(err.message, 'error');
      }
    };

    // Env Modal controls
    container.querySelector('#btn-create-env').onclick = () => {
      if (!state.currentProject) {
        Utils.showToast('请先选择一个项目', 'warning');
        return;
      }
      container.querySelector('#modal-env-title').textContent = '添加执行环境';
      container.querySelector('#env-edit-id').value = '';
      container.querySelector('#env-name').value = '';
      container.querySelector('#env-url').value = 'http://localhost:8080';
      container.querySelector('#env-allow-private').checked = false;
      container.querySelector('#env-default').checked = true;
      container.querySelectorAll('input[name="env-method"]').forEach(cb => {
        cb.checked = ['GET', 'POST'].includes(cb.value);
      });
      envModal.classList.add('active');
    };

    container.querySelector('#btn-close-env-modal').onclick = () => envModal.classList.remove('active');
    container.querySelector('#btn-cancel-env').onclick = () => envModal.classList.remove('active');

    container.querySelector('#btn-save-env').onclick = async () => {
      if (!state.currentProject) return;
      const id = container.querySelector('#env-edit-id').value;
      const name = container.querySelector('#env-name').value.trim();
      const baseUrl = container.querySelector('#env-url').value.trim();
      const allowPrivateNetwork = container.querySelector('#env-allow-private').checked;
      const defaultEnvironment = container.querySelector('#env-default').checked;
      const methods = Array.from(container.querySelectorAll('input[name="env-method"]:checked')).map(cb => cb.value);

      if (!name || !baseUrl) {
        Utils.showToast('环境名称和基地址不能为空', 'warning');
        return;
      }

      try {
        const payload = {
          name,
          baseUrl,
          allowedMethods: methods.join(','),
          allowPrivateNetwork,
          defaultEnvironment
        };
        if (id) {
          await Api.environments.update(state.currentProject.id, id, payload);
          Utils.showToast('环境已更新', 'success');
        } else {
          await Api.environments.create(state.currentProject.id, payload);
          Utils.showToast('环境添加成功', 'success');
        }
        envModal.classList.remove('active');
        if (reloadGlobalProjects) await reloadGlobalProjects();
        await this.loadEnvironments(container, state);
      } catch (err) {
        Utils.showToast(err.message, 'error');
      }
    };

    await this.loadData(container, state, navigate);
  },

  async loadData(container, state, navigate) {
    try {
      const projects = await Api.projects.list();
      state.projects = projects;

      const grid = container.querySelector('#projects-grid');
      if (!grid) return;

      if (projects.length === 0) {
        grid.innerHTML = `
          <div style="grid-column: 1/-1; padding: 48px; text-align: center; background: var(--bg-surface); border: 1px dashed var(--border-default); border-radius: var(--radius-lg);">
            <p style="margin-bottom: 12px; color: var(--text-muted);">暂无测试项目，请先创建第一个被测系统</p>
            <button class="btn btn-primary" onclick="document.getElementById('btn-create-project').click()">
              ${Utils.getIcon('plus', 16)} 立即创建
            </button>
          </div>
        `;
        return;
      }

      grid.innerHTML = projects.map(p => {
        const isCurrent = state.currentProject && state.currentProject.id === p.id;
        return `
          <div class="card" style="border-color: ${isCurrent ? 'var(--accent)' : 'var(--border-default)'}; cursor: pointer;" data-proj-id="${p.id}">
            <div class="card-header">
              <div class="flex items-center gap-2">
                <span class="badge" style="background: ${isCurrent ? 'var(--accent-surface)' : 'rgba(255,255,255,0.05)'}; color: ${isCurrent ? 'var(--accent-light)' : 'var(--text-secondary)'};">
                  #${p.id}
                </span>
                <h4 style="font-size: 15px; font-weight:600;">${Utils.escapeHtml(p.name)}</h4>
              </div>
              ${isCurrent ? `<span class="badge" style="background: var(--color-success-bg); color: var(--color-success);">当前选中</span>` : ''}
            </div>
            <div class="card-body flex flex-col gap-2">
              <p style="font-size: 13px; height: 38px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;">
                ${Utils.escapeHtml(p.description || '无详细描述')}
              </p>
              <div class="flex justify-between items-center" style="font-size: 12px; color: var(--text-muted); margin-top: 8px;">
                <span class="code-font">${Utils.escapeHtml(p.code)}</span>
                <span>创建: ${Utils.formatDateTime(p.createdAt)}</span>
              </div>
            </div>
            <div class="card-footer">
              <button class="btn btn-secondary btn-sm action-select-proj" data-proj-id="${p.id}">
                ${isCurrent ? '已激活' : '设为当前'}
              </button>
              <div class="flex gap-1">
                <button class="btn btn-ghost btn-sm action-edit-proj" data-proj-id="${p.id}" title="编辑">
                  ${Utils.getIcon('edit', 14)}
                </button>
                <button class="btn btn-ghost btn-sm action-del-proj" style="color: var(--color-danger);" data-proj-id="${p.id}" title="删除">
                  ${Utils.getIcon('trash', 14)}
                </button>
              </div>
            </div>
          </div>
        `;
      }).join('');

      // Bind Project Card actions
      grid.querySelectorAll('.action-select-proj').forEach(btn => {
        btn.onclick = async (e) => {
          e.stopPropagation();
          const pid = btn.getAttribute('data-proj-id');
          state.currentProject = state.projects.find(p => p.id === pid);
          // Sync header
          const projSelect = document.getElementById('header-project-select');
          if (projSelect) projSelect.value = pid;
          await this.loadData(container, state, navigate);
        };
      });

      grid.querySelectorAll('.action-edit-proj').forEach(btn => {
        btn.onclick = (e) => {
          e.stopPropagation();
          const pid = btn.getAttribute('data-proj-id');
          const p = state.projects.find(x => x.id === pid);
          if (!p) return;
          container.querySelector('#modal-project-title').textContent = '编辑项目';
          container.querySelector('#project-edit-id').value = p.id;
          container.querySelector('#proj-code').value = p.code;
          container.querySelector('#proj-code').readOnly = true;
          container.querySelector('#proj-name').value = p.name;
          container.querySelector('#proj-desc').value = p.description || '';
          container.querySelector('#modal-project').classList.add('active');
        };
      });

      grid.querySelectorAll('.action-del-proj').forEach(btn => {
        btn.onclick = async (e) => {
          e.stopPropagation();
          const pid = btn.getAttribute('data-proj-id');
          if (!confirm(`确定要删除项目 #${pid} 及其所有关联数据吗？`)) return;
          try {
            await Api.projects.delete(pid);
            Utils.showToast('项目已删除', 'success');
            if (state.currentProject && state.currentProject.id === pid) {
              state.currentProject = null;
            }
            await this.loadData(container, state, navigate);
          } catch (err) {
            Utils.showToast(err.message, 'error');
          }
        };
      });

      await this.loadEnvironments(container, state);
    } catch (err) {
      console.error('Failed to load projects:', err);
    }
  },

  async loadEnvironments(container, state) {
    const titleEl = container.querySelector('#env-project-title');
    const tbody = container.querySelector('#environments-tbody');
    if (!tbody) return;

    if (!state.currentProject) {
      if (titleEl) titleEl.textContent = '当前项目';
      tbody.innerHTML = `
        <tr><td colspan="5" style="padding: 24px; text-align: center; color: var(--text-muted);">请先选择一个项目</td></tr>
      `;
      return;
    }

    if (titleEl) titleEl.textContent = state.currentProject.name;

    try {
      const envs = await Api.environments.list(state.currentProject.id);
      state.environments = envs;

      if (envs.length === 0) {
        tbody.innerHTML = `
          <tr><td colspan="5" style="padding: 24px; text-align: center; color: var(--text-muted);">暂未配置执行环境，请点击“添加环境”</td></tr>
        `;
        return;
      }

      tbody.innerHTML = envs.map(env => {
        const methods = (env.allowedMethods || 'GET,POST')
          .split(',')
          .map(method => method.trim())
          .filter(Boolean);
        const methodsHtml = methods.map(m =>
          `<span class="method-badge method-${m}">${m}</span>`
        ).join(' ');

        return `
          <tr style="border-bottom: 1px solid var(--border-subtle);">
            <td style="padding: 12px 16px; font-weight: 600;">
              ${Utils.escapeHtml(env.name)}
              ${env.defaultEnvironment ? `<span class="badge" style="background:var(--color-success-bg); color:var(--color-success); margin-left:6px;">默认</span>` : ''}
            </td>
            <td style="padding: 12px 16px; font-family: var(--font-mono); font-size: 13px; color: var(--accent-light);">
              ${Utils.escapeHtml(env.baseUrl)}
            </td>
            <td style="padding: 12px 16px;">
              <div class="flex gap-1 flex-wrap">${methodsHtml}</div>
            </td>
            <td style="padding: 12px 16px; font-size: 12px; color: var(--text-muted);">
              ${env.allowPrivateNetwork ? '允许私网 / 环回' : '仅允许公网'}
            </td>
            <td style="padding: 12px 16px; text-align: right;">
              <button class="btn btn-ghost btn-sm action-del-env" style="color:var(--color-danger)" data-env-id="${env.id}">
                ${Utils.getIcon('trash', 14)}
              </button>
            </td>
          </tr>
        `;
      }).join('');

      tbody.querySelectorAll('.action-del-env').forEach(btn => {
        btn.onclick = async () => {
          const eid = btn.getAttribute('data-env-id');
          if (!confirm('确定删除该执行环境？')) return;
          try {
            await Api.environments.delete(state.currentProject.id, eid);
            Utils.showToast('环境已删除', 'success');
            await this.loadEnvironments(container, state);
          } catch (err) {
            Utils.showToast(err.message, 'error');
          }
        };
      });

    } catch (err) {
      console.error('Failed to load environments:', err);
    }
  }
};
