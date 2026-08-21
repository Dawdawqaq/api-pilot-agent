const state = {
    projects: [],
    currentProjectId: localStorage.getItem("apipilot.projectId") || "",
    environments: [],
    documents: [],
    openApiImports: [],
    endpoints: [],
    tasks: [],
    reports: [],
    currentTaskId: "",
    currentReportId: "",
    currentReport: null,
    eventSource: null,
    lastSequence: 0
};

const titles = {
    overview: "运行概览",
    workspace: "项目与知识",
    agent: "Agent 实验台",
    reports: "测试报告"
};

document.addEventListener("DOMContentLoaded", initialize);

async function initialize() {
    bindNavigation();
    bindForms();
    bindActions();
    await checkSystem();
    await loadProjects();
}

function bindNavigation() {
    document.querySelectorAll("[data-view-target]").forEach(button => {
        button.addEventListener("click", () => switchView(button.dataset.viewTarget));
    });
    document.querySelectorAll("[data-jump]").forEach(button => {
        button.addEventListener("click", () => switchView(button.dataset.jump));
    });
}

function switchView(name) {
    document.querySelectorAll(".view").forEach(view => {
        view.classList.toggle("active", view.dataset.view === name);
    });
    document.querySelectorAll(".nav-item").forEach(button => {
        button.classList.toggle("active", button.dataset.viewTarget === name);
    });
    document.getElementById("page-title").textContent = titles[name] || "ApiPilot";
    if (name === "reports") {
        loadReports();
    }
    if (name === "agent") {
        loadTasks();
    }
}

function bindForms() {
    document.getElementById("workspace-setup-form").addEventListener("submit", createWorkspace);
    document.getElementById("task-form").addEventListener("submit", createTask);
}

function bindActions() {
    document.getElementById("project-select").addEventListener("change", event => {
        selectProject(event.target.value);
    });
    document.getElementById("refresh-button").addEventListener("click", refreshCurrentProject);
    document.getElementById("delete-project-button").addEventListener("click", deleteCurrentProject);
    document.getElementById("reload-reports").addEventListener("click", loadReports);
    document.getElementById("toggle-agent-assets").addEventListener("click", toggleAgentAssets);
    document.getElementById("toggle-trace").addEventListener("click", toggleTracePanel);
    document.getElementById("modify-plan-toggle").addEventListener("click", toggleModifyDrawer);
    document.getElementById("close-modify-drawer").addEventListener("click", closeModifyDrawer);
    document.getElementById("plan-modify-form").addEventListener("submit", submitPlanModification);
    document.querySelectorAll(".chip-button").forEach(chip => {
        chip.addEventListener("click", () => handleChipClick(chip.dataset.chip));
    });
    document.getElementById("approve-confirmation").addEventListener("click", () => decideConfirmation(true));
    document.getElementById("reject-confirmation").addEventListener("click", () => decideConfirmation(false));
    document.getElementById("cancel-task").addEventListener("click", cancelCurrentTask);
    document.getElementById("export-markdown-btn").addEventListener("click", exportCurrentReportMarkdown);
    document.getElementById("export-junit-btn").addEventListener("click", exportCurrentReportJunit);
    document.querySelectorAll('input[name="allowedMethod"]').forEach(input => {
        input.addEventListener("change", renderAllowedMethodSummary);
    });
    bindFileName("openapi-file", "openapi-file-name", "选择 OpenAPI 文件");
    bindFileName("document-file", "document-file-name", "选择业务文档（可选）");
}

async function checkSystem() {
    const status = document.getElementById("system-status");
    const pulse = document.querySelector(".pulse");
    try {
        const data = await api("/api/v1/system/overview");
        status.textContent = `设施正常 · V${data.schemaVersion}`;
        pulse.classList.remove("offline");
    } catch (error) {
        status.textContent = "设施连接异常";
        pulse.classList.add("offline");
    }
}

async function loadProjects() {
    try {
        state.projects = await api("/api/v1/projects");
        const select = document.getElementById("project-select");
        select.textContent = "";
        select.append(option("", "请选择项目"));
        state.projects.forEach(project => select.append(option(String(project.id), project.name)));
        document.getElementById("metric-projects").textContent = state.projects.length;
        if (!state.projects.some(project => String(project.id) === state.currentProjectId)) {
            state.currentProjectId = state.projects.length ? String(state.projects[0].id) : "";
        }
        select.value = state.currentProjectId;
        await selectProject(state.currentProjectId);
    } catch (error) {
        notify(error.message, true);
    }
}

function bindFileName(inputId, labelId, emptyText) {
    document.getElementById(inputId).addEventListener("change", event => {
        const file = event.target.files[0];
        document.getElementById(labelId).textContent = file ? file.name : emptyText;
    });
}

function renderAllowedMethodSummary() {
    const methods = selectedAllowedMethods();
    document.getElementById("method-selector-summary").textContent = methods.length
        ? methods.join("、")
        : "请选择允许方法";
}

function selectedAllowedMethods() {
    return Array.from(document.querySelectorAll('input[name="allowedMethod"]:checked'))
        .map(input => input.value);
}

async function selectProject(projectId) {
    closeEventStream();
    state.currentProjectId = projectId || "";
    state.currentTaskId = "";
    state.currentReportId = "";
    localStorage.setItem("apipilot.projectId", state.currentProjectId);
    document.getElementById("project-select").value = state.currentProjectId;
    if (!state.currentProjectId) {
        resetProjectViews();
        return;
    }
    await Promise.all([
        loadEnvironments(),
        loadDocuments(),
        loadOpenApiState(),
        loadTasks(),
        loadReports()
    ]);
    renderOverview();
}

function resetProjectViews() {
    state.environments = [];
    state.documents = [];
    state.tasks = [];
    state.reports = [];
    state.currentReport = null;
    document.getElementById("export-markdown-btn").classList.add("hidden");
    document.getElementById("export-junit-btn").classList.add("hidden");
    renderEnvironments();
    renderDocuments();
    renderTasks();
    renderReports();
    renderOverview();
    renderCurrentProjectSummary();
}

