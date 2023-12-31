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

import java.sql.Timestamp;

@Getter
@Setter
@Entity
@Table(name = "share_recordings")
public class ShareRecording extends BaseEntity {
    // @todo should be able to share before capture session created and after
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capture_session_id", referencedColumnName = "id")
    private CaptureSession captureSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_with_user_id", referencedColumnName = "id")
    private User sharedWith;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_by_user_id", referencedColumnName = "id")
    private User sharedBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;
}
