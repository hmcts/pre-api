package uk.gov.hmcts.reform.preapi.entities;


import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
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
import org.hibernate.annotations.Type;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;

import java.sql.Timestamp;

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
    @Type(PostgreSQLEnumType.class)
    @Column(name = "status", nullable = false)
    private AccessStatus status = AccessStatus.invitation_sent;

    @Column(name = "invitation_datetime")
    private Timestamp invitationDateTime;

    @Column(name = "registered_datetime")
    private Timestamp registeredDateTime;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;
}
