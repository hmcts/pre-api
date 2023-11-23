package uk.gov.hmcts.reform.preapi.entities;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedOnEntity;

import java.sql.Timestamp;

@Getter
@Setter
@Entity
@Table(name = "portal_access")
public class PortalAccess extends CreatedModifiedOnEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(nullable = false, length = 45)
    private String password;

    @Column(name = "last_access")
    private Timestamp lastAccess;

    @Column(name = "invitation_sent")
    private boolean invitationSent = false;

    @Column(name = "invitation_datetime")
    private Timestamp invitationDateTime;

    @Column
    private boolean registered = false;

    @Column(name = "registered_datetime")
    private Timestamp registeredDateTime;

    @Column
    private boolean active;

    @Column
    private boolean deleted = false;
}
