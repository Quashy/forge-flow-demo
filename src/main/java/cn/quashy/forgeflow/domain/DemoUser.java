package cn.quashy.forgeflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "demo_user")
public class DemoUser {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    @Column(nullable = false, length = 32)
    private String orgCode;

    @Column(nullable = false, length = 96)
    private String orgName;

    protected DemoUser() {
    }

    public DemoUser(String id, String name, UserRole role, String orgCode, String orgName) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.orgCode = orgCode;
        this.orgName = orgName;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UserRole getRole() {
        return role;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public String getOrgName() {
        return orgName;
    }
}
