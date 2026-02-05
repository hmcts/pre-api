package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.util.HashMap;
import java.util.Map;

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

    @Override
    public Map<String, Object> getDetailsForAudit() {
        Map<String, Object> details = new HashMap<>();
        details.put("roleName", getName());
        return details;
    }
}
