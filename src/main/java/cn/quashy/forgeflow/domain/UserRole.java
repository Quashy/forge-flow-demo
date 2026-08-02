package cn.quashy.forgeflow.domain;

public enum UserRole {
    EMPLOYEE("作业部员工", 0),
    AREA_SAFETY("作业区安全员", 1),
    DEPARTMENT_SAFETY("作业部安全员", 2),
    COMPANY_SAFETY("公司安全员", 3),
    FUNCTION_EMPLOYEE("职能部员工", 2);

    private final String label;
    private final int level;

    UserRole(String label, int level) {
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
