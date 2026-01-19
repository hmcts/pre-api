package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourtRepository extends JpaRepository<Court, UUID> {
    @Query(
        """
        SELECT c FROM Court c
        WHERE (:#{#courtType} IS NULL OR c.courtType = :#{#courtType})
        AND (:#{#name} IS NULL OR c.name ILIKE %:#{#name}%)
        AND (:#{#locationCode} IS NULL OR c.locationCode ILIKE %:#{#locationCode}%)
        AND (
            :#{#regionName} IS NULL OR EXISTS (
                SELECT 1 FROM c.regions r
                WHERE r.name ILIKE %:#{#regionName}%
            )
        )

        """
    )
    Page<Court> searchBy(
        @Param("courtType") CourtType courtType,
        @Param("name") String name,
        @Param("locationCode") String locationCode,
        @Param("regionName") String regionName,
        Pageable pageable
    );

    Optional<Court> findFirstByName(String name);
}
