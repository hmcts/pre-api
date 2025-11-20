package uk.gov.hmcts.reform.preapi.entities;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.entities.base.ISoftDeletable;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "share_bookings")
public class ShareBooking extends BaseEntity implements ISoftDeletable {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "booking_id", referencedColumnName = "id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shared_with_user_id", referencedColumnName = "id")
    private User sharedWith;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shared_by_user_id", referencedColumnName = "id")
    private User sharedBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

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
        details.put("bookingId", booking.getId());
        details.put("sharedWithUser", sharedWith.getEmail());
        details.put("sharedByUser", sharedBy.getEmail());
        details.put("deleted", isDeleted());
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
