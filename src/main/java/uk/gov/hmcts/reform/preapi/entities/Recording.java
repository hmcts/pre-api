package uk.gov.hmcts.reform.preapi.entities;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.sql.Time;
import java.sql.Timestamp;

@Getter
@Setter
@Entity
@Table(name = "recordings")
public class Recording extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capture_session_id", referencedColumnName = "id")
    private CaptureSession captureSession;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String filename;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column
    private Time duration;

    @Column(name = "edit_instruction")
    @JdbcTypeCode(SqlTypes.JSON)
    private String editInstruction;

    @Column
    private boolean deleted;
}