async function refreshCurrentProject() {
    await checkSystem();
    await selectProject(state.currentProjectId);
    notify("数据已刷新");
}

async function createWorkspace(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const allowedMethods = selectedAllowedMethods();
    if (!allowedMethods.length) {
        notify("请至少选择一种允许的 HTTP 方法", true);
        document.getElementById("method-selector").open = true;
        return;
    }
    const openApiFile = document.getElementById("openapi-file").files[0];
    const documentFile = document.getElementById("document-file").files[0];
    if (!openApiFile) {
        notify("请选择 OpenAPI 文件", true);
        return;
    }
    const submitButton = document.getElementById("workspace-submit-button");
    let createdProjectId = "";
    submitButton.disabled = true;
    submitButton.textContent = "正在保存配置…";
    try {
        const project = await api("/api/v1/projects", {
            method: "POST",
            body: JSON.stringify({
                code: data.get("code"),
                name: data.get("projectName"),
                description: data.get("description")
            })
        });
        createdProjectId = String(project.id);
        await api(`/api/v1/projects/${createdProjectId}/environments`, {
            method: "POST",
            body: JSON.stringify({
                name: data.get("environmentName"),
                baseUrl: data.get("baseUrl"),
                allowedMethods: allowedMethods.join(","),
                allowPrivateNetwork: data.has("allowPrivateNetwork"),
                defaultEnvironment: data.has("defaultEnvironment")
            })
        });
        await uploadWorkspaceFile(
            `/api/v1/projects/${createdProjectId}/openapi/imports`,
            openApiFile
        );
        if (documentFile) {
            await uploadWorkspaceFile(
                `/api/v1/projects/${createdProjectId}/documents`,
                documentFile
            );
        }
        form.reset();
        renderAllowedMethodSummary();
        document.getElementById("openapi-file-name").textContent = "选择 OpenAPI 文件";
        document.getElementById("document-file-name").textContent = "选择业务文档（可选）";
        state.currentProjectId = createdProjectId;
        await loadProjects();
        await selectProject(createdProjectId);
        switchView("workspace");
        notify("项目及知识配置已全部保存");
    } catch (error) {
        if (createdProjectId) {
            try {
                await api(`/api/v1/projects/${createdProjectId}`, {method: "DELETE"});
            } catch (rollbackError) {
                console.warn("项目配置失败后的回滚未完成", rollbackError);
            }
        }
        notify(`保存失败：${error.message}`, true);
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = "保存全部配置";
    }
}

async function uploadWorkspaceFile(path, file) {
    const formData = new FormData();
    formData.append("file", file);
    return api(path, {method: "POST", body: formData});
}

async function deleteCurrentProject() {
    const project = state.projects.find(item => String(item.id) === state.currentProjectId);
    if (!project) {
        return;
    }
    const confirmed = window.confirm(
        `确认删除项目“${project.name}”？项目将从列表中移除，此操作不可在页面中撤销。`
    );
    if (!confirmed) {
        return;
    }
    try {
        await api(`/api/v1/projects/${project.id}`, {method: "DELETE"});
        localStorage.removeItem("apipilot.projectId");
        state.currentProjectId = "";
        await loadProjects();
        notify("项目已删除");
    } catch (error) {
        notify(`删除失败：${error.message}`, true);
    }
}

async function createEnvironment(event) {
    event.preventDefault();
    if (!requireProject()) {
        return;
    }
    const form = event.currentTarget;
    const data = new FormData(form);
    const request = {
        name: data.get("name"),
        baseUrl: data.get("baseUrl"),
        allowedMethods: data.get("allowedMethods"),
        allowPrivateNetwork: data.has("allowPrivateNetwork"),
        defaultEnvironment: data.has("defaultEnvironment")
    };
    try {
        await api(`/api/v1/projects/${state.currentProjectId}/environments`, {
            method: "POST",
            body: JSON.stringify(request)
        });
        form.reset();
        form.elements.allowedMethods.value = "GET,POST";
        form.elements.defaultEnvironment.checked = true;
        await loadEnvironments();
        notify("执行环境已保存");
    } catch (error) {
        notify(error.message, true);
    }
}

async function loadEnvironments() {
    if (!state.currentProjectId) {
        state.environments = [];
    } else {
        try {
            state.environments = await api(
                `/api/v1/projects/${state.currentProjectId}/environments`
            );
        } catch (error) {
            state.environments = [];
            notify(error.message, true);
        }
    }
    renderEnvironments();
}

function renderEnvironments() {
    const list = document.getElementById("environment-list");
    const taskSelect = document.getElementById("task-environment");
    list.textContent = "";
    taskSelect.textContent = "";
    document.getElementById("environment-count").textContent = `${state.environments.length} 个环境`;
    if (!state.environments.length) {
        taskSelect.append(option("", "请先配置环境"));
        list.textContent = "尚未配置";
        renderCurrentProjectSummary();
        return;
    }
    state.environments.forEach(environment => {
        list.append(element(
            "span",
            "summary-chip",
            `${environment.name} · ${environment.allowedMethods}${environment.defaultEnvironment ? " · 默认" : ""}`
        ));
        taskSelect.append(option(String(environment.id), `${environment.name} · ${environment.baseUrl}`));
    });
    const defaultEnvironment = state.environments.find(item => item.defaultEnvironment);
    taskSelect.value = String((defaultEnvironment || state.environments[0]).id);
    renderCurrentProjectSummary();
}

async function uploadOpenApi(event) {
    event.preventDefault();
    if (!requireProject()) {
        return;
    }
    const file = document.getElementById("openapi-file").files[0];
    if (!file) {
        return;
    }
    const formData = new FormData();
    formData.append("file", file);
    try {
        const result = await api(
            `/api/v1/projects/${state.currentProjectId}/openapi/imports`,
            {method: "POST", body: formData}
        );
        event.currentTarget.reset();
        document.getElementById("openapi-state").textContent =
            `${result.documentTitle || file.name} · ${result.endpointCount} 个接口 · ${result.status}`;
        notify("OpenAPI 导入完成");
    } catch (error) {
        notify(error.message, true);
    }
}

