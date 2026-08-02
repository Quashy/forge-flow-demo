package cn.quashy.forgeflow.service;

import cn.quashy.forgeflow.domain.ApprovalRecord;
import cn.quashy.forgeflow.domain.ApprovalTask;
import cn.quashy.forgeflow.domain.DemoUser;
import cn.quashy.forgeflow.domain.FlowType;
import cn.quashy.forgeflow.domain.NodeKey;
import cn.quashy.forgeflow.domain.Project;
import cn.quashy.forgeflow.domain.ProjectStatus;
import cn.quashy.forgeflow.domain.TaskStatus;
import cn.quashy.forgeflow.domain.TaskType;
import cn.quashy.forgeflow.domain.UserRole;
import cn.quashy.forgeflow.domain.WorkflowAction;
import cn.quashy.forgeflow.domain.WorkflowInstance;
import cn.quashy.forgeflow.repository.ApprovalRecordRepository;
import cn.quashy.forgeflow.repository.ApprovalTaskRepository;
import cn.quashy.forgeflow.repository.DemoUserRepository;
import cn.quashy.forgeflow.repository.ProjectRepository;
import cn.quashy.forgeflow.repository.WorkflowInstanceRepository;
import cn.quashy.forgeflow.web.ApiModels.ActionView;
import cn.quashy.forgeflow.web.ApiModels.ActivityView;
import cn.quashy.forgeflow.web.ApiModels.CompleteTaskRequest;
import cn.quashy.forgeflow.web.ApiModels.CreateProjectRequest;
import cn.quashy.forgeflow.web.ApiModels.DashboardView;
import cn.quashy.forgeflow.web.ApiModels.ProjectDetail;
import cn.quashy.forgeflow.web.ApiModels.ProjectSummary;
import cn.quashy.forgeflow.web.ApiModels.RecordView;
import cn.quashy.forgeflow.web.ApiModels.ReturnNodeView;
import cn.quashy.forgeflow.web.ApiModels.TaskView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkflowService {

    private final ProjectRepository projectRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final ApprovalTaskRepository taskRepository;
    private final ApprovalRecordRepository recordRepository;
    private final DemoUserRepository userRepository;

    public WorkflowService(ProjectRepository projectRepository,
                           WorkflowInstanceRepository instanceRepository,
                           ApprovalTaskRepository taskRepository,
                           ApprovalRecordRepository recordRepository,
                           DemoUserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.recordRepository = recordRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public String createProject(DemoUser actor, CreateProjectRequest request) {
        String projectNo = generateProjectNo();
        Project project = new Project(
            projectNo,
            request.title().trim(),
            request.category().trim(),
            request.background().trim(),
            actor,
            request.flowType(),
            request.completeDeadline()
        );
        WorkflowInstance instance = new WorkflowInstance(project.getId(), request.flowType());
        project.bindWorkflow(instance.getId());

        projectRepository.save(project);
        instanceRepository.save(instance);
        appendRecord(instance, project, NodeKey.DRAFT, WorkflowAction.CREATE, actor,
            NodeKey.DRAFT, NodeKey.DRAFT, "保存项目草稿", "create-" + UUID.randomUUID());
        return project.getId();
    }

    @Transactional
    public void submitProject(String projectId, DemoUser actor) {
        Project project = requireProject(projectId);
        WorkflowInstance instance = requireInstance(projectId);

        if (!project.getInitiatorId().equals(actor.getId())) {
            throw forbidden("只有填报人可以提交该项目");
        }
        if (instance.getCurrentNode() != NodeKey.DRAFT || project.getStatus() != ProjectStatus.DRAFT) {
            throw conflict("项目当前状态不可提交");
        }

        NodeKey target = project.getFlowType() == FlowType.ASSIGNMENT
            ? NodeKey.ASSIGN_CONFIRM
            : NodeKey.AREA_REVIEW;
        TaskType taskType = target == NodeKey.ASSIGN_CONFIRM ? TaskType.ASSIGN_CONFIRM : TaskType.APPROVAL;
        move(instance, project, actor, WorkflowAction.SUBMIT, target, taskType,
            "项目已提交，等待" + target.getLabel(), "submit-" + project.getId() + "-" + project.getVersion());
    }

    @Transactional
    public String completeTask(String taskId, DemoUser actor, CompleteTaskRequest request) {
        var existingRecord = recordRepository.findByOperationId(request.operationId());
        if (existingRecord.isPresent()) {
            return existingRecord.get().getProjectId();
        }

        ApprovalTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> notFound("待办任务不存在"));
        if (!task.getAssigneeId().equals(actor.getId())) {
            throw forbidden("该任务不属于当前演示身份");
        }
        if (task.getStatus() != TaskStatus.OPEN) {
            throw conflict("任务已被处理或已失效");
        }

        Project project = requireProject(task.getProjectId());
        WorkflowInstance instance = requireInstance(task.getProjectId());
        if (instance.getCurrentNode() != task.getNodeKey()) {
            throw conflict("流程已经进入其他节点，请刷新后重试");
        }

        validateAction(task, instance, request);
        String comment = normalizeComment(request.comment());
        Instant completedAt = Instant.now();
        int updated = taskRepository.completeIfOpen(
            taskId, actor.getId(), request.action(), comment, completedAt,
            TaskStatus.OPEN, TaskStatus.COMPLETED
        );
        if (updated == 0) {
            throw conflict("任务已被其他审批人处理");
        }
        taskRepository.cancelOpenSiblings(
            task.getTaskGroupId(), taskId, completedAt, TaskStatus.OPEN, TaskStatus.CANCELLED
        );

        NodeKey fromNode = instance.getCurrentNode();
        NodeKey targetNode;
        TaskType nextTaskType = TaskType.APPROVAL;

        switch (request.action()) {
            case APPROVE -> targetNode = NodeKey.WAITING_EXECUTION;
            case UPPER_HELP -> targetNode = nextUpperNode(fromNode);
            case REJECT -> {
                targetNode = request.targetNode();
                nextTaskType = TaskType.CORRECTION;
                instance.markReturned(fromNode, targetNode);
            }
            case RESUBMIT -> {
                targetNode = instance.consumeResumeNode();
                if (targetNode == null) {
                    throw conflict("缺少退回恢复节点，无法重新提交");
                }
                nextTaskType = targetNode == NodeKey.ASSIGN_CONFIRM
                    ? TaskType.ASSIGN_CONFIRM
                    : TaskType.APPROVAL;
            }
            case CONFIRM_ASSIGN -> targetNode = NodeKey.WAITING_EXECUTION;
            default -> throw badRequest("当前任务不支持该处理动作");
        }

        if (request.action() != WorkflowAction.REJECT) {
            instance.moveTo(targetNode);
        }
        applyProjectState(project, instance, targetNode, nextTaskType);
        appendRecord(instance, project, fromNode, request.action(), actor, fromNode, targetNode,
            comment, request.operationId());

        try {
            instanceRepository.saveAndFlush(instance);
            projectRepository.saveAndFlush(project);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw conflict("该流程刚刚被其他审批人处理，请刷新后重试");
        } catch (DataIntegrityViolationException ex) {
            throw conflict("重复请求已被系统拦截");
        }
        return project.getId();
    }

    @Transactional(readOnly = true)
    public DashboardView getDashboard(DemoUser actor) {
        List<Project> visibleProjects = visibleProjects(actor);
        List<TaskView> urgentTasks = taskRepository
            .findByAssigneeIdAndStatusOrderByCreatedAtDesc(actor.getId(), TaskStatus.OPEN)
            .stream()
            .limit(4)
            .map(this::toTaskView)
            .toList();

        List<ActivityView> activities = recordRepository.findTop8ByOrderByCreatedAtDesc().stream()
            .map(record -> projectRepository.findById(record.getProjectId())
                .map(project -> new ActivityView(
                    project.getId(), project.getProjectNo(), project.getTitle(),
                    record.getOperatorName(), record.getAction().getLabel(),
                    record.getNodeKey().getLabel(), record.getCreatedAt()
                ))
                .orElse(null))
            .filter(item -> item != null)
            .limit(6)
            .toList();

        return new DashboardView(
            taskRepository.countByAssigneeIdAndStatus(actor.getId(), TaskStatus.OPEN),
            visibleProjects.size(),
            visibleProjects.stream().filter(p -> p.getStatus() == ProjectStatus.WAITING_EXECUTION).count(),
            visibleProjects.stream().filter(p -> p.getStatus() == ProjectStatus.RETURNED).count(),
            urgentTasks,
            activities
        );
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> listProjects(DemoUser actor) {
        return visibleProjects(actor).stream().map(project -> toProjectSummary(project, actor)).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetail getProjectDetail(String projectId, DemoUser actor) {
        Project project = requireProject(projectId);
        if (!isVisible(project, actor)) {
            throw forbidden("当前身份无权查看该项目");
        }
        WorkflowInstance instance = requireInstance(projectId);
        List<RecordView> records = recordRepository.findByProjectIdOrderByEventSequenceAsc(projectId).stream()
            .map(record -> new RecordView(
                record.getEventSequence(), record.getOperatorName(), record.getAction().getLabel(),
                record.getNodeKey().getLabel(), record.getFromNode().getLabel(),
                record.getToNode().getLabel(), record.getCommentText(), record.getCreatedAt()
            ))
            .toList();
        List<TaskView> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .map(this::toTaskView)
            .toList();
        return new ProjectDetail(
            toProjectSummary(project, actor), project.getBackground(), instance.getDefinitionVersion(),
            instance.getRevision(), records, tasks
        );
    }

    @Transactional(readOnly = true)
    public List<TaskView> listTasks(DemoUser actor, boolean completed) {
        List<ApprovalTask> tasks = completed
            ? taskRepository.findByAssigneeIdAndStatusNotOrderByCreatedAtDesc(actor.getId(), TaskStatus.OPEN)
            : taskRepository.findByAssigneeIdAndStatusOrderByCreatedAtDesc(actor.getId(), TaskStatus.OPEN);
        return tasks.stream().map(this::toTaskView).toList();
    }

    private void move(WorkflowInstance instance, Project project, DemoUser actor, WorkflowAction action,
                      NodeKey target, TaskType taskType, String comment, String operationId) {
        NodeKey from = instance.getCurrentNode();
        instance.moveTo(target);
        applyProjectState(project, instance, target, taskType);
        appendRecord(instance, project, from, action, actor, from, target, comment, operationId);
        instanceRepository.save(instance);
        projectRepository.save(project);
    }

    private void applyProjectState(Project project, WorkflowInstance instance,
                                   NodeKey target, TaskType taskType) {
        if (target == NodeKey.WAITING_EXECUTION) {
            project.moveTo(ProjectStatus.WAITING_EXECUTION, target, "—");
            return;
        }

        List<DemoUser> assignees = resolveAssignees(project, target);
        if (assignees.isEmpty()) {
            throw conflict("节点“" + target.getLabel() + "”未配置处理人");
        }
        String taskGroupId = UUID.randomUUID().toString();
        List<ApprovalTask> tasks = assignees.stream()
            .map(user -> new ApprovalTask(instance.getId(), project.getId(), target, taskType, taskGroupId, user))
            .toList();
        taskRepository.saveAll(tasks);
        String handlerNames = String.join("、", assignees.stream().map(DemoUser::getName).toList());
        ProjectStatus status = taskType == TaskType.CORRECTION
            ? ProjectStatus.RETURNED
            : ProjectStatus.APPROVING;
        project.moveTo(status, target, handlerNames);
    }

    private List<DemoUser> resolveAssignees(Project project, NodeKey node) {
        return switch (node) {
            case APPLICANT_EDIT -> userRepository.findById(project.getInitiatorId()).stream().toList();
            case AREA_REVIEW, ASSIGN_CONFIRM -> userRepository.findByRoleOrderByName(UserRole.AREA_SAFETY);
            case DEPARTMENT_REVIEW -> userRepository.findByRoleOrderByName(UserRole.DEPARTMENT_SAFETY);
            case COMPANY_REVIEW -> userRepository.findByRoleOrderByName(UserRole.COMPANY_SAFETY);
            default -> List.of();
        };
    }

    private void validateAction(ApprovalTask task, WorkflowInstance instance, CompleteTaskRequest request) {
        Set<WorkflowAction> allowed = allowedActions(task, instance.getCurrentNode());
        if (!allowed.contains(request.action())) {
            throw badRequest("当前节点不支持“" + request.action().getLabel() + "”操作");
        }
        if (request.action() == WorkflowAction.REJECT) {
            if (request.targetNode() == null || !returnableNodes(instance.getCurrentNode()).contains(request.targetNode())) {
                throw badRequest("请选择有效的退回节点");
            }
            if (request.comment() == null || request.comment().isBlank()) {
                throw badRequest("退回时必须填写原因");
            }
        }
    }

    private Set<WorkflowAction> allowedActions(ApprovalTask task, NodeKey currentNode) {
        if (task.getStatus() != TaskStatus.OPEN) {
            return Set.of();
        }
        return switch (task.getTaskType()) {
            case CORRECTION -> Set.of(WorkflowAction.RESUBMIT);
            case ASSIGN_CONFIRM -> Set.of(WorkflowAction.CONFIRM_ASSIGN, WorkflowAction.REJECT);
            case APPROVAL -> {
                Set<WorkflowAction> actions = new LinkedHashSet<>();
                actions.add(WorkflowAction.APPROVE);
                if (currentNode == NodeKey.AREA_REVIEW || currentNode == NodeKey.DEPARTMENT_REVIEW) {
                    actions.add(WorkflowAction.UPPER_HELP);
                }
                actions.add(WorkflowAction.REJECT);
                yield actions;
            }
        };
    }

    private List<NodeKey> returnableNodes(NodeKey currentNode) {
        return switch (currentNode) {
            case AREA_REVIEW, ASSIGN_CONFIRM -> List.of(NodeKey.APPLICANT_EDIT);
            case DEPARTMENT_REVIEW -> List.of(NodeKey.APPLICANT_EDIT, NodeKey.AREA_REVIEW);
            case COMPANY_REVIEW -> List.of(NodeKey.APPLICANT_EDIT, NodeKey.AREA_REVIEW, NodeKey.DEPARTMENT_REVIEW);
            default -> List.of();
        };
    }

    private NodeKey nextUpperNode(NodeKey node) {
        return switch (node) {
            case AREA_REVIEW -> NodeKey.DEPARTMENT_REVIEW;
            case DEPARTMENT_REVIEW -> NodeKey.COMPANY_REVIEW;
            default -> throw badRequest("当前节点已经是最高审批层级");
        };
    }

    private void appendRecord(WorkflowInstance instance, Project project, NodeKey nodeKey,
                              WorkflowAction action, DemoUser actor, NodeKey fromNode, NodeKey toNode,
                              String comment, String operationId) {
        recordRepository.save(new ApprovalRecord(
            instance.getId(), project.getId(), instance.nextEventSequence(), nodeKey,
            action, actor, fromNode, toNode, normalizeComment(comment), operationId
        ));
    }

    private TaskView toTaskView(ApprovalTask task) {
        Project project = requireProject(task.getProjectId());
        WorkflowInstance instance = requireInstance(task.getProjectId());
        List<ActionView> actions = allowedActions(task, instance.getCurrentNode()).stream()
            .map(action -> new ActionView(action, action.getLabel(), actionTone(action)))
            .toList();
        List<ReturnNodeView> returnNodes = actions.stream().anyMatch(a -> a.value() == WorkflowAction.REJECT)
            ? returnableNodes(instance.getCurrentNode()).stream()
                .map(node -> new ReturnNodeView(node, node.getLabel()))
                .toList()
            : List.of();
        return new TaskView(
            task.getId(), project.getId(), project.getProjectNo(), project.getTitle(),
            task.getNodeKey(), task.getNodeKey().getLabel(), task.getTaskType().name(),
            task.getStatus(), task.getStatus().getLabel(), task.getAssigneeName(),
            actions, returnNodes, task.getCreatedAt(), task.getCompletedAt()
        );
    }

    private ProjectSummary toProjectSummary(Project project, DemoUser actor) {
        return new ProjectSummary(
            project.getId(), project.getProjectNo(), project.getTitle(), project.getCategory(),
            project.getInitiatorName(), project.getInitiatorOrgName(), project.getFlowType(),
            project.getFlowType().getLabel(), project.getStatus(), project.getStatus().getLabel(),
            project.getCurrentNode(), project.getCurrentNode().getLabel(), project.getCurrentHandlers(),
            project.getCompleteDeadline(), progress(project),
            project.getStatus() == ProjectStatus.DRAFT && project.getInitiatorId().equals(actor.getId()),
            project.getUpdatedAt()
        );
    }

    private List<Project> visibleProjects(DemoUser actor) {
        return projectRepository.findAllByOrderByUpdatedAtDesc().stream()
            .filter(project -> isVisible(project, actor))
            .toList();
    }

    private boolean isVisible(Project project, DemoUser actor) {
        return project.getStatus() != ProjectStatus.DRAFT || project.getInitiatorId().equals(actor.getId());
    }

    private int progress(Project project) {
        if (project.getStatus() == ProjectStatus.RETURNED) {
            return 45;
        }
        return switch (project.getCurrentNode()) {
            case DRAFT -> 10;
            case APPLICANT_EDIT -> 28;
            case AREA_REVIEW -> 34;
            case ASSIGN_CONFIRM -> 48;
            case DEPARTMENT_REVIEW -> 58;
            case COMPANY_REVIEW -> 78;
            case WAITING_EXECUTION -> 100;
        };
    }

    private String generateProjectNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int attempt = 0; attempt < 8; attempt++) {
            String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
            String number = "GFLOW-" + date + "-" + suffix;
            if (!projectRepository.existsByProjectNo(number)) {
                return number;
            }
        }
        throw conflict("项目编号生成失败，请稍后重试");
    }

    private String actionTone(WorkflowAction action) {
        return switch (action) {
            case APPROVE, CONFIRM_ASSIGN, RESUBMIT -> "positive";
            case UPPER_HELP -> "accent";
            case REJECT -> "danger";
            default -> "neutral";
        };
    }

    private Project requireProject(String projectId) {
        return projectRepository.findById(projectId).orElseThrow(() -> notFound("项目不存在"));
    }

    private WorkflowInstance requireInstance(String projectId) {
        return instanceRepository.findByProjectId(projectId)
            .orElseThrow(() -> notFound("流程实例不存在"));
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return "—";
        }
        return comment.trim();
    }

    private DemoException badRequest(String message) {
        return new DemoException(HttpStatus.BAD_REQUEST, "INVALID_OPERATION", message);
    }

    private DemoException forbidden(String message) {
        return new DemoException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private DemoException conflict(String message) {
        return new DemoException(HttpStatus.CONFLICT, "WORKFLOW_CONFLICT", message);
    }

    private DemoException notFound(String message) {
        return new DemoException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
