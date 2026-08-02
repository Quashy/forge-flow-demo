const state = {
    users: [],
    currentUser: null,
    dashboard: null,
    projects: [],
    tasks: [],
    taskView: "todo",
    activeView: "overview",
    activeTask: null,
    activeAction: null,
    search: "",
    loading: false,
    csrfToken: null,
    csrfHeaderName: "X-CSRF-TOKEN"
};

const els = {
    userSelect: document.querySelector("#demo-user-select"),
    activeUserCard: document.querySelector("#active-user-card"),
    metricGrid: document.querySelector("#metric-grid"),
    urgentTaskList: document.querySelector("#urgent-task-list"),
    activityList: document.querySelector("#activity-list"),
    taskGrid: document.querySelector("#task-grid"),
    projectTableBody: document.querySelector("#project-table-body"),
    projectCardList: document.querySelector("#project-card-list"),
    navTaskCount: document.querySelector("#nav-task-count"),
    toast: document.querySelector("#toast"),
    sidebar: document.querySelector("#sidebar"),
    menuButton: document.querySelector("#menu-button"),
    createDialog: document.querySelector("#create-project-dialog"),
    createForm: document.querySelector("#create-project-form"),
    actionDialog: document.querySelector("#task-action-dialog"),
    actionForm: document.querySelector("#task-action-form"),
    detailDialog: document.querySelector("#project-detail-dialog"),
    detailContent: document.querySelector("#project-detail-content"),
    projectSearch: document.querySelector("#project-search")
};

const ACTION_COPY = {
    APPROVE: { title: "审批通过", kicker: "APPROVE", button: "确认通过", placeholder: "填写审批意见（可选）" },
    UPPER_HELP: { title: "申请上级协助", kicker: "ESCALATE", button: "提交上级", placeholder: "说明需要上级协调的事项（可选）" },
    REJECT: { title: "退回项目", kicker: "RETURN", button: "确认退回", placeholder: "请说明退回原因", required: true },
    RESUBMIT: { title: "修改后重新提交", kicker: "RESUBMIT", button: "直达原审批节点", placeholder: "说明本次修改内容（可选）" },
    CONFIRM_ASSIGN: { title: "确认接收指派", kicker: "ASSIGNMENT", button: "确认接收", placeholder: "填写确认意见（可选）" }
};

document.addEventListener("DOMContentLoaded", boot);

async function boot() {
    document.querySelector("#current-date").textContent = new Intl.DateTimeFormat("zh-CN", {
        year: "numeric", month: "2-digit", day: "2-digit", weekday: "short"
    }).format(new Date());
    document.querySelector("#project-deadline").min = todayString();
    bindEvents();

    try {
        const csrf = await api("/api/csrf", { withUser: false });
        state.csrfToken = csrf.token;
        state.csrfHeaderName = csrf.headerName;
        state.users = await api("/api/users", { withUser: false });
        renderUserOptions();
        const savedUser = localStorage.getItem("forge-flow-user");
        const defaultUser = state.users.find(user => user.id === savedUser)
            || state.users.find(user => user.id === "EMP001")
            || state.users[0];
        await switchUser(defaultUser?.id, false);
    } catch (error) {
        showToast(error.message || "应用初始化失败", true);
        renderFatalState(error.message);
    }
}