async function loadOpenApiState() {
    const line = document.getElementById("openapi-state");
    if (!state.currentProjectId) {
        line.textContent = "尚未载入当前接口目录";
        return;
    }
    try {
        const imports = await api(`/api/v1/projects/${state.currentProjectId}/openapi/imports`);
        state.openApiImports = imports;
        const current = imports.find(item => item.status === "SUCCEEDED");
        line.textContent = current
            ? `${current.documentTitle || current.fileName} · ${current.endpointCount} 个接口 · Revision ${current.revisionNumber}`
            : "尚未载入当前接口目录";
        state.endpoints = current
            ? await api(`/api/v1/projects/${state.currentProjectId}/openapi/endpoints`)
            : [];
        renderAgentAssets();
    } catch (error) {
        state.openApiImports = [];
        state.endpoints = [];
        line.textContent = "接口目录读取失败";
        renderAgentAssets();
    }
}

async function uploadDocument(event) {
    event.preventDefault();
    if (!requireProject()) {
        return;
    }
    const file = document.getElementById("document-file").files[0];
    if (!file) {
        return;
    }
    const formData = new FormData();
    formData.append("file", file);
    try {
        await api(`/api/v1/projects/${state.currentProjectId}/documents`, {
            method: "POST",
            body: formData
        });
        event.currentTarget.reset();
        await loadDocuments();
        notify("文档解析和索引完成");
    } catch (error) {
        notify(error.message, true);
    }
}

async function loadDocuments() {
    if (!state.currentProjectId) {
        state.documents = [];
    } else {
        try {
            state.documents = await api(`/api/v1/projects/${state.currentProjectId}/documents`);
        } catch (error) {
            state.documents = [];
        }
    }
    renderDocuments();
}

function renderDocuments() {
    const container = document.getElementById("document-list");
    container.textContent = "";
    if (!state.documents.length) {
        container.className = "summary-list";
        container.textContent = "暂无文档";
        renderCurrentProjectSummary();
        return;
    }
    container.className = "summary-list";
    state.documents.forEach(document => {
        container.append(element(
            "span",
            "summary-chip",
            `${document.fileName} · ${document.chunkCount} 个切片`
        ));
    });
    renderAgentAssets();
    renderCurrentProjectSummary();
}

function toggleAgentAssets() {
    const panel = document.getElementById("agent-assets-panel");
    const opening = panel.classList.contains("hidden");
    panel.classList.toggle("hidden", !opening);
    document.getElementById("toggle-agent-assets").textContent = opening ? "收起资料" : "查看资料";
}

function renderAgentAssets() {
    const summary = document.getElementById("agent-resource-summary");
    const fileList = document.getElementById("agent-file-list");
    const endpointList = document.getElementById("agent-endpoint-list");
    if (!summary || !fileList || !endpointList) {
        return;
    }
    const succeededImports = state.openApiImports.filter(item => item.status === "SUCCEEDED");
    summary.textContent = `${succeededImports.length} 份 OpenAPI · ${state.documents.length} 份业务文档 · ${state.endpoints.length} 个接口`;
    fileList.textContent = "";
    succeededImports.forEach(item => fileList.append(resourceFileRow(
        "OpenAPI",
        item.fileName || item.documentTitle,
        `${item.endpointCount} 个接口 · Revision ${item.revisionNumber}`
    )));
    state.documents.forEach(item => fileList.append(resourceFileRow(
        "业务文档",
        item.fileName,
        `${item.chunkCount} 个切片 · ${item.status}`
    )));
    if (!fileList.childElementCount) {
        fileList.append(element("p", "agent-assets-empty", "当前项目还没有上传文件"));
    }
    endpointList.textContent = "";
    state.endpoints.forEach(endpoint => {
        const row = element("div", "agent-endpoint-row");
        row.append(element("span", "method-chip", endpoint.httpMethod));
        const copy = element("div");
        copy.append(element("code", "", endpoint.path));
        copy.append(element("small", "", endpoint.summary || endpoint.operationId || "未填写说明"));
        row.append(copy);
        endpointList.append(row);
    });
    if (!endpointList.childElementCount) {
        endpointList.append(element("p", "agent-assets-empty", "尚未解析出可测试接口"));
    }
}

function resourceFileRow(type, name, meta) {
    const row = element("div", "agent-file-row");
    row.append(element("span", "resource-type", type));
    const copy = element("div");
    copy.append(element("strong", "", name || "未命名文件"));
    copy.append(element("small", "", meta));
    row.append(copy);
    return row;
}

function renderCurrentProjectSummary() {
    const project = state.projects.find(item => String(item.id) === state.currentProjectId);
    document.getElementById("delete-project-button").classList.toggle("hidden", !project);
    document.getElementById("current-project-summary").classList.toggle("hidden", !project);
    if (project) {
        document.getElementById("current-project-name").textContent = project.name;
    }
}

async function createTask(event) {
    event.preventDefault();
    if (!requireProject()) {
        return;
    }
    const form = event.currentTarget;
    const data = new FormData(form);
    const rawPlan = String(data.get("planHint") || "").trim();
    let planHint;
    try {
        planHint = rawPlan ? JSON.parse(rawPlan) : undefined;
        if (planHint && !Array.isArray(planHint)) {
            throw new Error("计划必须是 JSON 数组");
        }
    } catch (error) {
        notify(`计划 JSON 无效：${error.message}`, true);
        return;
    }
    const request = {
        environmentId: String(data.get("environmentId")),
        goal: data.get("goal")
    };
    if (planHint) {
        request.planHint = planHint;
    }
    try {
        const task = await api(`/api/v1/projects/${state.currentProjectId}/agent-tasks`, {
            method: "POST",
            body: JSON.stringify(request)
        });
        state.currentTaskId = String(task.id);
        document.getElementById("task-goal").value = "";
        await loadTasks();
        await openTask(state.currentTaskId);
        notify("Agent 任务已启动");
    } catch (error) {
        notify(error.message, true);
    }
}

