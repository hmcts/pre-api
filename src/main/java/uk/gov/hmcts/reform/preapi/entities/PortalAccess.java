package uk.gov.hmcts.reform.preapi.entities;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;

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

    @Column(name = "last_access")
    private Timestamp lastAccess;

    @Column(name = "invite_code", length = 45)
    private String code;

    @Column(name = "invited_at")
    private Timestamp invitedAt;

    @Column(name = "registered_at")
    private Timestamp registeredAt;

    @Column(name = "terms_accepted_at")
    private Timestamp termsAcceptedAt;

    @Column(name = "logged_in")
    private Timestamp loggedIn;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @Override
    public HashMap<String, Object> getDetailsForAudit() {
        var details = new HashMap<String, Object>();
        details.put("portalAccessUserEmail", user.getEmail());
        details.put("portalAccessInvitedAt", invitedAt);
        details.put("portalAccessInviteCode", code);
        details.put("portalAccessRegisteredAt", registeredAt);
        details.put("portalAccessTermsAcceptedAt", termsAcceptedAt);
        details.put("portalAccessLoggedIn", loggedIn);
        details.put("deleted", deletedAt != null);
        return details;
    }
}
