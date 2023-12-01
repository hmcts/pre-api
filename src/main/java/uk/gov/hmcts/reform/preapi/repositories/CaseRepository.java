package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Case;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseRepository extends JpaRepository<Case, UUID> {
    @Query(
        """
        SELECT c FROM Case c WHERE
        (:reference IS NULL OR LOWER(c.reference) LIKE LOWER(CONCAT('%', :reference, '%'))) AND
        (CAST(:courtId AS java.util.UUID) IS NULL OR c.court.id = :courtId) AND
        c.deletedAt IS NULL
        """
    )
    List<Case> searchCasesBy(
        @Param("reference") String reference,
        @Param("courtId") UUID courtId
    );
}
