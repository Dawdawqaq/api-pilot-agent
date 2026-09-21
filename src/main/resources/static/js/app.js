/**
 * DocHelper Application Entry & State Manager
 * Modern ES2024 module, zero-build, zero-dependency SPA
 */
import { Api } from './api.js';
import { Utils } from './utils.js';

// Views
import { OverviewView } from './views/overview.js';
import { ProjectsView } from './views/projects.js';
import { OpenApiView } from './views/openapi.js';
import { WorkbenchView } from './views/workbench.js';
import { ReportsView } from './views/reports.js';
import { ContractView } from './views/contract.js';
import { GovernanceView } from './views/governance.js';
import { KnowledgeView } from './views/knowledge.js';

class Application {
  constructor() {
    this.state = {
      projects: [],
      currentProject: null,
      environments: [],
      currentEnvironment: null,
      systemOverview: null,
      activeView: 'overview',
      theme: localStorage.getItem('dochelper_theme') || 'dark',
      activeTaskId: null,
      agentPrefillGoal: null
    };

    this.views = {
      overview: OverviewView,
      projects: ProjectsView,
      openapi: OpenApiView,
      workbench: WorkbenchView,
      reports: ReportsView,
      contract: ContractView,
      governance: GovernanceView,
      knowledge: KnowledgeView
    };
  }

  async init() {
    this.applyTheme(this.state.theme);
    this.bindGlobalEvents();

    try {
      // 1. Fetch System Overview
      this.state.systemOverview = await Api.system.getOverview();
      this.renderSystemPill();

      // 2. Fetch Projects
      await this.reloadProjects();

      // 3. Handle initial route from URL Hash
      const initialHash = window.location.hash.replace('#', '') || 'overview';
      this.navigate(initialHash);
    } catch (err) {
      console.error('App initialization error:', err);
      Utils.showToast(`初始化失败: ${err.message}`, 'error');
    }
  }

  applyTheme(theme) {
    this.state.theme = theme;
    localStorage.setItem('dochelper_theme', theme);
    document.documentElement.setAttribute('data-theme', theme);
    const themeBtn = document.getElementById('btn-theme-toggle');
    if (themeBtn) {
      themeBtn.innerHTML = theme === 'dark' ? Utils.getIcon('activity', 16) : Utils.getIcon('layers', 16);
      themeBtn.title = theme === 'dark' ? '切换为亮色模式' : '切换为暗色模式';
    }
  }

  toggleTheme() {
    const next = this.state.theme === 'dark' ? 'light' : 'dark';
    this.applyTheme(next);
  }

  renderSystemPill() {
    const modelPill = document.getElementById('header-model-pill');
    if (!modelPill) return;

    const overview = this.state.systemOverview;
    if (!overview) return;

    const isLightweight = !overview.knowledgeEnabled;
    modelPill.innerHTML = `
      <span class="badge-dot" style="background:var(--color-success)"></span>
      <span>${Utils.escapeHtml(overview.chatModel || '默认模型')}</span>
      <span class="mode-tag">${isLightweight ? '轻量模式' : '知识库模式'}</span>
    `;
  }

  async reloadProjects() {
    try {
      const list = await Api.projects.list();
      this.state.projects = list;

      const savedProjectId = localStorage.getItem('dochelper_selected_project_id');
      if (savedProjectId) {
        this.state.currentProject = list.find(p => String(p.id) === savedProjectId) || list[0] || null;
      } else {
        this.state.currentProject = list[0] || null;
      }

      this.renderHeaderProjectSelect();
      if (this.state.currentProject) {
        await this.reloadEnvironments();
      }
    } catch (err) {
      console.error('Failed to reload projects:', err);
    }
  }

