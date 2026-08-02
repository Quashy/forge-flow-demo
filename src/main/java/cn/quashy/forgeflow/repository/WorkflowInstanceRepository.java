package cn.quashy.forgeflow.repository;

import cn.quashy.forgeflow.domain.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, String> {
    Optional<WorkflowInstance> findByProjectId(String projectId);
}
