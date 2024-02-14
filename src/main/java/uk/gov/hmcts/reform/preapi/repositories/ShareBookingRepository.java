package uk.gov.hmcts.reform.preapi.repositories;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShareBookingRepository extends SoftDeleteRepository<ShareBooking, UUID> {
    List<ShareBooking> findAllByDeletedAtIsNotNull();


    @Query(
        """
        SELECT s FROM ShareBooking s
        WHERE (
            CAST(:courtId as uuid) IS NULL OR
            s.booking.caseId.court.id = :courtId
        )
        AND (
            CAST(:bookingId as uuid) IS NULL OR
            s.booking.id = :bookingId
        )
        AND (
            CAST(:sharedWithId as uuid) IS NULL OR
            s.sharedWith.id = :sharedWithId
        )
        AND (
            :sharedWithEmail IS NULL OR
            s.sharedWith.email ILIKE %:sharedWithEmail%
        )
        """
    )
    List<ShareBooking> searchAll(
        @Param("courtId") UUID courtId,
        @Param("bookingId") UUID bookingId,
        @Param("sharedWithId") UUID sharedWithId,
        @Param("sharedWithEmail") String sharedWithEmail
    );

    @Query("""
        update ShareBooking e
        set e.deletedAt=CURRENT_TIMESTAMP
        where e.booking=:booking
        and e.deletedAt is null
        """
    )
    @Modifying
    @Transactional
    void deleteAllByBooking(Booking booking);

    @Query(
        """
        SELECT s FROM ShareBooking s
        WHERE s.sharedWith.id = ?1
        AND s.deletedAt IS NULL
        AND s.booking.caseId.court.id = ?2
        """
    )
    List<ShareBooking> findAllSharesForUserByCourt(UUID userId, UUID courtId);

    Page<ShareBooking> findAllByBooking_Id(UUID bookingId, Pageable pageable);
}
