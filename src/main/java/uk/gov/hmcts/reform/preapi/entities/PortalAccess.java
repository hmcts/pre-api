package uk.gov.hmcts.reform.preapi.entities;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;

import java.sql.Timestamp;
import java.util.HashMap;

@Getter
@Setter
@Entity
@Table(name = "portal_access")
public class PortalAccess extends CreatedModifiedAtEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(name = "password", nullable = false, length = 45)
    private String password;

    @Column(name = "last_access")
    private Timestamp lastAccess;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private AccessStatus status = AccessStatus.INVITATION_SENT;

    @Column(name = "invited_at")
    private Timestamp invitedAt;

    @Column(name = "registered_at")
    private Timestamp registeredAt;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @Override
    public HashMap<String, Object> getDetailsForAudit() {
        var details = new HashMap<String, Object>();
        details.put("user", user.getEmail());
        details.put("status", status);
        details.put("invited_at", invitedAt);
        details.put("registered_at", registeredAt);
        details.put("deleted", deletedAt != null);
        return details;
    }
}
