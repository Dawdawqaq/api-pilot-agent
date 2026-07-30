const state = {
    projects: [],
    currentProjectId: localStorage.getItem("apipilot.projectId") || "",
    environments: [],
    documents: [],
    tasks: [],
    reports: [],
    currentTaskId: "",
    currentReportId: "",
    eventSource: null,
    lastSequence: 0
};

const titles = {
    overview: "运行概览",
    workspace: "项目与知识资产",
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
    document.getElementById("project-form").addEventListener("submit", createProject);
    document.getElementById("environment-form").addEventListener("submit", createEnvironment);
    document.getElementById("openapi-form").addEventListener("submit", uploadOpenApi);
    document.getElementById("document-form").addEventListener("submit", uploadDocument);
    document.getElementById("task-form").addEventListener("submit", createTask);
}

function bindActions() {
    document.getElementById("project-select").addEventListener("change", event => {
        selectProject(event.target.value);
    });
    document.getElementById("refresh-button").addEventListener("click", refreshCurrentProject);
    document.getElementById("reload-tasks").addEventListener("click", loadTasks);
    document.getElementById("reload-reports").addEventListener("click", loadReports);
    document.getElementById("approve-confirmation").addEventListener("click", () => decideConfirmation(true));
    document.getElementById("reject-confirmation").addEventListener("click", () => decideConfirmation(false));
    document.getElementById("cancel-task").addEventListener("click", cancelCurrentTask);
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
    renderEnvironments();
    renderDocuments();
    renderTasks();
    renderReports();
    renderOverview();
}

async function refreshCurrentProject() {
    await checkSystem();
    await selectProject(state.currentProjectId);
    notify("数据已刷新");
}

async function createProject(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form));
    try {
        const project = await api("/api/v1/projects", {
            method: "POST",
            body: JSON.stringify(values)
        });
        form.reset();
        await loadProjects();
        await selectProject(String(project.id));
        switchView("workspace");
        notify("项目创建成功");
    } catch (error) {
        notify(error.message, true);
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
    document.getElementById("environment-count").textContent = `${state.environments.length} 个`;
    if (!state.environments.length) {
        taskSelect.append(option("", "请先配置环境"));
        return;
    }
    state.environments.forEach(environment => {
        const tag = element("span", "tag");
        tag.textContent = `${environment.name} · ${environment.allowedMethods}${environment.defaultEnvironment ? " · 默认" : ""}`;
        list.append(tag);
        taskSelect.append(option(String(environment.id), `${environment.name} · ${environment.baseUrl}`));
    });
    const defaultEnvironment = state.environments.find(item => item.defaultEnvironment);
    taskSelect.value = String((defaultEnvironment || state.environments[0]).id);
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
        const current = imports.find(item => item.status === "SUCCEEDED");
        line.textContent = current
            ? `${current.documentTitle || current.fileName} · ${current.endpointCount} 个接口 · Revision ${current.revisionNumber}`
            : "尚未载入当前接口目录";
    } catch (error) {
        line.textContent = "接口目录读取失败";
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
        container.className = "compact-list empty-state";
        container.textContent = "暂无文档";
        return;
    }
    container.className = "compact-list";
    state.documents.forEach(document => {
        const item = element("div", "compact-item");
        item.append(element("span", `compact-mark ${document.status === "INDEXED" ? "success" : "running"}`));
        const copy = element("div");
        copy.append(element("strong", "", document.fileName));
        copy.append(element("small", "", `${document.chunkCount} 个切片 · ${document.status}`));
        item.append(copy);
        item.append(element("small", "", formatDate(document.createdAt)));
        container.append(item);
    });
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
    document.getElementById("cancel-task").classList.toggle("hidden", isTerminal(task.status));
    const confirmation = document.getElementById("confirmation-box");
    const waiting = task.status === "WAITING_CONFIRMATION"
        && task.confirmation
        && task.confirmation.status === "PENDING";
    confirmation.classList.toggle("hidden", !waiting);
    if (waiting) {
        document.getElementById("confirmation-copy").textContent =
            `计划步骤 ${task.confirmation.stepIndex + 1} 包含 DELETE，请确认是否允许。`;
    }
    renderToolCalls(task.toolCalls || [], modelCalls);
}

function startEventStream(taskId) {
    closeEventStream();
    const trace = document.getElementById("trace-list");
    trace.textContent = "";
    trace.className = "timeline";
    const url = `/api/v1/projects/${state.currentProjectId}/agent-tasks/${taskId}/stream?after=${state.lastSequence}`;
    const source = new EventSource(url);
    state.eventSource = source;
    document.getElementById("trace-meta").textContent = "SSE 已连接 · 正在读取持久化事件";
    source.addEventListener("agent-event", event => {
        const message = safeJsonParse(event.data);
        state.lastSequence = Math.max(state.lastSequence, Number(message.sequenceNo || 0));
        appendTimelineEvent(message);
        if (["CONFIRMATION_REQUIRED", "CONFIRMATION_DECIDED", "TASK_COMPLETED", "TASK_CANCELLED"].includes(message.eventType)) {
            refreshTaskDetail(taskId);
        }
        if (["TASK_COMPLETED", "TASK_CANCELLED"].includes(message.eventType)) {
            closeEventStream();
            loadReports();
            loadTasks();
        }
    });
    source.onerror = () => {
        document.getElementById("trace-meta").textContent =
            `SSE 重连中 · 游标 ${state.lastSequence}`;
    };
}

function closeEventStream() {
    if (state.eventSource) {
        state.eventSource.close();
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
