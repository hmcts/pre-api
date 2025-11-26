package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;
import uk.gov.hmcts.reform.preapi.entities.base.ISoftDeletable;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "users")
@SuppressWarnings("PMD.ShortClassName")
public class User extends CreatedModifiedAtEntity implements ISoftDeletable {
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "email2", length = 100)
    private String email2;

    @Column(name = "organisation", length = 250)
    private String organisation;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @OneToMany
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private Set<AppAccess> appAccess;

    @OneToMany
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private Set<PortalAccess> portalAccess;

    @OneToMany
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private Set<UserTermsAccepted> userTermsAccepted;

    @Transient
    private boolean deleted;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Transient
    private boolean isSoftDeleteOperation;

    @Transient
    private String fullName;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public HashMap<String, Object> getDetailsForAudit() {
        var details = new HashMap<String, Object>();
        details.put("userEmail", email);
        details.put("userEmail2", email2);
        details.put("userOrganisation", organisation);
        details.put("deleted", isDeleted());
        return details;
    }

    @Override
    public void setDeleteOperation(boolean deleteOperation) {
        this.isSoftDeleteOperation = deleteOperation;
    }

    @Override
    public boolean isDeleteOperation() {
        return this.isSoftDeleteOperation;
    }
}
