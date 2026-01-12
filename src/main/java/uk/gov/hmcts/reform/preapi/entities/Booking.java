package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;
import uk.gov.hmcts.reform.preapi.entities.base.ISoftDeletable;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "bookings")
public class Booking extends CreatedModifiedAtEntity implements ISoftDeletable {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "case_id", referencedColumnName = "id")
    private Case caseId;

    @Column(name = "scheduled_for", nullable = false)
    private Timestamp scheduledFor;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "booking_participant",
        joinColumns = @JoinColumn(name = "booking_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "participant_id", referencedColumnName = "id")
    )
    private Set<Participant> participants;

    @OneToMany
    @JoinColumn(name = "booking_id", referencedColumnName = "id")
    private Set<CaptureSession> captureSessions;

    @OneToMany
    @JoinColumn(name = "booking_id", referencedColumnName = "id")
    private Set<ShareBooking> shares;

    @Transient
    private boolean deleted;

    @Transient
    private boolean isSoftDeleteOperation;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Override
    public Map<String, Object> getDetailsForAudit() {
        Map<String, Object> details = new HashMap<>();
        details.put("caseId", caseId.getId());
        details.put("bookingScheduledFor", scheduledFor);
        details.put("deleted", isDeleted());
        details.put("participants",
                    (participants == null)
                        ? List.of()
                        : participants.stream().map(BaseEntity::getId).toList());
        return details;
    }

    @Override
    public void setDeleteOperation(boolean deleteOperation) {
        this.isSoftDeleteOperation = deleteOperation;
    }

    @Override
    public boolean isDeleteOperation() {
        return this.isSoftDeleteOperation;
    }
}
