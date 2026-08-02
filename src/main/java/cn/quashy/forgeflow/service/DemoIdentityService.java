package cn.quashy.forgeflow.service;

import cn.quashy.forgeflow.domain.DemoUser;
import cn.quashy.forgeflow.repository.DemoUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DemoIdentityService {

    private final DemoUserRepository userRepository;

    public DemoIdentityService(DemoUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public DemoUser requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new DemoException(HttpStatus.UNAUTHORIZED, "DEMO_USER_REQUIRED", "请选择一个演示身份");
        }
        return userRepository.findById(userId)
            .orElseThrow(() -> new DemoException(HttpStatus.UNAUTHORIZED, "DEMO_USER_UNKNOWN", "演示身份不存在"));
    }
}
