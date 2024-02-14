package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.util.HashMap;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "roles")
@SuppressWarnings("PMD.ShortClassName")
public class Role extends BaseEntity {
    @Column(name = "name", nullable = false, length = 45)
    private String name;

    @Column(name = "description")
    private String description;

    @ManyToMany(mappedBy = "roles")
    private Set<Permission> permissions;

    @Override
    public HashMap<String, Object> getDetailsForAudit() {
        var details = new HashMap<String, Object>();
        details.put("roleName", getName());
        details.put("rolePermissions", getPermissions());
        return details;
    }
}