function bindEvents() {
    els.userSelect.addEventListener("change", event => switchUser(event.target.value));
    document.querySelector("#refresh-button").addEventListener("click", refreshAll);
    document.querySelector("#new-project-button").addEventListener("click", event => openDialog(els.createDialog, event.currentTarget));
    els.createForm.addEventListener("submit", handleCreateProject);
    els.actionForm.addEventListener("submit", handleTaskAction);
    document.querySelector("#project-background").addEventListener("input", event => {
        document.querySelector("#background-count").textContent = `${event.target.value.length} / 1600`;
    });
    els.projectSearch.addEventListener("input", event => {
        state.search = event.target.value.trim().toLowerCase();
        renderProjects();
    });

    document.querySelectorAll(".nav-item").forEach(button => {
        button.addEventListener("click", () => showView(button.dataset.view));
    });
    document.querySelectorAll("[data-go-view]").forEach(button => {
        button.addEventListener("click", () => showView(button.dataset.goView));
    });
    document.querySelectorAll("[data-task-view]").forEach(button => {
        button.addEventListener("click", () => changeTaskView(button.dataset.taskView));
    });
    document.querySelectorAll(".dialog-close, .dialog-cancel").forEach(button => {
        button.addEventListener("click", () => button.closest("dialog").close());
    });
    document.querySelectorAll("dialog").forEach(dialog => {
        dialog.addEventListener("click", event => {
            if (event.target === dialog) dialog.close();
        });
        dialog.addEventListener("close", () => {
            const trigger = dialog._trigger;
            setTimeout(() => {
                if (trigger?.isConnected) trigger.focus();
            }, 50);
        });
    });

    els.menuButton.addEventListener("click", () => {
        const open = els.sidebar.classList.toggle("is-open");
        els.menuButton.setAttribute("aria-expanded", String(open));
        els.menuButton.setAttribute("aria-label", open ? "关闭导航" : "打开导航");
    });

    document.addEventListener("click", handleDelegatedClick);
    document.addEventListener("click", event => {
        if (!els.sidebar.classList.contains("is-open")) return;
        if (els.sidebar.contains(event.target) || els.menuButton.contains(event.target)) return;
        closeSidebar();
    });
    document.addEventListener("keydown", event => {
        if (event.key !== "Escape" || !els.sidebar.classList.contains("is-open")) return;
        closeSidebar(true);
    });
}

function closeSidebar(restoreFocus = false) {
    els.sidebar.classList.remove("is-open");
    els.menuButton.setAttribute("aria-expanded", "false");
    els.menuButton.setAttribute("aria-label", "打开导航");
    if (restoreFocus) els.menuButton.focus();
}

async function switchUser(userId, announce = true) {
    const user = state.users.find(item => item.id === userId);
    if (!user) return;
    state.currentUser = user;
    els.userSelect.value = user.id;
    localStorage.setItem("forge-flow-user", user.id);
    renderActiveUser();
    await refreshAll();
    if (announce) showToast(`已切换为 ${user.name} · ${user.roleLabel}`);
}

async function refreshAll() {
    if (!state.currentUser || state.loading) return;
    setLoading(true);
    try {
        const [dashboard, projects, tasks] = await Promise.all([
            api("/api/dashboard"),
            api("/api/projects"),
            api(`/api/tasks?view=${state.taskView}`)
        ]);
        state.dashboard = dashboard;
        state.projects = projects;
        state.tasks = tasks;
        renderAll();
    } catch (error) {
        showToast(error.message, true);
    } finally {
        setLoading(false);
    }
}

function renderAll() {
    renderMetrics();
    renderUrgentTasks();
    renderActivities();
    renderTasks();
    renderProjects();
    els.navTaskCount.textContent = state.dashboard?.todoCount ?? 0;
    els.navTaskCount.setAttribute("aria-label", `${state.dashboard?.todoCount ?? 0} 条待办`);
}

function renderUserOptions() {
    els.userSelect.innerHTML = state.users.map(user =>
        `<option value="${e(user.id)}">${e(user.name)} · ${e(user.roleLabel)}</option>`
    ).join("");
}

function renderActiveUser() {
    const user = state.currentUser;
    els.activeUserCard.innerHTML = `
        <span class="avatar" aria-hidden="true">${e(user.initials)}</span>
        <span><strong>${e(user.name)}</strong><span>${e(user.roleLabel)} · ${e(user.orgName)}</span></span>
    `;
}

function renderMetrics() {
    const dashboard = state.dashboard || {};
    const metrics = [
        ["我的待办", dashboard.todoCount ?? 0, "条", "01"],
        ["可见项目", dashboard.visibleProjectCount ?? 0, "项", "02"],
        ["审批完成", dashboard.waitingExecutionCount ?? 0, "项", "03"],
        ["待修改", dashboard.returnedCount ?? 0, "项", "04"]
    ];
    els.metricGrid.innerHTML = metrics.map(([label, value, unit, index]) => `
        <article class="metric-card" data-index="${index}">
            <span class="metric-label">${e(label)}<i aria-hidden="true"></i></span>
            <strong class="metric-value">${Number(value)}<span class="metric-unit">${unit}</span></strong>
        </article>
    `).join("");
}

