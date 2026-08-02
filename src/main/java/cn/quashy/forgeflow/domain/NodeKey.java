package cn.quashy.forgeflow.domain;

public enum NodeKey {
    DRAFT("项目填报", 0),
    APPLICANT_EDIT("填报人修改", 0),
    AREA_REVIEW("作业区审批", 1),
    DEPARTMENT_REVIEW("作业部审批", 2),
    COMPANY_REVIEW("公司审批", 3),
    ASSIGN_CONFIRM("指派确认", 1),
    WAITING_EXECUTION("审批完成", 4);

    private final String label;
    private final int level;

    NodeKey(String label, int level) {
        this.label = label;
        this.level = level;
    }

    public String getLabel() {
        return label;
    }

    public int getLevel() {
        return level;
    }
}
