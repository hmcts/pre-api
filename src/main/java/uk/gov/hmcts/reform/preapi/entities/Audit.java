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

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "audits")
public class Audit extends BaseEntity {
    @Column(name = "auditable_id", nullable = false)
    private UUID auditableId;

    @Column(name = "auditable_type", nullable = false, length = 16)
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 16)
    private String action;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(nullable = false)
    private Timestamp timestamp;
}
