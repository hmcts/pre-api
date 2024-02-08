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

import java.sql.Timestamp;
import java.util.HashMap;

@Getter
@Setter
@Entity
@Table(name = "share_bookings")
public class ShareBooking extends BaseEntity {
    // @todo should be able to share before capture session created and after
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

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Override
    public HashMap<String, Object> getDetailsForAudit() {

        var details = new HashMap<String, Object>();
        details.put("bookingId", booking.getId());
        details.put("sharedWithUser", sharedWith.getEmail());
        details.put("sharedByUser", sharedBy.getEmail());
        details.put("deleted", isDeleted());
        return details;
    }
}
