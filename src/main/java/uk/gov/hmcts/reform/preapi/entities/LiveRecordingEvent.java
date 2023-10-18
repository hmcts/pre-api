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

@Getter
@Setter
@Entity
@Table(name = "live_recording_events")
public class LiveRecordingEvent extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recording_id", nullable = false)
    private Recording recording;

    @Column(nullable = false)
    private String event;

    @Column(nullable = false)
    private Timestamp timestamp;

    @Column(nullable = false, columnDefinition = "json")
    private String payload;
}
