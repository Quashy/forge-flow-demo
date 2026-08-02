package cn.quashy.forgeflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_instance")
public class WorkflowInstance {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 36)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FlowType flowType;

    @Column(nullable = false)
    private int definitionVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NodeKey currentNode;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private NodeKey resumeNode;

    @Column(nullable = false)
    private long eventSequence;

    @Version
    private long revision;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected WorkflowInstance() {
    }

    public WorkflowInstance(String projectId, FlowType flowType) {
        this.id = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.flowType = flowType;
        this.definitionVersion = 1;
        this.currentNode = NodeKey.DRAFT;
        this.eventSequence = 0;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public FlowType getFlowType() { return flowType; }
    public int getDefinitionVersion() { return definitionVersion; }
    public NodeKey getCurrentNode() { return currentNode; }
    public NodeKey getResumeNode() { return resumeNode; }
    public long getEventSequence() { return eventSequence; }
    public long getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public long nextEventSequence() {
        return ++eventSequence;
    }

    public void moveTo(NodeKey node) {
        this.currentNode = node;
    }

    public void markReturned(NodeKey rejectingNode, NodeKey targetNode) {
        this.resumeNode = rejectingNode;
        this.currentNode = targetNode;
    }

    public NodeKey consumeResumeNode() {
        NodeKey node = resumeNode;
        resumeNode = null;
        return node;
    }
}
