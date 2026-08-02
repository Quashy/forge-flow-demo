package cn.quashy.forgeflow.service;

import cn.quashy.forgeflow.domain.DemoUser;
import cn.quashy.forgeflow.domain.FlowType;
import cn.quashy.forgeflow.domain.UserRole;
import cn.quashy.forgeflow.repository.ApprovalRecordRepository;
import cn.quashy.forgeflow.repository.ApprovalTaskRepository;
import cn.quashy.forgeflow.repository.DemoUserRepository;
import cn.quashy.forgeflow.repository.ProjectRepository;
import cn.quashy.forgeflow.repository.WorkflowInstanceRepository;
import cn.quashy.forgeflow.web.ApiModels.CreateProjectRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DemoDataServiceTest {

    @Autowired
    private DemoDataService demoDataService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private DemoUserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    @Autowired
    private ApprovalTaskRepository taskRepository;

    @Autowired
    private ApprovalRecordRepository recordRepository;

    @Test
    void resetShouldReplaceBusinessDataWithCompleteDemoBaselineAndKeepUsers() {
        demoDataService.initializeIfEmpty();
        DemoUser temporaryUser = userRepository.save(new DemoUser(
            "TEMP001", "临时演示人", UserRole.EMPLOYEE, "TEMP", "临时组织"
        ));
        workflowService.createProject(temporaryUser, new CreateProjectRequest(
            "访客创建的数据", "演示污染", "该项目应在恢复基线时被清理。",
            FlowType.REPORT, LocalDate.now().plusDays(10)
        ));

        assertThat(projectRepository.count()).isEqualTo(5);

        demoDataService.resetToBaseline();

        assertThat(projectRepository.count()).isEqualTo(4);
        assertThat(instanceRepository.count()).isEqualTo(4);
        assertThat(taskRepository.count()).isEqualTo(7);
        assertThat(recordRepository.count()).isEqualTo(8);
        assertThat(userRepository.existsById("TEMP001")).isTrue();
        assertThat(projectRepository.findAll())
            .extracting(project -> project.getTitle())
            .containsExactlyInAnyOrder(
                "高炉煤气区域机械隔离优化",
                "皮带通廊巡检路径本质化改造",
                "受限空间气体检测点前置",
                "原料区域车辆行人分流改造"
            );
    }
}
