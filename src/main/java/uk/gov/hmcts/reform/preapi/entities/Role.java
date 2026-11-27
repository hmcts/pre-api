package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "roles")
@SuppressWarnings("PMD.ShortClassName")
public class Role extends BaseEntity {
    public static final Comparator<Role> BY_NAME_PRIORITY =
        Comparator.comparingInt((Role role) -> getRolePriority(role == null ? null : role.getName()))
            .thenComparing(role -> role == null || role.getName() == null ? "" : role.getName(),
                           String.CASE_INSENSITIVE_ORDER);

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

    private static int getRolePriority(String name) {
        if (name == null) {
            return 99;
        }
        return switch (name.trim().toLowerCase(Locale.ENGLISH)) {
            case "super user" -> 0;
            case "level 1" -> 1;
            case "level 2" -> 2;
            case "level 3" -> 3;
            case "level 4" -> 4;
            default -> 5; // other
        };
    }

}
