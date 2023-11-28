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
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Timestamp;

@Getter
@Setter
@Entity
@Table(name = "capture_sessions")
public class CaptureSession extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", referencedColumnName = "id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, columnDefinition = "recording_origin")
    @Type(PostgreSQLEnumType.class)
    private RecordingOrigin origin;

    @Column(name = "ingest_address")
    private String ingestAddress;

    @Column(name = "live_output_url", length = 100)
    private String liveOutputUrl;

    @Column(name = "started_at")
    private Timestamp startedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "started_by_user_id", referencedColumnName = "id")
    private User startedByUser;

    @Column(name = "finished_at")
    private Timestamp finishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finished_by_user_id", referencedColumnName = "id")
    private User finishedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "recording_status")
    @Type(PostgreSQLEnumType.class)
    private RecordingStatus status;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;
}
