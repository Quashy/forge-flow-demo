package cn.quashy.forgeflow.web;

import cn.quashy.forgeflow.domain.DemoUser;
import cn.quashy.forgeflow.repository.DemoUserRepository;
import cn.quashy.forgeflow.service.DemoIdentityService;
import cn.quashy.forgeflow.service.WorkflowService;
import cn.quashy.forgeflow.web.ApiModels.CompleteTaskRequest;
import cn.quashy.forgeflow.web.ApiModels.CreateProjectRequest;
import cn.quashy.forgeflow.web.ApiModels.ProjectDetail;
import cn.quashy.forgeflow.web.ApiModels.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    private final DemoUserRepository userRepository;
    private final DemoIdentityService identityService;
    private final WorkflowService workflowService;

    public DemoController(DemoUserRepository userRepository,
                          DemoIdentityService identityService,
                          WorkflowService workflowService) {
        this.userRepository = userRepository;
        this.identityService = identityService;
        this.workflowService = workflowService;
    }

    @GetMapping("/meta")
    public Map<String, String> meta() {
        return Map.of(
            "name", "Forge Flow",
            "version", "0.1.0",
            "mode", "fixed-workflow-demo"
        );
    }

    @GetMapping("/users")
    public List<UserView> users() {
        return userRepository.findAllByOrderByRoleAscNameAsc().stream()
            .map(user -> new UserView(
                user.getId(), user.getName(), user.getRole().name(), user.getRole().getLabel(),
                user.getOrgName(), initials(user.getName())
            ))
            .toList();
    }

    @GetMapping("/dashboard")
    public ApiModels.DashboardView dashboard(@RequestHeader("X-Demo-User") String userId) {
        return workflowService.getDashboard(identityService.requireUser(userId));
    }

    @GetMapping("/projects")
    public List<ApiModels.ProjectSummary> projects(@RequestHeader("X-Demo-User") String userId) {
        return workflowService.listProjects(identityService.requireUser(userId));
    }

    @GetMapping("/projects/{projectId}")
    public ProjectDetail project(@PathVariable String projectId,
                                 @RequestHeader("X-Demo-User") String userId) {
        return workflowService.getProjectDetail(projectId, identityService.requireUser(userId));
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDetail createProject(@Valid @RequestBody CreateProjectRequest request,
                                       @RequestHeader("X-Demo-User") String userId) {
        DemoUser actor = identityService.requireUser(userId);
        String projectId = workflowService.createProject(actor, request);
        return workflowService.getProjectDetail(projectId, actor);
    }

    @PostMapping("/projects/{projectId}/submit")
    public ProjectDetail submitProject(@PathVariable String projectId,
                                       @RequestHeader("X-Demo-User") String userId) {
        DemoUser actor = identityService.requireUser(userId);
        workflowService.submitProject(projectId, actor);
        return workflowService.getProjectDetail(projectId, actor);
    }

    @GetMapping("/tasks")
    public List<ApiModels.TaskView> tasks(@RequestHeader("X-Demo-User") String userId,
                                         @RequestParam(defaultValue = "todo") String view) {
        return workflowService.listTasks(identityService.requireUser(userId), "done".equalsIgnoreCase(view));
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ProjectDetail completeTask(@PathVariable String taskId,
                                      @Valid @RequestBody CompleteTaskRequest request,
                                      @RequestHeader("X-Demo-User") String userId) {
        DemoUser actor = identityService.requireUser(userId);
        String projectId = workflowService.completeTask(taskId, actor, request);
        return workflowService.getProjectDetail(projectId, actor);
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) {
            return "--";
        }
        return name.substring(Math.max(0, name.length() - 2));
    }
}