function renderUrgentTasks() {
    const tasks = state.dashboard?.urgentTasks || [];
    if (!tasks.length) {
        els.urgentTaskList.innerHTML = emptyState("当前没有待处理任务", "切换演示身份，可以查看其他角色的流程任务。");
        return;
    }
    els.urgentTaskList.innerHTML = tasks.map((task, index) => `
        <article class="compact-task">
            <span class="task-node-index">${String(index + 1).padStart(2, "0")}</span>
            <div><strong>${e(task.projectTitle)}</strong><small>${e(task.nodeLabel)} · ${e(task.projectNo)}</small></div>
            <button class="mini-button" type="button" data-open-project="${e(task.projectId)}">办理</button>
        </article>
    `).join("");
}

function renderActivities() {
    const activities = state.dashboard?.recentActivities || [];
    if (!activities.length) {
        els.activityList.innerHTML = `<li class="empty-state"><div><strong>暂无流转记录</strong><p>流程操作会以追加方式出现在这里。</p></div></li>`;
        return;
    }
    els.activityList.innerHTML = activities.map(activity => `
        <li>
            <span class="activity-dot" aria-hidden="true"></span>
            <div class="activity-copy">
                <strong>${e(activity.actor)} · ${e(activity.action)}</strong>
                <small>${e(activity.projectTitle)} / ${e(activity.node)}</small>
            </div>
            <time class="activity-time" datetime="${e(activity.createdAt)}">${formatRelative(activity.createdAt)}</time>
        </li>
    `).join("");
}

function renderTasks() {
    if (!state.tasks.length) {
        els.taskGrid.innerHTML = emptyState(
            state.taskView === "todo" ? "任务队列已清空" : "还没有已处理任务",
            state.taskView === "todo" ? "当前身份没有需要处理的节点。" : "完成一次审批后，记录会保留在这里。"
        );
        return;
    }
    els.taskGrid.innerHTML = state.tasks.map(task => `
        <article class="task-card ${task.status === "OPEN" ? "" : "is-closed"}">
            <div class="task-card-head">
                <span class="tag">${e(task.nodeLabel)}</span>
                <span class="status-chip" data-status="${task.status === "OPEN" ? "APPROVING" : "DRAFT"}">${e(task.statusLabel)}</span>
            </div>
            <h3>${e(task.projectTitle)}</h3>
            <span class="project-code">${e(task.projectNo)}</span>
            <div class="task-card-meta">
                <div><span>任务类型</span><strong>${taskTypeLabel(task.taskType)}</strong></div>
                <div><span>到达时间</span><strong>${formatDateTime(task.createdAt)}</strong></div>
            </div>
            <div class="task-actions">
                <button class="action-button" type="button" data-open-project="${e(task.projectId)}">查看详情</button>
                ${task.actions.map(action => `
                    <button class="action-button" type="button" data-task-action="${e(action.value)}" data-task-id="${e(task.id)}" data-tone="${e(action.tone)}">${e(action.label)}</button>
                `).join("")}
            </div>
        </article>
    `).join("");
}

function renderProjects() {
    const projects = state.projects.filter(project => {
        if (!state.search) return true;
        return project.title.toLowerCase().includes(state.search)
            || project.projectNo.toLowerCase().includes(state.search)
            || project.initiatorName.toLowerCase().includes(state.search);
    });

    if (!projects.length) {
        els.projectTableBody.innerHTML = `<tr><td colspan="6">${emptyState("没有匹配的项目", "尝试调整搜索关键词。")}</td></tr>`;
        els.projectCardList.innerHTML = emptyState("没有匹配的项目", "尝试调整搜索关键词。");
        return;
    }

    els.projectTableBody.innerHTML = projects.map(project => `
        <tr>
            <td class="project-cell"><strong>${e(project.title)}</strong><span>${e(project.projectNo)} · ${e(project.category)}</span></td>
            <td><span class="status-chip" data-status="${e(project.status)}">${e(project.statusLabel)}</span></td>
            <td>${e(project.currentNodeLabel)}</td>
            <td>${e(project.currentHandlers)}</td>
            <td><div class="progress-track" aria-label="进度 ${project.progress}%"><span style="width:${project.progress}%"></span></div><span class="progress-label">${project.progress}%</span></td>
            <td><div class="row-actions">
                ${project.canSubmit ? `<button class="mini-button" type="button" data-submit-project="${e(project.id)}">提交</button>` : ""}
                <button class="mini-button" type="button" data-open-project="${e(project.id)}">详情</button>
            </div></td>
        </tr>
    `).join("");

    els.projectCardList.innerHTML = projects.map(project => `
        <article class="project-mobile-card">
            <span class="status-chip" data-status="${e(project.status)}">${e(project.statusLabel)}</span>
            <h3>${e(project.title)}</h3>
            <p>${e(project.projectNo)} · ${e(project.currentNodeLabel)}</p>
            <div class="project-mobile-meta"><span class="tag">${e(project.currentHandlers)}</span><span class="tag">${project.progress}%</span></div>
            <div class="project-mobile-actions">
                ${project.canSubmit ? `<button class="mini-button" type="button" data-submit-project="${e(project.id)}">提交</button>` : ""}
                <button class="mini-button" type="button" data-open-project="${e(project.id)}">查看详情</button>
            </div>
        </article>
    `).join("");
}