async function loadTasks() {
    if (!state.currentProjectId) {
        state.tasks = [];
    } else {
        try {
            state.tasks = await api(
                `/api/v1/projects/${state.currentProjectId}/agent-tasks?limit=50`
            );
        } catch (error) {
            state.tasks = [];
        }
    }
    renderTasks();
    renderOverview();
}

function renderTasks() {
    const container = document.getElementById("task-list");
    document.getElementById("agent-history-count").textContent = `${state.tasks.length} 条`;
    container.textContent = "";
    if (!state.tasks.length) {
        container.className = "task-list empty-state";
        container.textContent = "当前项目暂无任务";
        return;
    }
    container.className = "task-list";
    state.tasks.forEach(task => {
        const button = element(
            "button",
            `task-card ${String(task.id) === state.currentTaskId ? "active" : ""}`
        );
        button.type = "button";
        const top = element("span");
        top.append(statusBadge(task.status));
        top.append(element("small", "", formatDate(task.createdAt)));
        button.append(top);
        button.append(element("strong", "", task.goal));
        button.append(element(
            "small",
            "",
            `${task.toolCallCount} 次工具调用 · ${task.replanCount} 次重规划`
        ));
        if (task.status === "FAILED" && task.errorMessage) {
            button.append(element("span", "task-error", task.errorMessage));
        }
        button.addEventListener("click", () => openTask(String(task.id)));
        container.append(button);
    });
}

async function openTask(taskId) {
    if (!state.currentProjectId) {
        return;
    }
    state.currentTaskId = taskId;
    state.lastSequence = 0;
    renderTasks();
    try {
        const task = await api(
            `/api/v1/projects/${state.currentProjectId}/agent-tasks/${taskId}`
        );
        renderTaskDetail(task);
        startEventStream(taskId);
    } catch (error) {
        notify(error.message, true);
    }
}

function renderTaskDetail(task) {
    const modelCalls = task.modelCalls || [];
    const totalTokens = modelCalls.reduce(
        (sum, call) => sum + Number(call.totalTokens || 0),
        0
    );
    const modelDuration = modelCalls.reduce(
        (sum, call) => sum + Number(call.durationMs || 0),
        0
    );
    const status = document.getElementById("trace-status");
    status.className = `status-badge ${statusClass(task.status)}`;
    status.textContent = task.status;
    document.getElementById("trace-title").textContent = task.goal;
    document.getElementById("trace-meta").textContent =
        `Task ${task.id} · ${task.toolCallCount} 次工具调用 · `
        + `${totalTokens} Tokens · 模型 ${modelDuration} ms`;
    const errorBox = document.getElementById("trace-error");
    const failed = task.status === "FAILED" && task.errorMessage;
    errorBox.classList.toggle("hidden", !failed);
    errorBox.textContent = failed
        ? `${task.errorCode || "AGENT_FAILED"}：${task.errorMessage}`
        : "";
    document.getElementById("cancel-task").classList.toggle("hidden", isTerminal(task.status));
    const confirmation = document.getElementById("confirmation-box");
    const modifyDrawer = document.getElementById("plan-modify-drawer");
    const waiting = task.status === "WAITING_CONFIRMATION"
        && task.confirmation
        && task.confirmation.status === "PENDING";
    confirmation.classList.toggle("hidden", !waiting);
    if (waiting) {
        document.getElementById("confirmation-copy").textContent =
            `计划步骤 ${task.confirmation.stepIndex + 1} 包含受控敏感操作，请确认执行或修改计划。`;
        const modCount = task.modificationCount || 0;
        const remaining = Math.max(0, 3 - modCount);
        const badge = document.getElementById("modify-count-badge");
        const submitBtn = document.getElementById("submit-modify-btn");
        const input = document.getElementById("modify-instruction-input");

        if (remaining <= 0) {
            badge.textContent = "已达上限 (3/3)";
            badge.className = "count-pill limit-reached";
            submitBtn.disabled = true;
            input.disabled = true;
            input.placeholder = "已达 3 轮修改上限，请直接确认执行或取消任务重新发起。";
        } else {
            badge.textContent = `还可修改 ${remaining} 次`;
            badge.className = "count-pill";
            submitBtn.disabled = false;
            input.disabled = false;
            input.placeholder = "请输入您的计划调整要求…";
        }
    } else {
        modifyDrawer.classList.add("hidden");
    }
    renderTestPlan(task);
    renderToolCalls(task.toolCalls || [], modelCalls);
}

function renderTestPlan(task) {
    const container = document.getElementById("test-plan");
    const progress = document.getElementById("plan-progress");
    const plan = task.plan || [];
    container.textContent = "";
    if (!plan.length) {
        container.className = "test-plan empty-state";
        container.textContent = ["RECEIVED", "RETRIEVING", "PLANNING"].includes(task.status)
            ? "Agent 正在检索资料并生成测试计划…"
            : "该任务没有生成可执行计划";
        progress.textContent = task.status === "PLANNING" ? "生成中" : "等待生成";
        return;
    }
    container.className = "test-plan";
    const completedCount = plan.filter(step => planStepState(task, step).status === "success").length;
    progress.textContent = `${completedCount}/${plan.length} 已通过`;
    plan.forEach(step => container.append(renderPlanStep(task, step)));
}

function renderPlanStep(task, step) {
    const stateInfo = planStepState(task, step);
    const request = step.request || {};
    const row = element("article", `test-plan-step ${stateInfo.status}`);
    row.append(element("span", "plan-step-mark", stateInfo.mark));
    const copy = element("div", "plan-step-copy");
    copy.append(element("small", "plan-step-index", `步骤 ${Number(step.index) + 1}`));
    copy.append(element("strong", "", request.name || step.objective || "未命名步骤"));
    const route = element("div", "plan-step-route");
    route.append(element("span", "method-chip", request.method || "HTTP"));
    route.append(element("code", "", request.path || "未指定路径"));
    copy.append(route);
    const assertionCount = Array.isArray(request.assertions) ? request.assertions.length : 0;
    copy.append(element("small", "plan-step-meta", `${step.objective || "执行接口验证"} · ${assertionCount} 条断言`));
    if (stateInfo.message) {
        copy.append(element("p", "plan-step-result", stateInfo.message));
    }
    row.append(copy);
    row.append(element("span", "plan-step-status", stateInfo.label));
    return row;
}

