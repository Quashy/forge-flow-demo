package cn.quashy.forgeflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_record")
public class ApprovalRecord {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String instanceId;

    @Column(nullable = false, length = 36)
    private String projectId;

    @Column(nullable = false)
    private long eventSequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NodeKey nodeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkflowAction action;

    @Column(nullable = false, length = 32)
    private String operatorId;

    @Column(nullable = false, length = 64)
    private String operatorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NodeKey fromNode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NodeKey toNode;

    @Column(length = 800)
    private String commentText;

    @Column(nullable = false, unique = true, length = 72)
    private String operationId;

    @Column(nullable = false)
    private Instant createdAt;

    protected ApprovalRecord() {
    }

    public ApprovalRecord(String instanceId, String projectId, long eventSequence, NodeKey nodeKey,
                          WorkflowAction action, DemoUser operator, NodeKey fromNode, NodeKey toNode,
                          String commentText, String operationId) {
        this.id = UUID.randomUUID().toString();
        this.instanceId = instanceId;
        this.projectId = projectId;
        this.eventSequence = eventSequence;
        this.nodeKey = nodeKey;
        this.action = action;
        this.operatorId = operator.getId();
        this.operatorName = operator.getName();
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.commentText = commentText;
        this.operationId = operationId;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getInstanceId() { return instanceId; }
    public String getProjectId() { return projectId; }
    public long getEventSequence() { return eventSequence; }
    public NodeKey getNodeKey() { return nodeKey; }
    public WorkflowAction getAction() { return action; }
    public String getOperatorId() { return operatorId; }
    public String getOperatorName() { return operatorName; }
    public NodeKey getFromNode() { return fromNode; }
    public NodeKey getToNode() { return toNode; }
    public String getCommentText() { return commentText; }
    public String getOperationId() { return operationId; }
    public Instant getCreatedAt() { return createdAt; }
}