async function changeTaskView(view) {
    state.taskView = view;
    document.querySelectorAll("[data-task-view]").forEach(button => {
        const active = button.dataset.taskView === view;
        button.classList.toggle("is-active", active);
        button.setAttribute("aria-selected", String(active));
    });
    try {
        state.tasks = await api(`/api/tasks?view=${view}`);
        renderTasks();
    } catch (error) {
        showToast(error.message, true);
    }
}

function showView(view) {
    state.activeView = view;
    document.querySelectorAll(".view").forEach(section => {
        const active = section.id === `${view}-view`;
        section.hidden = !active;
        section.classList.toggle("is-active", active);
    });
    document.querySelectorAll(".nav-item").forEach(button => {
        const active = button.dataset.view === view;
        button.classList.toggle("is-active", active);
        if (active) button.setAttribute("aria-current", "page");
        else button.removeAttribute("aria-current");
    });
    closeSidebar();
    document.querySelector(`#${view}-view h1`)?.focus?.();
    window.scrollTo({ top: 0, behavior: "smooth" });
}

async function handleDelegatedClick(event) {
    const openProject = event.target.closest("[data-open-project]");
    if (openProject) {
        await openProjectDetail(openProject.dataset.openProject, openProject);
        return;
    }
    const submitProject = event.target.closest("[data-submit-project]");
    if (submitProject) {
        await submitDraft(submitProject.dataset.submitProject, submitProject);
        return;
    }
    const actionButton = event.target.closest("[data-task-action]");
    if (actionButton) {
        openTaskAction(actionButton.dataset.taskId, actionButton.dataset.taskAction, actionButton);
    }
}

async function handleCreateProject(event) {
    event.preventDefault();
    clearFormErrors(els.createForm);
    const formData = new FormData(els.createForm);
    const title = formData.get("title")?.trim();
    const category = formData.get("category")?.trim();
    const background = formData.get("background")?.trim();
    const errors = [];
    if (!title) errors.push(["project-title", "请填写攻关课题名称"]);
    if (!category) errors.push(["project-category", "请选择攻关分类"]);
    if (!background) errors.push(["project-background", "请填写问题背景"]);
    if (errors.length) {
        errors.forEach(([id, message]) => setFieldError(id, message));
        document.querySelector(`#${errors[0][0]}`).focus();
        return;
    }

    const submitButton = event.submitter;
    setButtonBusy(submitButton, true, "保存中…");
    try {
        await api("/api/projects", {
            method: "POST",
            body: JSON.stringify({
                title,
                category,
                background,
                flowType: formData.get("flowType"),
                completeDeadline: formData.get("completeDeadline") || null
            })
        });
        els.createDialog.close();
        els.createForm.reset();
        document.querySelector("#background-count").textContent = "0 / 1600";
        await refreshAll();
        showView("projects");
        showToast("项目草稿已保存，可在项目台账中提交");
    } catch (error) {
        showToast(error.message, true);
    } finally {
        setButtonBusy(submitButton, false);
    }
}

async function submitDraft(projectId, button) {
    setButtonBusy(button, true, "提交中…");
    try {
        await api(`/api/projects/${projectId}/submit`, { method: "POST" });
        await refreshAll();
        showToast("项目已进入审批流程");
    } catch (error) {
        showToast(error.message, true);
    } finally {
        setButtonBusy(button, false);
    }
}

