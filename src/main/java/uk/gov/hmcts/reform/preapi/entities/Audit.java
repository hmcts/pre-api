package uk.gov.hmcts.reform.preapi.entities;

import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.AuditLogType;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "audits")
public class Audit extends CreatedModifiedAtEntity {
    @Column(name = "table_name", length = 25)
    private String tableName;

    @Column(name = "table_record_id")
    private UUID tableRecordId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "audit_log_source")
    @Type(PostgreSQLEnumType.class)
    private AuditLogSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "audit_log_type")
    @Type(PostgreSQLEnumType.class)
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

    @Column(name = "deleted_at", nullable = false)
    private Timestamp deletedAt;
}

