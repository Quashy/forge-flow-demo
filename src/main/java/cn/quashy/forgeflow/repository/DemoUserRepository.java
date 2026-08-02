package cn.quashy.forgeflow.repository;

import cn.quashy.forgeflow.domain.DemoUser;
import cn.quashy.forgeflow.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DemoUserRepository extends JpaRepository<DemoUser, String> {
    List<DemoUser> findByRoleOrderByName(UserRole role);
    List<DemoUser> findAllByOrderByRoleAscNameAsc();
}
