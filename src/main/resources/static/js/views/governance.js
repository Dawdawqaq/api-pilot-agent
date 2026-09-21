/**
 * Project Model Governance Policy View
 */
import { Api } from '../api.js';
import { Utils } from '../utils.js';

export const GovernanceView = {
  async render(container, state, navigate) {
    container.innerHTML = `
      <div class="flex flex-col gap-6" style="max-width: 900px; margin: 0 auto;">
        <div>
          <h2>模型数据出站与安全治理策略</h2>
          <p style="font-size: 13px;">管控大模型交互过程中的敏感数据出站边界、候选 Prompt 预算与供应商白名单</p>
        </div>

        <div class="card">
          <div class="card-header">
            <div class="flex items-center gap-2">
              ${Utils.getIcon('shield', 18)}
              <h3 style="font-size: 15px; font-weight:600;">项目数据出站约束规则</h3>
            </div>
            <span class="badge" style="background:var(--color-success-bg); color:var(--color-success);">本地规则强制拦截</span>
          </div>
          <div class="card-body">
            <form id="form-model-policy" class="flex flex-col gap-4">
              <div class="form-group">
                <label class="flex items-center gap-2" style="font-size: 13px; cursor: pointer;">
                  <input type="checkbox" id="policy-external-model">
                  允许调用外部模型
                </label>
              </div>
              <div class="form-group">
                <label class="form-label" for="policy-provider">允许调用的模型供应商</label>
                <input class="form-input" id="policy-provider" maxlength="64" placeholder="ANY 或 deepseek">
                <span class="form-helper">填写 ANY 表示允许当前配置的模型供应商</span>
              </div>

              <div style="display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-4);">
                <div class="form-group">
                  <label class="form-label" for="policy-topk">接口候选最大 Top-K (候选接口截取限制)</label>
                  <input class="form-input" type="number" id="policy-topk" min="1" max="50" value="12">
                  <span class="form-helper">限制发送给大模型的 OpenAPI 候选接口数量，防止 Prompt 溢出</span>
                </div>

                <div class="form-group">
                  <label class="form-label" for="policy-budget">Prompt 最大字符预算上限</label>
                  <input class="form-input" type="number" id="policy-budget" min="2000" max="200000" value="40000">
                  <span class="form-helper">严格约束每次规划时允许发送的上下文字符数</span>
                </div>
              </div>

              <div class="flex flex-col gap-2" style="background: rgba(0,0,0,0.2); padding: var(--space-3); border-radius: var(--radius-md);">
                <label class="flex items-center gap-2" style="font-size: 13px; cursor: pointer;">
                  <input type="checkbox" id="policy-doc-outbound">
                  允许业务知识文档文本发送给外部模型
                </label>
                <label class="flex items-center gap-2" style="font-size: 13px; cursor: pointer;">
                  <input type="checkbox" id="policy-schema-outbound" checked>
                  允许接口路径与字段 Schema 发送给大模型进行规划
                </label>
              </div>

              <div class="flex justify-end gap-2" style="margin-top: 8px;">
                <button type="submit" class="btn btn-primary" id="btn-save-policy">
                  ${Utils.getIcon('check-circle', 15)} 保存治理策略
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    `;

    const form = container.querySelector('#form-model-policy');
    form.onsubmit = async (e) => {
      e.preventDefault();
      if (!state.currentProject) return;

      const externalModelAllowed = container.querySelector('#policy-external-model').checked;
      const allowedProvider = container.querySelector('#policy-provider').value.trim();
      const endpointTopK = parseInt(container.querySelector('#policy-topk').value, 10) || 12;
      const promptCharacterBudget = parseInt(container.querySelector('#policy-budget').value, 10) || 40000;
      const allowDocumentContent = container.querySelector('#policy-doc-outbound').checked;
      const allowSchemaContent = container.querySelector('#policy-schema-outbound').checked;

      if (!allowedProvider) {
        Utils.showToast('模型供应商不能为空', 'warning');
        return;
      }

      try {
        await Api.governance.update(state.currentProject.id, {
          externalModelAllowed,
          allowedProvider,
          allowDocumentContent,
          allowSchemaContent,
          promptCharacterBudget,
          endpointTopK
        });
        Utils.showToast('模型治理策略已更新', 'success');
      } catch (err) {
        Utils.showToast(`保存失败: ${err.message}`, 'error');
      }
    };

    await this.loadPolicy(container, state);
  },

  async loadPolicy(container, state) {
    if (!state.currentProject) return;
    try {
      const policy = await Api.governance.get(state.currentProject.id);
      if (!policy) return;

      container.querySelector('#policy-external-model').checked = !!policy.externalModelAllowed;
      container.querySelector('#policy-provider').value = policy.allowedProvider || 'ANY';
      container.querySelector('#policy-topk').value = policy.endpointTopK || 12;
      container.querySelector('#policy-budget').value = policy.promptCharacterBudget || 40000;
      container.querySelector('#policy-doc-outbound').checked = !!policy.allowDocumentContent;
      container.querySelector('#policy-schema-outbound').checked = policy.allowSchemaContent !== false;
    } catch (err) {
      console.error('Failed to load governance policy:', err);
    }
  }
};
