package cn.quashy.forgeflow.repository;

import cn.quashy.forgeflow.domain.ApprovalTask;
import cn.quashy.forgeflow.domain.TaskStatus;
import cn.quashy.forgeflow.domain.WorkflowAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, String> {

    List<ApprovalTask> findByAssigneeIdAndStatusOrderByCreatedAtDesc(String assigneeId, TaskStatus status);

    List<ApprovalTask> findByAssigneeIdAndStatusNotOrderByCreatedAtDesc(String assigneeId, TaskStatus status);

    List<ApprovalTask> findByProjectIdOrderByCreatedAtDesc(String projectId);

    long countByAssigneeIdAndStatus(String assigneeId, TaskStatus status);

    @Modifying
    @Query("""
        update ApprovalTask t
           set t.status = :completed,
               t.decision = :decision,
               t.commentText = :commentText,
               t.completedAt = :completedAt,
               t.revision = t.revision + 1
         where t.id = :taskId
           and t.assigneeId = :assigneeId
           and t.status = :open
        """)
    int completeIfOpen(@Param("taskId") String taskId,
                       @Param("assigneeId") String assigneeId,
                       @Param("decision") WorkflowAction decision,
                       @Param("commentText") String commentText,
                       @Param("completedAt") Instant completedAt,
                       @Param("open") TaskStatus open,
                       @Param("completed") TaskStatus completed);

    @Modifying
    @Query("""
        update ApprovalTask t
           set t.status = :cancelled,
               t.completedAt = :completedAt,
               t.revision = t.revision + 1
         where t.taskGroupId = :taskGroupId
           and t.id <> :completedTaskId
           and t.status = :open
        """)
    int cancelOpenSiblings(@Param("taskGroupId") String taskGroupId,
                           @Param("completedTaskId") String completedTaskId,
                           @Param("completedAt") Instant completedAt,
                           @Param("open") TaskStatus open,
                           @Param("cancelled") TaskStatus cancelled);
}
