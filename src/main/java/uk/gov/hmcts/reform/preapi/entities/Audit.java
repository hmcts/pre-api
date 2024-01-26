package uk.gov.hmcts.reform.preapi.entities;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "audit")
@Immutable
public class Audit extends BaseEntity {

    @Column(name = "table_name", length = 25)
    private String tableName;

    @Column(name = "table_record_id")
    private UUID tableRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, columnDefinition = "audit_log_source")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private AuditLogSource source;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "activity", length = 100)
    private String activity;

    @Column(name = "functional_area", length = 100)
    private String functionalArea;

    @Type(JsonType.class)
    @Column(name = "audit_details", columnDefinition = "jsonb")
    private String auditDetails;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;
}

