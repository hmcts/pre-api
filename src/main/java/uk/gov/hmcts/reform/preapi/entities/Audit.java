package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.AuditLogType;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "audits")
public class Audit extends BaseEntity {
    @Column(name = "table_name", length = 25)
    private String tableName;

    @Column(name = "table_record_id")
    private UUID tableRecordId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditLogSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditLogType type;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String activity;

    @Column(name = "functional_area", length = 100)
    private String functionalArea;

    @Column(name = "audit_details")
    private String auditDetails;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_on", nullable = false)
    private Timestamp createdOn;
}

