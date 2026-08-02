package cn.quashy.forgeflow.domain;

public enum ProjectStatus {
    DRAFT("草稿"),
    APPROVING("审批中"),
    RETURNED("待修改"),
    WAITING_EXECUTION("待攻关");

    private final String label;

    ProjectStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
