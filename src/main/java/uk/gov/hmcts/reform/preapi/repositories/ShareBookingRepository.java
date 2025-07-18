package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShareBookingRepository extends JpaRepository<ShareBooking, UUID> {
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
        AND (
            :onlyActive = FALSE OR s.deletedAt IS NULL
        )
        """
    )
    List<ShareBooking> searchAll(
        @Param("courtId") UUID courtId,
        @Param("bookingId") UUID bookingId,
        @Param("sharedWithId") UUID sharedWithId,
        @Param("sharedWithEmail") String sharedWithEmail,
        @Param("onlyActive") boolean onlyActive
    );

    List<ShareBooking> findAllByBookingAndDeletedAtIsNull(Booking booking);

    Page<ShareBooking> findByBooking_IdOrderBySharedWith_FirstNameAsc(UUID bookingId, Pageable pageable);

    List<ShareBooking> findAllBySharedWith_IdAndDeletedAtIsNull(UUID userId);

    boolean existsBySharedWith_IdAndBooking_IdAndDeletedAtIsNull(UUID userId, UUID bookingId);
}
