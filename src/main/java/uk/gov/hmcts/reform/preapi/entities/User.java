package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;

import java.sql.Timestamp;

@Getter
@Setter
@Entity
@Table(name = "users")
@SuppressWarnings("PMD.ShortClassName")
public class User extends CreatedModifiedAtEntity {
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "organisation", length = 250)
    private String organisation;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @Transient
    private boolean deleted;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