  renderHeaderProjectSelect() {
    const select = document.getElementById('header-project-select');
    if (!select) return;

    select.innerHTML = '';
    if (this.state.projects.length === 0) {
      select.innerHTML = '<option value="">(暂无项目，请先创建)</option>';
      return;
    }

    this.state.projects.forEach(p => {
      const opt = document.createElement('option');
      opt.value = p.id;
      opt.textContent = `${p.name} (#${p.id})`;
      if (this.state.currentProject && this.state.currentProject.id === p.id) {
        opt.selected = true;
      }
      select.appendChild(opt);
    });
  }

  async reloadEnvironments() {
    if (!this.state.currentProject) return;
    try {
      const envs = await Api.environments.list(this.state.currentProject.id);
      this.state.environments = envs;

      // Select default env or first env
      this.state.currentEnvironment = envs.find(e => e.defaultEnvironment) || envs[0] || null;
      this.renderHeaderEnvSelect();
    } catch (err) {
      console.error('Failed to reload environments:', err);
    }
  }

  renderHeaderEnvSelect() {
    const select = document.getElementById('header-env-select');
    if (!select) return;

    select.innerHTML = '';
    if (this.state.environments.length === 0) {
      select.innerHTML = '<option value="">(未配环境)</option>';
      return;
    }

    this.state.environments.forEach(e => {
      const opt = document.createElement('option');
      opt.value = e.id;
      opt.textContent = `${e.name} (${e.baseUrl})`;
      if (this.state.currentEnvironment && this.state.currentEnvironment.id === e.id) {
        opt.selected = true;
      }
      select.appendChild(opt);
    });
  }

  bindGlobalEvents() {
    // Project switcher in header
    const projSelect = document.getElementById('header-project-select');
    if (projSelect) {
      projSelect.onchange = async () => {
        const pid = projSelect.value;
        this.state.currentProject = this.state.projects.find(p => p.id === pid) || null;
        if (this.state.currentProject) {
          localStorage.setItem('dochelper_selected_project_id', String(pid));
          await this.reloadEnvironments();
        }
        // Re-render current view
        this.navigate(this.state.activeView);
      };
    }

    // Env switcher in header
    const envSelect = document.getElementById('header-env-select');
    if (envSelect) {
      envSelect.onchange = () => {
        const eid = envSelect.value;
        this.state.currentEnvironment = this.state.environments.find(e => e.id === eid) || null;
      };
    }

    // Theme toggle
    const themeBtn = document.getElementById('btn-theme-toggle');
    if (themeBtn) {
      themeBtn.onclick = () => this.toggleTheme();
    }

    // Sidebar navigation items
    document.querySelectorAll('.nav-item[data-view]').forEach(item => {
      item.onclick = () => {
        const targetView = item.getAttribute('data-view');
        this.navigate(targetView);
      };
    });

    // Handle browser back/forward
    window.onhashchange = () => {
      const hash = window.location.hash.replace('#', '') || 'overview';
      if (hash !== this.state.activeView) {
        this.navigate(hash);
      }
    };
  }

  navigate(viewName) {
    const viewHandler = this.views[viewName] || this.views.overview;
    const previousView = this.views[this.state.activeView];
    if (previousView && previousView !== viewHandler && typeof previousView.destroy === 'function') {
      previousView.destroy();
    }
    this.state.activeView = viewName;
    window.location.hash = `#${viewName}`;

    // Update active nav-item
    document.querySelectorAll('.nav-item').forEach(el => {
      if (el.getAttribute('data-view') === viewName) {
        el.classList.add('active');
      } else {
        el.classList.remove('active');
      }
    });

    // Render View
    const mainContainer = document.getElementById('main-content');
    if (mainContainer) {
      mainContainer.innerHTML = '';
      viewHandler.render(
        mainContainer,
        this.state,
        (targetView) => this.navigate(targetView),
        () => this.reloadProjects()
      );
    }
  }
}

// Bootstrap Application
document.addEventListener('DOMContentLoaded', () => {
  const app = new Application();
  app.init();
  window.__DocHelperApp = app;
});
