package uk.gov.hmcts.reform.preapi.entities;

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
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Date;

@Getter
@Setter
@Entity
@Table(name = "recordings")
public class Recording extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "case_id", referencedColumnName = "id", nullable = false)
    private Case recordingCase;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id", referencedColumnName = "id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", referencedColumnName = "id")
    private Recording parent;

    @Column(name = "ingest_rtmp")
    private String ingestRtmp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordingStatus status;

    @Column(name = "start_date", nullable = false)
    private Date startDate;

    @Column(length = 16)
    private String duration;
}