function planStepState(task, step) {
    const calls = (task.toolCalls || []).filter(call =>
        Number(call.stepIndex) === Number(step.index) && call.toolName === "executeHttpRequest"
    );
    const failed = calls.find(call => call.status === "FAILED");
    if (failed) {
        return {
            status: "failed",
            mark: "×",
            label: "失败",
            message: failed.errorMessage || "接口请求或断言未通过"
        };
    }
    const succeeded = calls.find(call => call.status === "SUCCEEDED");
    if (succeeded) {
        return {
            status: "success",
            mark: "✓",
            label: "已通过",
            message: succeeded.durationMs == null ? "执行完成" : `执行完成 · ${succeeded.durationMs} ms`
        };
    }
    const running = calls.find(call => ["PENDING", "RUNNING"].includes(call.status));
    if (running || (task.status === "EXECUTING" && Number(task.currentStep) === Number(step.index))) {
        return {status: "running", mark: "…", label: "执行中", message: "正在调用接口并校验响应"};
    }
    return {status: "pending", mark: String(Number(step.index) + 1), label: "待执行", message: ""};
}

function toggleTracePanel() {
    const panel = document.querySelector(".trace-panel");
    const collapsed = panel.classList.toggle("collapsed");
    document.getElementById("toggle-trace").textContent = collapsed ? "展开轨迹" : "收起轨迹";
}

async function startEventStream(taskId) {
    closeEventStream();
    const trace = document.getElementById("trace-list");
    trace.textContent = "";
    trace.className = "timeline";
    const url = `/api/v1/projects/${state.currentProjectId}/agent-tasks/${taskId}/stream?after=${state.lastSequence}`;
    const controller = new AbortController();
    state.eventSource = controller;
    document.getElementById("trace-meta").textContent = "SSE 已连接 · 正在读取持久化事件";
    try {
        const response = await fetch(url, {
            headers: {
                "Accept": "text/event-stream"
            },
            signal: controller.signal
        });
        if (!response.ok || !response.body) {
            throw new Error(`SSE 建连失败：HTTP ${response.status}`);
        }
        await consumeEventStream(response.body, taskId, controller);
    } catch (error) {
        if (controller.signal.aborted || controller !== state.eventSource) {
            return;
        }
        document.getElementById("trace-meta").textContent =
            `SSE 重连中 · 游标 ${state.lastSequence}`;
        window.setTimeout(() => {
            if (controller === state.eventSource) {
                startEventStream(taskId);
            }
        }, 1000);
    }
}

async function consumeEventStream(body, taskId, controller) {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (!controller.signal.aborted) {
        const {value, done} = await reader.read();
        if (done) {
            break;
        }
        buffer += decoder.decode(value, {stream: true}).replace(/\r\n/g, "\n");
        let boundary;
        while ((boundary = buffer.indexOf("\n\n")) >= 0) {
            const frame = buffer.slice(0, boundary);
            buffer = buffer.slice(boundary + 2);
            handleSseFrame(frame, taskId);
        }
    }
}

function handleSseFrame(frame, taskId) {
    const data = frame.split("\n")
        .filter(line => line.startsWith("data:"))
        .map(line => line.slice(5).trimStart())
        .join("\n");
    if (!data) {
        return;
    }
    const message = safeJsonParse(data);
    state.lastSequence = Math.max(state.lastSequence, Number(message.sequenceNo || 0));
    appendTimelineEvent(message);
    if (["PLAN_CREATED", "PLAN_REVISED", "TOOL_STARTED", "TOOL_RETRIED", "TOOL_COMPLETED", "TOOL_FAILED", "CONFIRMATION_REQUIRED", "CONFIRMATION_DECIDED", "TASK_COMPLETED", "TASK_CANCELLED"].includes(message.eventType)) {
        refreshTaskDetail(taskId);
        if (message.eventType === "PLAN_REVISED") {
            notify("Agent 执行计划已更新");
        }
    }
    if (["TASK_COMPLETED", "TASK_CANCELLED"].includes(message.eventType)) {
        closeEventStream();
        loadReports();
        loadTasks();
    }
}

function closeEventStream() {
    if (state.eventSource) {
        state.eventSource.abort();
        state.eventSource = null;
    }
}

async function refreshTaskDetail(taskId) {
    if (taskId !== state.currentTaskId) {
        return;
    }
    try {
        const task = await api(
            `/api/v1/projects/${state.currentProjectId}/agent-tasks/${taskId}`
        );
        renderTaskDetail(task);
    } catch (error) {
        notify(error.message, true);
    }
}

function appendTimelineEvent(event) {
    const trace = document.getElementById("trace-list");
    const row = element("div", "timeline-event");
    row.append(element("span", "timeline-dot"));
    const copy = element("div");
    copy.append(element("strong", "", eventLabel(event.eventType)));
    copy.append(element("small", "", summarizePayload(event.payload)));
    row.append(copy);
    row.append(element("time", "", `#${event.sequenceNo} · ${formatTime(event.createdAt)}`));
    trace.append(row);
    trace.scrollTop = trace.scrollHeight;
}

function renderToolCalls(calls, modelCalls = []) {
    const drawer = document.getElementById("tool-drawer");
    drawer.textContent = "";
    modelCalls.forEach(call => {
        const row = element("div", "tool-row model-row");
        row.append(statusBadge(call.status));
        row.append(element(
            "code",
            "",
            `${call.modelName} · planning attempt ${call.attempt}`
        ));
        row.append(element(
            "small",
            "",
            `${call.totalTokens} Tokens · ${call.durationMs} ms`
        ));
        drawer.append(row);
    });
    calls.forEach(call => {
        const row = element("div", "tool-row");
        row.append(statusBadge(call.status));
        row.append(element("code", "", `${call.toolName} · attempt ${call.attempt}`));
        row.append(element("small", "", call.durationMs == null ? "运行中" : `${call.durationMs} ms`));
        drawer.append(row);
    });
}

