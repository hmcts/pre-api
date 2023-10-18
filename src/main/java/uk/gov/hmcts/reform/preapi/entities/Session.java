package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "sessions")
public class Session extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "ip_address", nullable = false)
    private String ipAddress;

    @Column(name = "user_agent", nullable = false)
    private String userAgent;

    @Column(nullable = false)
    private String payload;

    @Column(name = "last_activity", nullable = false)
    private int lastActivity;
}
