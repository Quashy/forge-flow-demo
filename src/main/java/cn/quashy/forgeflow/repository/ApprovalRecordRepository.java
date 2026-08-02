package cn.quashy.forgeflow.repository;

import cn.quashy.forgeflow.domain.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, String> {
    List<ApprovalRecord> findByProjectIdOrderByEventSequenceAsc(String projectId);
    List<ApprovalRecord> findTop8ByOrderByCreatedAtDesc();
    Optional<ApprovalRecord> findByOperationId(String operationId);
}