async function decideConfirmation(approved) {
    if (!state.currentTaskId) {
        return;
    }
    try {
        const task = await api(
            `/api/v1/projects/${state.currentProjectId}/agent-tasks/${state.currentTaskId}/confirmation`,
            {
                method: "POST",
                body: JSON.stringify({
                    approved,
                    note: approved ? "通过 Web 控制台批准" : "通过 Web 控制台拒绝"
                })
            }
        );
        renderTaskDetail(task);
        notify(approved ? "危险操作已批准" : "危险操作已拒绝");
    } catch (error) {
        notify(error.message, true);
    }
}

function toggleModifyDrawer() {
    const drawer = document.getElementById("plan-modify-drawer");
    drawer.classList.toggle("hidden");
    if (!drawer.classList.contains("hidden")) {
        document.getElementById("modify-instruction-input").focus();
    }
}

function closeModifyDrawer() {
    document.getElementById("plan-modify-drawer").classList.add("hidden");
}

function handleChipClick(text) {
    const input = document.getElementById("modify-instruction-input");
    if (input.disabled) {
        return;
    }
    input.value = text;
    input.focus();
}

async function submitPlanModification(event) {
    event.preventDefault();
    if (!state.currentTaskId) {
        return;
    }
    const input = document.getElementById("modify-instruction-input");
    const instruction = input.value.trim();
    if (!instruction) {
        notify("请输入修改指令", true);
        return;
    }
    const statusText = document.getElementById("modify-status-text");
    const submitBtn = document.getElementById("submit-modify-btn");
    submitBtn.disabled = true;
    statusText.className = "modify-status-text loading";
    statusText.textContent = "模型正在重构执行计划…";

    try {
        const task = await api(
            `/api/v1/projects/${state.currentProjectId}/agent-tasks/${state.currentTaskId}/modify`,
            {
                method: "POST",
                body: JSON.stringify({ instruction })
            }
        );
        input.value = "";
        statusText.className = "modify-status-text";
        statusText.textContent = "";
        renderTaskDetail(task);
        notify("计划已成功修改并重新生成");
    } catch (error) {
        statusText.className = "modify-status-text error";
        statusText.textContent = error.message;
        notify(`计划修改失败：${error.message}`, true);
    } finally {
        submitBtn.disabled = false;
    }
}

async function cancelCurrentTask() {
    if (!state.currentTaskId) {
        return;
    }
    try {
        const task = await api(
            `/api/v1/projects/${state.currentProjectId}/agent-tasks/${state.currentTaskId}/cancellation`,
            {method: "POST"}
        );
        renderTaskDetail(task);
        notify("取消请求已提交");
    } catch (error) {
        notify(error.message, true);
    }
}

function exportCurrentReportMarkdown() {
    if (!state.currentReport) {
        notify("请先选择一份测试报告", true);
        return;
    }
    const markdown = generateReportMarkdown(state.currentReport);
    copyTextToClipboard(markdown, "Markdown 报告已复制到剪贴板");
}

function exportCurrentReportJunit() {
    if (!state.currentReport) {
        notify("请先选择一份测试报告", true);
        return;
    }
    const xml = generateReportJunitXml(state.currentReport);
    downloadFile(`test-report-${state.currentReport.id || "export"}.xml`, xml, "application/xml");
    notify("JUnit XML 报告已开始下载");
}

function generateReportMarkdown(report) {
    const rate = report.totalSteps ? Math.round(report.passedSteps / report.totalSteps * 100) : 0;
    const lines = [
        `# ApiPilot 测试诊断报告: ${report.title || "Agent 自动化测试"}`,
        ``,
        `> **执行状态**: ${report.status} | **通过率**: ${rate}% (${report.passedSteps}/${report.totalSteps}) | **耗时**: ${report.durationMs || 0}ms`,
        ``,
        `## 一、 摘要与指标`,
        `- **任务 ID**: \`${report.taskId || "—"}\``,
        `- **生成时间**: ${formatDate(report.createdAt)}`,
        `- **总结说明**: ${report.summary || "无"}`,
        `- **工具调用总计**: ${report.totalToolCalls || 0} 次`,
        ``,
        `## 二、 RAG 知识检索证据`,
    ];

    if (report.evidenceCitations && report.evidenceCitations.length) {
        report.evidenceCitations.forEach(citation => {
            lines.push(`- \`${citation}\``);
        });
    } else {
        lines.push(`- *本次任务未命中文档证据*`);
    }

    lines.push(``, `## 三、 接口执行步骤明细`, ``);
    lines.push(`| 序号 | 步骤名称 | Method | 响应状态 | 耗时 | 判定 |`);
    lines.push(`| :--- | :--- | :--- | :--- | :--- | :--- |`);

    (report.steps || []).forEach((step, idx) => {
        const statusStr = step.success ? "✅ 通过" : "❌ 失败";
        lines.push(`| ${idx + 1} | ${step.stepName || "未命名"} | \`${step.httpMethod || "GET"}\` | \`${step.responseStatus || "—"}\` | ${step.durationMs || 0}ms | ${statusStr} |`);
    });

    lines.push(``, `---`, `*由 ApiPilot Agent 自动生成 · 凭证数据已自动脱敏*`);
    return lines.join("\n");
}

