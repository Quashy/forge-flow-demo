package cn.quashy.forgeflow.service;

import cn.quashy.forgeflow.domain.ApprovalTask;
import cn.quashy.forgeflow.domain.DemoUser;
import cn.quashy.forgeflow.domain.FlowType;
import cn.quashy.forgeflow.domain.NodeKey;
import cn.quashy.forgeflow.domain.ProjectStatus;
import cn.quashy.forgeflow.domain.TaskStatus;
import cn.quashy.forgeflow.domain.TaskType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class WorkflowServiceTest {

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

    @Autowired
    private EntityManager entityManager;

    private DemoUser employee;
    private DemoUser areaOne;
    private DemoUser areaTwo;
    private DemoUser department;
    private DemoUser company;

    @BeforeEach
    void setUp() {
        employee = new DemoUser("T-EMP", "测试员工", UserRole.EMPLOYEE, "AREA-A", "一高炉作业区");
        areaOne = new DemoUser("T-AREA-1", "一区安全员甲", UserRole.AREA_SAFETY, "AREA-A", "一高炉作业区");
        areaTwo = new DemoUser("T-AREA-2", "一区安全员乙", UserRole.AREA_SAFETY, "AREA-A", "一高炉作业区");
        department = new DemoUser("T-DEPT", "作业部安全员", UserRole.DEPARTMENT_SAFETY, "DEPT", "炼铁作业部");
        company = new DemoUser("T-COMP", "公司安全员", UserRole.COMPANY_SAFETY, "COMP", "公司安全管理部");
        userRepository.saveAll(List.of(employee, areaOne, areaTwo, department, company));
    }

    @Test
    void firstSignerShouldCompleteOrSignAndCancelSiblingTask() {
        String projectId = createAndSubmitReport();
        List<ApprovalTask> areaTasks = openTasks(projectId, TaskType.APPROVAL);

        assertThat(areaTasks).hasSize(2);

        ApprovalTask areaOneTask = areaTasks.stream()
            .filter(task -> task.getAssigneeId().equals(areaOne.getId()))
            .findFirst()
            .orElseThrow();
        workflowService.completeTask(areaOneTask.getId(), areaOne, new CompleteTaskRequest(
            WorkflowAction.APPROVE, null, "同意实施", "test-any-sign"
        ));
        entityManager.clear();

        assertThat(projectRepository.findById(projectId).orElseThrow().getStatus())
            .isEqualTo(ProjectStatus.WAITING_EXECUTION);
        assertThat(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
            .extracting(ApprovalTask::getStatus)
            .containsExactlyInAnyOrder(TaskStatus.COMPLETED, TaskStatus.CANCELLED);
    }

    @Test
    void returnedNodeShouldResubmitDirectlyToRejectingNode() {
        String projectId = createAndSubmitReport();

        ApprovalTask areaTask = openTaskFor(projectId, areaOne.getId());
        workflowService.completeTask(areaTask.getId(), areaOne, new CompleteTaskRequest(
            WorkflowAction.UPPER_HELP, null, "需要作业部协调", "test-area-upper"
        ));
        entityManager.clear();

        ApprovalTask departmentTask = openTaskFor(projectId, department.getId());
        workflowService.completeTask(departmentTask.getId(), department, new CompleteTaskRequest(
            WorkflowAction.UPPER_HELP, null, "需要公司协调", "test-department-upper"
        ));
        entityManager.clear();

        ApprovalTask companyTask = openTaskFor(projectId, company.getId());
        workflowService.completeTask(companyTask.getId(), company, new CompleteTaskRequest(
            WorkflowAction.REJECT, NodeKey.AREA_REVIEW, "补充区域隔离方案", "test-company-reject"
        ));
        entityManager.clear();

        assertThat(instanceRepository.findByProjectId(projectId).orElseThrow().getResumeNode())
            .isEqualTo(NodeKey.COMPANY_REVIEW);
        assertThat(projectRepository.findById(projectId).orElseThrow().getStatus())
            .isEqualTo(ProjectStatus.RETURNED);

        ApprovalTask correctionTask = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .filter(task -> task.getAssigneeId().equals(areaOne.getId()))
            .filter(task -> task.getTaskType() == TaskType.CORRECTION)
            .filter(task -> task.getStatus() == TaskStatus.OPEN)
            .findFirst()
            .orElseThrow();
        workflowService.completeTask(correctionTask.getId(), areaOne, new CompleteTaskRequest(
            WorkflowAction.RESUBMIT, null, "已补充隔离方案", "test-area-resubmit"
        ));
        entityManager.clear();

        assertThat(instanceRepository.findByProjectId(projectId).orElseThrow().getCurrentNode())
            .isEqualTo(NodeKey.COMPANY_REVIEW);
        assertThat(openTaskFor(projectId, company.getId()).getNodeKey())
            .isEqualTo(NodeKey.COMPANY_REVIEW);
    }

    @Test
    void repeatedOperationIdShouldReturnOriginalResultWithoutDuplicatingHistory() {
        String projectId = createAndSubmitReport();
        ApprovalTask areaTask = openTaskFor(projectId, areaOne.getId());
        CompleteTaskRequest request = new CompleteTaskRequest(
            WorkflowAction.APPROVE, null, "同意", "test-idempotent-operation"
        );

        workflowService.completeTask(areaTask.getId(), areaOne, request);
        int recordCount = recordRepository.findByProjectIdOrderByEventSequenceAsc(projectId).size();
        workflowService.completeTask(areaTask.getId(), areaOne, request);

        assertThat(recordRepository.findByProjectIdOrderByEventSequenceAsc(projectId))
            .hasSize(recordCount);
    }

    private String createAndSubmitReport() {
        String projectId = workflowService.createProject(employee, new CreateProjectRequest(
            "测试攻关项目", "设备设施", "用于验证固定审批流的测试背景。",
            FlowType.REPORT, LocalDate.now().plusDays(20)
        ));
        workflowService.submitProject(projectId, employee);
        entityManager.flush();
        entityManager.clear();
        return projectId;
    }

    private List<ApprovalTask> openTasks(String projectId, TaskType taskType) {
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .filter(task -> task.getStatus() == TaskStatus.OPEN)
            .filter(task -> task.getTaskType() == taskType)
            .toList();
    }

    private ApprovalTask openTaskFor(String projectId, String assigneeId) {
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .filter(task -> task.getAssigneeId().equals(assigneeId))
            .filter(task -> task.getStatus() == TaskStatus.OPEN)
            .findFirst()
            .orElseThrow();
    }
}
