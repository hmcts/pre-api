package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;

import java.util.HashMap;

@Getter
@Setter
@Entity
@Table(name = "invites")
@SuppressWarnings("PMD.ShortClassName")
public class Invite extends CreatedModifiedAtEntity {
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "organisation", nullable = false)
    private String organisation;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "code", nullable = false)
    private String code;

    @Override
    public HashMap<String, Object> getDetailsForAudit() {
        var details = new HashMap<String, Object>();
        details.put("email", email);
        details.put("organisation", organisation);
        return details;
    }
}
