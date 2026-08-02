package cn.quashy.forgeflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_task")
public class ApprovalTask {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String instanceId;

    @Column(nullable = false, length = 36)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NodeKey nodeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TaskType taskType;

    @Column(nullable = false, length = 36)
    private String taskGroupId;

    @Column(nullable = false, length = 32)
    private String assigneeId;

    @Column(nullable = false, length = 64)
    private String assigneeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private WorkflowAction decision;

    @Column(length = 800)
    private String commentText;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;

    @Version
    private long revision;

    protected ApprovalTask() {
    }

    public ApprovalTask(String instanceId, String projectId, NodeKey nodeKey, TaskType taskType,
                        String taskGroupId, DemoUser assignee) {
        this.id = UUID.randomUUID().toString();
        this.instanceId = instanceId;
        this.projectId = projectId;
        this.nodeKey = nodeKey;
        this.taskType = taskType;
        this.taskGroupId = taskGroupId;
        this.assigneeId = assignee.getId();
        this.assigneeName = assignee.getName();
        this.status = TaskStatus.OPEN;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getInstanceId() { return instanceId; }
    public String getProjectId() { return projectId; }
    public NodeKey getNodeKey() { return nodeKey; }
    public TaskType getTaskType() { return taskType; }
    public String getTaskGroupId() { return taskGroupId; }
    public String getAssigneeId() { return assigneeId; }
    public String getAssigneeName() { return assigneeName; }
    public TaskStatus getStatus() { return status; }
    public WorkflowAction getDecision() { return decision; }
    public String getCommentText() { return commentText; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getRevision() { return revision; }
}
