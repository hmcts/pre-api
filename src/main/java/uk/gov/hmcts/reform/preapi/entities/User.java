package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;

import java.sql.Timestamp;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends CreatedModifiedAtEntity { //NOPMD - suppressed ShortClassName
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
}
