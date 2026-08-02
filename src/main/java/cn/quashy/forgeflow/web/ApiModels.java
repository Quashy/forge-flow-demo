package cn.quashy.forgeflow.web;

import cn.quashy.forgeflow.domain.FlowType;
import cn.quashy.forgeflow.domain.NodeKey;
import cn.quashy.forgeflow.domain.ProjectStatus;
import cn.quashy.forgeflow.domain.TaskStatus;
import cn.quashy.forgeflow.domain.WorkflowAction;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApiModels {

    private ApiModels() {
    }

    public record CreateProjectRequest(
        @NotBlank(message = "请填写攻关课题名称")
        @Size(max = 160, message = "课题名称不能超过160个字符")
        String title,

        @NotBlank(message = "请选择攻关分类")
        String category,

        @NotBlank(message = "请填写问题背景")
        @Size(max = 1600, message = "问题背景不能超过1600个字符")
        String background,

        @NotNull(message = "请选择流程方式")
        FlowType flowType,

        @FutureOrPresent(message = "完成时限不能早于今天")
        LocalDate completeDeadline
    ) {
    }

    public record CompleteTaskRequest(
        @NotNull(message = "请选择处理动作")
        WorkflowAction action,
        NodeKey targetNode,
        @Size(max = 800, message = "处理意见不能超过800个字符")
        String comment,
        @NotBlank(message = "缺少请求幂等标识")
        String operationId
    ) {
    }

    public record UserView(
        String id,
        String name,
        String role,
        String roleLabel,
        String orgName,
        String initials
    ) {
    }

    public record DashboardView(
        long todoCount,
        long visibleProjectCount,
        long waitingExecutionCount,
        long returnedCount,
        List<TaskView> urgentTasks,
        List<ActivityView> recentActivities
    ) {
    }

    public record ActivityView(
        String projectId,
        String projectNo,
        String projectTitle,
        String actor,
        String action,
        String node,
        Instant createdAt
    ) {
    }

    public record ProjectSummary(
        String id,
        String projectNo,
        String title,
        String category,
        String initiatorName,
        String initiatorOrgName,
        FlowType flowType,
        String flowTypeLabel,
        ProjectStatus status,
        String statusLabel,
        NodeKey currentNode,
        String currentNodeLabel,
        String currentHandlers,
        LocalDate completeDeadline,
        int progress,
        boolean canSubmit,
        Instant updatedAt
    ) {
    }

    public record ProjectDetail(
        ProjectSummary project,
        String background,
        int definitionVersion,
        long revision,
        List<RecordView> records,
        List<TaskView> tasks
    ) {
    }

    public record TaskView(
        String id,
        String projectId,
        String projectNo,
        String projectTitle,
        NodeKey nodeKey,
        String nodeLabel,
        String taskType,
        TaskStatus status,
        String statusLabel,
        String assigneeName,
        List<ActionView> actions,
        List<ReturnNodeView> returnableNodes,
        Instant createdAt,
        Instant completedAt
    ) {
    }

    public record ActionView(WorkflowAction value, String label, String tone) {
    }

    public record ReturnNodeView(NodeKey value, String label) {
    }

    public record RecordView(
        long sequence,
        String operatorName,
        String action,
        String node,
        String fromNode,
        String toNode,
        String comment,
        Instant createdAt
    ) {
    }

    public record ApiError(String code, String message, Instant timestamp) {
    }
}
