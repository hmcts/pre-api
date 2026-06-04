package uk.gov.hmcts.reform.preapi.entities;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "portal_access")
public class PortalAccess extends CreatedModifiedAtEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

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

    @Transient
    private boolean deleted;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Override
    public Map<String, Object> getDetailsForAudit() {
        Map<String, Object> details = new HashMap<>();
        details.put("portalAccessUserEmail", user.getEmail());
        details.put("portalAccessStatus", status);
        details.put("portalAccessInvitedAt", invitedAt);
        details.put("portalAccessRegisteredAt", registeredAt);
        details.put("deleted", deletedAt != null);
        return details;
    }
}
