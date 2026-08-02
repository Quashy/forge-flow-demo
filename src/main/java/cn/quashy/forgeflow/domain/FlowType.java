package cn.quashy.forgeflow.domain;

public enum FlowType {
    REPORT("逐级上报"),
    ASSIGNMENT("跨部门指派");

    private final String label;

    FlowType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
