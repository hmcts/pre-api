package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role extends BaseEntity { //NOPMD - suppressed ShortClassName
    @Column(name = "name", nullable = false, length = 45)
    private String name;

    @ManyToMany(mappedBy = "roles")
    private Set<Permission> permissions;
}
