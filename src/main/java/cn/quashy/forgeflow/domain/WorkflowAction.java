package cn.quashy.forgeflow.domain;

public enum WorkflowAction {
    CREATE("创建草稿"),
    SUBMIT("提交"),
    APPROVE("审批通过"),
    UPPER_HELP("需上级协助"),
    REJECT("退回"),
    RESUBMIT("修改后重提"),
    CONFIRM_ASSIGN("确认指派");

    private final String label;

    WorkflowAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
