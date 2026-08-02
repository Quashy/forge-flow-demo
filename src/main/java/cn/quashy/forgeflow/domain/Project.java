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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "research_project")
public class Project {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 40)
    private String projectNo;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 48)
    private String category;

    @Column(nullable = false, length = 1600)
    private String background;

    @Column(nullable = false, length = 32)
    private String initiatorId;

    @Column(nullable = false, length = 64)
    private String initiatorName;

    @Column(nullable = false, length = 96)
    private String initiatorOrgName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FlowType flowType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NodeKey currentNode;

    @Column(nullable = false, length = 300)
    private String currentHandlers;

    private LocalDate completeDeadline;

    @Column(nullable = false, length = 36)
    private String workflowInstanceId;

    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Project() {
    }

    public Project(String projectNo, String title, String category, String background,
                   DemoUser initiator, FlowType flowType, LocalDate completeDeadline) {
        this.id = UUID.randomUUID().toString();
        this.projectNo = projectNo;
        this.title = title;
        this.category = category;
        this.background = background;
        this.initiatorId = initiator.getId();
        this.initiatorName = initiator.getName();
        this.initiatorOrgName = initiator.getOrgName();
        this.flowType = flowType;
        this.status = ProjectStatus.DRAFT;
        this.currentNode = NodeKey.DRAFT;
        this.currentHandlers = initiator.getName();
        this.completeDeadline = completeDeadline;
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
    public String getProjectNo() { return projectNo; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getBackground() { return background; }
    public String getInitiatorId() { return initiatorId; }
    public String getInitiatorName() { return initiatorName; }
    public String getInitiatorOrgName() { return initiatorOrgName; }
    public FlowType getFlowType() { return flowType; }
    public ProjectStatus getStatus() { return status; }
    public NodeKey getCurrentNode() { return currentNode; }
    public String getCurrentHandlers() { return currentHandlers; }
    public LocalDate getCompleteDeadline() { return completeDeadline; }
    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void bindWorkflow(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public void moveTo(ProjectStatus status, NodeKey currentNode, String currentHandlers) {
        this.status = status;
        this.currentNode = currentNode;
        this.currentHandlers = currentHandlers;
    }
}
