package cn.quashy.forgeflow.repository;

import cn.quashy.forgeflow.domain.Project;
import cn.quashy.forgeflow.domain.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, String> {
    List<Project> findAllByOrderByUpdatedAtDesc();
    long countByStatus(ProjectStatus status);
    boolean existsByProjectNo(String projectNo);
}
