package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "permissions")
public class Permission extends BaseEntity {
    @Column(name = "name", nullable = false, length = 45)
    private String name;

    @ManyToMany
    @JoinTable(name = "role_permission",
        joinColumns = @JoinColumn(name = "permission_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles;

    @Override
    public Map<String, Object> getDetailsForAudit() {
        Map<String, Object> details = new HashMap<>();
        details.put("permissionName", getName());
        details.put("permissionRoles", getRoles());
        return details;
    }
}