function generateReportJunitXml(report) {
    const total = report.totalSteps || 0;
    const failures = Math.max(0, total - (report.passedSteps || 0));
    const timeSec = ((report.durationMs || 0) / 1000).toFixed(3);
    const lines = [
        `<?xml version="1.0" encoding="UTF-8"?>`,
        `<testsuite name="ApiPilot.AgentTasks" tests="${total}" failures="${failures}" errors="0" time="${timeSec}" timestamp="${new Date().toISOString()}">`,
    ];

    (report.steps || []).forEach((step, idx) => {
        const stepTime = ((step.durationMs || 0) / 1000).toFixed(3);
        lines.push(`  <testcase classname="ApiPilot.${step.httpMethod || "HTTP"}" name="Step${idx + 1}_${escapeXml(step.stepName || "Step")}" time="${stepTime}">`);
        if (!step.success) {
            lines.push(`    <failure message="Response status: ${step.responseStatus || "FAILED"}"><![CDATA[URL: ${step.requestUrl}\nStatus: ${step.responseStatus}]]></failure>`);
        }
        lines.push(`  </testcase>`);
    });

    lines.push(`</testsuite>`);
    return lines.join("\n");
}

function escapeXml(text) {
    return String(text || "").replace(/[<>&'"]/g, char => {
        switch (char) {
            case '<': return '&lt;';
            case '>': return '&gt;';
            case '&': return '&amp;';
            case '\'': return '&apos;';
            case '"': return '&quot;';
            default: return char;
        }
    });
}

function copyTextToClipboard(text, successMsg) {
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(() => {
            notify(successMsg);
        }).catch(() => {
            fallbackCopy(text, successMsg);
        });
    } else {
        fallbackCopy(text, successMsg);
    }
}

function fallbackCopy(text, successMsg) {
    const textArea = document.createElement("textarea");
    textArea.value = text;
    textArea.style.position = "fixed";
    textArea.style.opacity = "0";
    document.body.appendChild(textArea);
    textArea.select();
    try {
        document.execCommand("copy");
        notify(successMsg);
    } catch (e) {
        notify("复制失败，请手动选择复制", true);
    }
    document.body.removeChild(textArea);
}

function downloadFile(filename, content, mimeType) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

async function loadReports() {
    if (!state.currentProjectId) {
        state.reports = [];
    } else {
        try {
            state.reports = await api(
                `/api/v1/projects/${state.currentProjectId}/reports?limit=50`
            );
        } catch (error) {
            state.reports = [];
        }
    }
    renderReports();
    renderOverview();
}

function renderReports() {
    const container = document.getElementById("report-list");
    container.textContent = "";
    if (!state.reports.length) {
        container.className = "report-list empty-state";
        container.textContent = "当前项目暂无报告";
        return;
    }
    container.className = "report-list";
    state.reports.forEach(report => {
        const button = element(
            "button",
            `report-card ${String(report.id) === state.currentReportId ? "active" : ""}`
        );
        button.type = "button";
        button.append(statusBadge(report.status));
        button.append(element("h4", "", report.title));
        const meta = element("div");
        meta.append(element("span", "", `${report.passedSteps}/${report.totalSteps} 通过`));
        meta.append(element("span", "", formatDate(report.createdAt)));
        button.append(meta);
        button.addEventListener("click", () => openReport(String(report.id)));
        container.append(button);
    });
}

async function openReport(reportId) {
    state.currentReportId = reportId;
    renderReports();
    try {
        const report = await api(
            `/api/v1/projects/${state.currentProjectId}/reports/${reportId}`
        );
        renderReportDetail(report);
    } catch (error) {
        notify(error.message, true);
    }
}

function renderReportDetail(report) {
    state.currentReport = report;
    document.getElementById("export-markdown-btn").classList.remove("hidden");
    document.getElementById("export-junit-btn").classList.remove("hidden");

    const container = document.getElementById("report-detail");
    container.textContent = "";
    container.className = "";

    const head = element("div", "report-head");
    const copy = element("div");
    copy.append(statusBadge(report.status));
    copy.append(element("h3", "", report.title));
    copy.append(element("p", "", report.summary));
    head.append(copy);
    const rate = report.totalSteps ? Math.round(report.passedSteps / report.totalSteps * 100) : 0;
    head.append(element("div", "score-ring", `${rate}%`));
    container.append(head);

    const metrics = element("div", "report-metrics");
    [
        [report.totalSteps, "总步骤"],
        [report.passedSteps, "通过"],
        [report.totalToolCalls, "工具调用"],
        [`${report.durationMs}ms`, "执行耗时"]
    ].forEach(([value, label]) => {
        const card = element("div", "report-metric");
        card.append(element("strong", "", String(value)));
        card.append(element("small", "", label));
        metrics.append(card);
    });
    container.append(metrics);

    const evidence = element("div", "evidence-box");
    evidence.append(element("strong", "", "RAG Evidence"));
    if (report.evidenceCitations && report.evidenceCitations.length) {
        report.evidenceCitations.forEach(citation => evidence.append(element("code", "", citation)));
    } else {
        evidence.append(element("code", "", "本次任务未命中文档证据"));
    }
    container.append(evidence);

    const steps = element("div", "report-steps");
    (report.steps || []).forEach(step => steps.append(renderReportStep(step)));
    container.append(steps);
}

function renderReportStep(step) {
    const details = element("details", "report-step");
    const summary = element("summary");
    summary.append(element("span", "method-chip", step.httpMethod));
    summary.append(element("strong", "", `${step.stepIndex + 1}. ${step.stepName}`));
    summary.append(element(
        "small",
        "",
        `${step.responseStatus || "—"} · ${step.durationMs} ms · ${step.success ? "通过" : "失败"}`
    ));
    details.append(summary);

    const exchange = element("div", "exchange-grid");
    exchange.append(exchangeColumn(
        "Request · 已脱敏",
        {
            url: step.requestUrl,
            headers: redactDeep(step.requestHeaders),
            body: redactDeep(step.requestBody)
        }
    ));
    exchange.append(exchangeColumn(
        "Response · 已脱敏",
        {
            status: step.responseStatus,
            headers: redactDeep(step.responseHeaders),
            body: redactDeep(step.responseBody),
            assertions: redactDeep(step.assertions)
        }
    ));
    details.append(exchange);
    return details;
}

function exchangeColumn(title, value) {
    const column = element("div", "exchange-column");
    column.append(element("h5", "", title));
    const pre = element("pre");
    pre.textContent = JSON.stringify(value, null, 2);
    column.append(pre);
    return column;
}

