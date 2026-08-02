package cn.quashy.forgeflow.domain;

public enum TaskStatus {
    OPEN("待处理"),
    COMPLETED("已完成"),
    CANCELLED("已失效");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
