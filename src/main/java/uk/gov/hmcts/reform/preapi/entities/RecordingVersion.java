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
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.sql.Time;
import java.sql.Timestamp;

@Getter
@Setter
@Entity
@Table(name = "recording_versions")
public class RecordingVersion extends BaseEntity {
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
    @Column(name = "created_on", nullable = false)
    private Timestamp createdOn;

    @Column
    private Time duration;

    @Column(name = "edit_instruction", columnDefinition = "json")
    private String editInstruction;

    @Column
    private boolean deleted = false;
}