function renderOverview() {
    document.getElementById("metric-tasks").textContent = state.tasks.length;
    const completed = state.reports.filter(report => report.totalSteps > 0);
    const passed = completed.reduce((sum, report) => sum + report.passedSteps, 0);
    const total = completed.reduce((sum, report) => sum + report.totalSteps, 0);
    document.getElementById("metric-success").textContent =
        total ? `${Math.round(passed / total * 100)}%` : "—";

    const list = document.getElementById("overview-task-list");
    list.textContent = "";
    if (!state.tasks.length) {
        list.className = "compact-list empty-state";
        list.textContent = state.currentProjectId ? "当前项目暂无任务" : "选择项目后显示任务";
        return;
    }
    list.className = "compact-list";
    state.tasks.slice(0, 4).forEach(task => {
        const item = element("div", "compact-item");
        item.append(element("span", `compact-mark ${statusClass(task.status)}`));
        const copy = element("div");
        copy.append(element("strong", "", task.goal));
        copy.append(element("small", "", `${task.toolCallCount} 次工具调用`));
        item.append(copy);
        item.append(element("small", "", formatDate(task.createdAt)));
        list.append(item);
    });
}

async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.body && !(options.body instanceof FormData)) {
        headers.set("Content-Type", "application/json");
    }
    const response = await fetch(path, {...options, headers});
    const text = await response.text();
    const payload = text ? safeJsonParse(text) : null;
    if (!response.ok || (payload && payload.code && payload.code !== "SUCCESS")) {
        throw new Error(payload?.message || `请求失败：HTTP ${response.status}`);
    }
    return payload?.data;
}

function safeJsonParse(text) {
    const protectedText = text.replace(
        /("(?:id|projectId|environmentId|conversationId|taskId|reportId|executionId|toolCallId|documentId|endpointId|importId)"\s*:\s*)(\d{16,})/g,
        '$1"$2"'
    );
    return JSON.parse(protectedText);
}

function redactDeep(value, fieldName = "") {
    if (value == null) {
        return value;
    }
    if (/authorization|token|password|secret|cookie|api[-_]?key/i.test(fieldName)) {
        return "******";
    }
    if (Array.isArray(value)) {
        return value.map(item => redactDeep(item, fieldName));
    }
    if (typeof value === "object") {
        return Object.fromEntries(
            Object.entries(value).map(([key, child]) => [key, redactDeep(child, key)])
        );
    }
    if (typeof value === "string") {
        return value
            .replace(/(Bearer\s+)[^\s,;]+/gi, "$1******")
            .replace(/((?:token|password|secret|api[-_]?key)\s*[:=]\s*)[^\s,;]+/gi, "$1******");
    }
    return value;
}

function requireProject() {
    if (state.currentProjectId) {
        return true;
    }
    notify("请先创建或选择项目", true);
    switchView("workspace");
    return false;
}

function statusClass(status) {
    if (["SUCCEEDED", "INDEXED"].includes(status)) {
        return "success";
    }
    if (["FAILED", "REJECTED"].includes(status)) {
        return "failed";
    }
    if (["WAITING_CONFIRMATION", "PENDING"].includes(status)) {
        return "warning";
    }
    if (["RECEIVED", "RETRIEVING", "PLANNING", "EXECUTING", "OBSERVING", "REPLANNING", "REPORTING", "RUNNING"].includes(status)) {
        return "running";
    }
    return "neutral";
}

function statusBadge(status) {
    return element("span", `status-badge ${statusClass(status)}`, status);
}

function isTerminal(status) {
    return ["SUCCEEDED", "FAILED", "CANCELLED"].includes(status);
}

function eventLabel(type) {
    const labels = {
        TASK_CREATED: "任务已创建",
        STATE_CHANGED: "状态迁移",
        RETRIEVAL_COMPLETED: "知识检索完成",
        PLAN_CREATED: "结构化计划生成",
        TOOL_STARTED: "工具开始执行",
        TOOL_RETRIED: "工具调用重试",
        TOOL_COMPLETED: "工具执行完成",
        TOOL_FAILED: "工具执行失败",
        CONFIRMATION_REQUIRED: "等待危险操作确认",
        CONFIRMATION_DECIDED: "人工确认已处理",
        REPLAN_STARTED: "开始重新规划",
        CANCEL_REQUESTED: "收到取消请求",
        TASK_CANCELLED: "任务已取消",
        REPORT_GENERATED: "测试报告已生成",
        TASK_COMPLETED: "任务进入终态"
    };
    return labels[type] || type;
}

function summarizePayload(payload) {
    if (!payload || !Object.keys(payload).length) {
        return "事件已持久化";
    }
    if (payload.toolName) {
        return `${payload.toolName}${payload.durationMs != null ? ` · ${payload.durationMs} ms` : ""}`;
    }
    if (payload.executionStatus) {
        return `Execution ${payload.executionId} · ${payload.executionStatus}`;
    }
    if (payload.stepCount != null) {
        return `${payload.stepCount} 个计划步骤`;
    }
    if (payload.resultCount != null) {
        return `${payload.resultCount} 条检索证据`;
    }
    if (payload.status) {
        return payload.status;
    }
    return JSON.stringify(redactDeep(payload)).slice(0, 120);
}

function element(tag, className = "", text = "") {
    const node = document.createElement(tag);
    if (className) {
        node.className = className;
    }
    if (text !== "") {
        node.textContent = text;
    }
    return node;
}

function option(value, text) {
    const node = document.createElement("option");
    node.value = value;
    node.textContent = text;
    return node;
}

function formatDate(value) {
    if (!value) {
        return "—";
    }
    return new Intl.DateTimeFormat("zh-CN", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    }).format(new Date(value));
}

function formatTime(value) {
    if (!value) {
        return "—";
    }
    return new Intl.DateTimeFormat("zh-CN", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
    }).format(new Date(value));
}

function notify(message, error = false) {
    const region = document.getElementById("toast-region");
    const toast = element("div", `toast ${error ? "error" : ""}`, message);
    region.append(toast);
    window.setTimeout(() => toast.remove(), 4200);
}