function openTaskAction(taskId, action, trigger) {
    const task = findTask(taskId);
    if (!task) {
        showToast("任务信息已刷新，请重新打开", true);
        refreshAll();
        return;
    }
    const copy = ACTION_COPY[action];
    state.activeTask = task;
    state.activeAction = action;
    document.querySelector("#action-dialog-kicker").textContent = copy.kicker;
    document.querySelector("#action-dialog-title").textContent = copy.title;
    document.querySelector("#confirm-action-button").textContent = copy.button;
    document.querySelector("#task-comment").placeholder = copy.placeholder;
    document.querySelector("#task-comment").value = "";
    document.querySelector("#comment-required").textContent = copy.required ? "*" : "";
    document.querySelector("#decision-context").innerHTML = `
        <span>${e(task.projectNo)} / ${e(task.nodeLabel)}</span>
        <strong>${e(task.projectTitle)}</strong>
    `;

    const returnField = document.querySelector("#return-target-field");
    returnField.hidden = action !== "REJECT";
    const returnSelect = document.querySelector("#return-target");
    returnSelect.innerHTML = task.returnableNodes.map(node =>
        `<option value="${e(node.value)}">${e(node.label)}</option>`
    ).join("");
    clearFormErrors(els.actionForm);
    openDialog(els.actionDialog, trigger);
}

async function handleTaskAction(event) {
    event.preventDefault();
    const comment = document.querySelector("#task-comment").value.trim();
    if (state.activeAction === "REJECT" && !comment) {
        setFieldError("task-comment", "退回时必须填写原因");
        document.querySelector("#task-comment").focus();
        return;
    }
    const button = event.submitter;
    setButtonBusy(button, true, "处理中…");
    try {
        await api(`/api/tasks/${state.activeTask.id}/complete`, {
            method: "POST",
            body: JSON.stringify({
                action: state.activeAction,
                targetNode: state.activeAction === "REJECT" ? document.querySelector("#return-target").value : null,
                comment: comment || null,
                operationId: crypto.randomUUID()
            })
        });
        els.actionDialog.close();
        await refreshAll();
        showToast(`${ACTION_COPY[state.activeAction].title}已完成`);
    } catch (error) {
        showToast(error.message, true);
    } finally {
        setButtonBusy(button, false);
    }
}

async function openProjectDetail(projectId, trigger) {
    try {
        els.detailContent.innerHTML = `<div class="empty-state"><div><strong>正在读取流程实例…</strong></div></div>`;
        openDialog(els.detailDialog, trigger);
        const detail = await api(`/api/projects/${projectId}`);
        renderProjectDetail(detail);
    } catch (error) {
        els.detailDialog.close();
        showToast(error.message, true);
    }
}

function renderProjectDetail(detail) {
    const project = detail.project;
    document.querySelector("#detail-dialog-title").textContent = project.title;
    els.detailContent.innerHTML = `
        <section class="detail-hero">
            <div>
                <span class="status-chip" data-status="${e(project.status)}">${e(project.statusLabel)}</span>
                <h3>${e(project.title)}</h3>
                <p>${e(project.projectNo)} · ${e(project.flowTypeLabel)} · ${e(project.initiatorOrgName)}</p>
            </div>
            <div class="detail-version"><strong>v${detail.definitionVersion}</strong><span>DEFINITION · REV ${detail.revision}</span></div>
        </section>
        <div class="detail-grid">
            <div class="detail-stat"><span>当前节点</span><strong>${e(project.currentNodeLabel)}</strong></div>
            <div class="detail-stat"><span>当前处理人</span><strong>${e(project.currentHandlers)}</strong></div>
            <div class="detail-stat"><span>填报人</span><strong>${e(project.initiatorName)}</strong></div>
            <div class="detail-stat"><span>计划时限</span><strong>${project.completeDeadline ? formatDate(project.completeDeadline) : "未设置"}</strong></div>
        </div>
        <section class="detail-section">
            <h3>问题背景</h3>
            <p class="detail-background">${e(detail.background)}</p>
        </section>
        <section class="detail-section">
            <h3>审批时间线 · ${detail.records.length} 条</h3>
            <ol class="timeline">
                ${detail.records.map(record => `
                    <li>
                        <span class="timeline-index">${String(record.sequence).padStart(2, "0")}</span>
                        <div class="timeline-copy">
                            <strong>${e(record.operatorName)} · ${e(record.action)}</strong>
                            <span>${e(record.fromNode)} → ${e(record.toNode)}<br>${e(record.comment)}</span>
                        </div>
                        <time datetime="${e(record.createdAt)}">${formatDateTime(record.createdAt)}</time>
                    </li>
                `).join("")}
            </ol>
        </section>
    `;
}

