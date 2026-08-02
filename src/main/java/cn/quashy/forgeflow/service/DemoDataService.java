package cn.quashy.forgeflow.service;

import cn.quashy.forgeflow.domain.ApprovalTask;
import cn.quashy.forgeflow.domain.DemoUser;
import cn.quashy.forgeflow.domain.FlowType;
import cn.quashy.forgeflow.domain.TaskStatus;
import cn.quashy.forgeflow.domain.UserRole;
import cn.quashy.forgeflow.domain.WorkflowAction;
import cn.quashy.forgeflow.repository.ApprovalRecordRepository;
import cn.quashy.forgeflow.repository.ApprovalTaskRepository;
import cn.quashy.forgeflow.repository.DemoUserRepository;
import cn.quashy.forgeflow.repository.ProjectRepository;
import cn.quashy.forgeflow.repository.WorkflowInstanceRepository;
import cn.quashy.forgeflow.web.ApiModels.CompleteTaskRequest;
import cn.quashy.forgeflow.web.ApiModels.CreateProjectRequest;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class DemoDataService {

    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

    private final DemoUserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final ApprovalTaskRepository taskRepository;
    private final ApprovalRecordRepository recordRepository;
    private final WorkflowService workflowService;
    private final EntityManager entityManager;

    public DemoDataService(DemoUserRepository userRepository,
                           ProjectRepository projectRepository,
                           WorkflowInstanceRepository instanceRepository,
                           ApprovalTaskRepository taskRepository,
                           ApprovalRecordRepository recordRepository,
                           WorkflowService workflowService,
                           EntityManager entityManager) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.recordRepository = recordRepository;
        this.workflowService = workflowService;
        this.entityManager = entityManager;
    }

    @Transactional
    public void initializeIfEmpty() {
        ensureDemoUsers();
        if (projectRepository.count() == 0) {
            seedProjects();
        }
    }

    @Transactional
    public void resetToBaseline() {
        long projectCount = projectRepository.count();

        recordRepository.deleteAllInBatch();
        taskRepository.deleteAllInBatch();
        instanceRepository.deleteAllInBatch();
        projectRepository.deleteAllInBatch();
        entityManager.flush();
        entityManager.clear();

        ensureDemoUsers();
        seedProjects();
        log.info("演示数据基线恢复完成：清理 {} 个项目，重新生成 {} 个项目", projectCount, projectRepository.count());
    }

    private void ensureDemoUsers() {
        List<DemoUser> users = List.of(
            new DemoUser("EMP001", "林知夏", UserRole.EMPLOYEE, "AREA-A", "炼铁作业部 · 一高炉作业区"),
            new DemoUser("AREA001", "周砺", UserRole.AREA_SAFETY, "AREA-A", "炼铁作业部 · 一高炉作业区"),
            new DemoUser("AREA002", "韩青", UserRole.AREA_SAFETY, "AREA-A", "炼铁作业部 · 一高炉作业区"),
            new DemoUser("DEPT001", "陈屿", UserRole.DEPARTMENT_SAFETY, "DEPT-IRON", "炼铁作业部"),
            new DemoUser("COMP001", "许峥", UserRole.COMPANY_SAFETY, "COMPANY", "迁钢公司安全管理部"),
            new DemoUser("FUNC001", "沈念", UserRole.FUNCTION_EMPLOYEE, "FUNC-EQUIP", "设备管理部")
        );
        users.stream()
            .filter(user -> !userRepository.existsById(user.getId()))
            .forEach(userRepository::save);
        userRepository.flush();
    }

    private void seedProjects() {
        DemoUser employee = userRepository.findById("EMP001").orElseThrow();
        DemoUser areaOfficer = userRepository.findById("AREA001").orElseThrow();
        DemoUser functionEmployee = userRepository.findById("FUNC001").orElseThrow();

        String areaPending = workflowService.createProject(employee, new CreateProjectRequest(
            "高炉煤气区域机械隔离优化",
            "设备设施",
            "现有煤气区域采用临时警戒和人工确认方式，检修窗口期存在误入风险。计划增设联锁隔离装置并优化检修挂牌流程。",
            FlowType.REPORT,
            LocalDate.now().plusDays(35)
        ));
        workflowService.submitProject(areaPending, employee);

        String departmentPending = workflowService.createProject(employee, new CreateProjectRequest(
            "皮带通廊巡检路径本质化改造",
            "作业环境",
            "部分通廊巡检点位靠近转动部件，原有护栏和观察窗无法覆盖所有视角，需要调整巡检路径并增加非接触式监测点。",
            FlowType.REPORT,
            LocalDate.now().plusDays(52)
        ));
        workflowService.submitProject(departmentPending, employee);
        ApprovalTask areaTask = taskRepository.findByProjectIdOrderByCreatedAtDesc(departmentPending).stream()
            .filter(task -> task.getAssigneeId().equals(areaOfficer.getId()) && task.getStatus() == TaskStatus.OPEN)
            .findFirst()
            .orElseThrow();
        workflowService.completeTask(areaTask.getId(), areaOfficer, new CompleteTaskRequest(
            WorkflowAction.UPPER_HELP,
            null,
            "涉及跨区域停机协调，建议作业部统筹资源。",
            "seed-upper-help-department"
        ));

        workflowService.createProject(employee, new CreateProjectRequest(
            "受限空间气体检测点前置",
            "作业管理",
            "现有检测点位距离入口较远，人员进入前的气体状态确认不够直观，拟将固定检测与声光报警前置到入口。",
            FlowType.REPORT,
            LocalDate.now().plusDays(28)
        ));

        String assignment = workflowService.createProject(functionEmployee, new CreateProjectRequest(
            "原料区域车辆行人分流改造",
            "交通安全",
            "原料区域高峰期车辆与巡检人员共用部分通道，拟指派炼铁作业区完成物理隔离、单向组织和警示系统改造。",
            FlowType.ASSIGNMENT,
            LocalDate.now().plusDays(45)
        ));
        workflowService.submitProject(assignment, functionEmployee);
    }
}
