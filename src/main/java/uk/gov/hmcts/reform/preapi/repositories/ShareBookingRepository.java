package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
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
}