function findTask(taskId) {
    return state.tasks.find(task => task.id === taskId)
        || state.dashboard?.urgentTasks?.find(task => task.id === taskId);
}

function openDialog(dialog, trigger) {
    dialog._trigger = trigger || document.activeElement;
    if (!dialog.open) dialog.showModal();
    setTimeout(() => {
        const candidates = [...dialog.querySelectorAll("input, select, textarea, button:not(.dialog-close)")];
        const first = candidates.find(element => !element.closest("[hidden]") && element.getClientRects().length > 0);
        if (first) {
            first.focus();
        } else {
            dialog.setAttribute("tabindex", "-1");
            dialog.focus();
        }
    }, 0);
}

async function api(path, options = {}) {
    const { withUser = true, ...fetchOptions } = options;
    const headers = new Headers(fetchOptions.headers || {});
    const method = (fetchOptions.method || "GET").toUpperCase();
    if (fetchOptions.body) headers.set("Content-Type", "application/json");
    if (withUser && state.currentUser) headers.set("X-Demo-User", state.currentUser.id);
    if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(method) && state.csrfToken) {
        headers.set(state.csrfHeaderName, state.csrfToken);
    }
    const response = await fetch(path, { ...fetchOptions, headers });
    if (response.status === 401) {
        window.location.assign("/login.html?expired");
        throw new Error("登录状态已过期");
    }
    const payload = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) {
        throw new Error(payload?.message || `请求失败（${response.status}）`);
    }
    return payload;
}

function setLoading(loading) {
    state.loading = loading;
    const button = document.querySelector("#refresh-button");
    button.disabled = loading;
    button.querySelector(".refresh-icon").textContent = loading ? "…" : "↻";
}

function setButtonBusy(button, busy, busyText = "处理中…") {
    if (!button) return;
    if (busy) {
        button.dataset.originalText = button.textContent;
        button.textContent = busyText;
        button.disabled = true;
    } else {
        button.textContent = button.dataset.originalText || button.textContent;
        button.disabled = false;
    }
}

function clearFormErrors(form) {
    form.querySelectorAll("[aria-invalid='true']").forEach(field => {
        field.removeAttribute("aria-invalid");
        field.removeAttribute("aria-describedby");
    });
    form.querySelectorAll(".field-error").forEach(error => error.textContent = "");
}

function setFieldError(fieldId, message) {
    const field = document.querySelector(`#${fieldId}`);
    const error = document.querySelector(`#${fieldId}-error`);
    field.setAttribute("aria-invalid", "true");
    if (error) {
        error.textContent = message;
        field.setAttribute("aria-describedby", error.id);
    }
}

let toastTimer;
function showToast(message, error = false) {
    clearTimeout(toastTimer);
    els.toast.textContent = message;
    els.toast.classList.toggle("is-error", error);
    els.toast.classList.add("is-visible");
    toastTimer = setTimeout(() => els.toast.classList.remove("is-visible"), error ? 6000 : 3600);
}

function renderFatalState(message) {
    document.querySelector("#main-content").innerHTML = `
        <div class="empty-state"><div><strong>应用暂时无法启动</strong><p>${e(message || "请确认后端服务已启动。")}</p></div></div>
    `;
}

function emptyState(title, description) {
    return `<div class="empty-state"><div><strong>${e(title)}</strong><p>${e(description)}</p></div></div>`;
}

function taskTypeLabel(type) {
    return ({ APPROVAL: "审批任务", CORRECTION: "修改任务", ASSIGN_CONFIRM: "指派确认" })[type] || type;
}

function formatDate(value) {
    if (!value) return "—";
    const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
    return new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
}

function formatDateTime(value) {
    if (!value) return "—";
    return new Intl.DateTimeFormat("zh-CN", {
        month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false
    }).format(new Date(value));
}

function formatRelative(value) {
    const deltaMinutes = Math.max(0, Math.round((Date.now() - new Date(value).getTime()) / 60000));
    if (deltaMinutes < 1) return "刚刚";
    if (deltaMinutes < 60) return `${deltaMinutes} 分钟前`;
    if (deltaMinutes < 1440) return `${Math.floor(deltaMinutes / 60)} 小时前`;
    return formatDate(value);
}

function todayString() {
    const date = new Date();
    const offset = date.getTimezoneOffset() * 60000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 10);
}

function e(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
