package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaseRepository extends JpaRepository<Case, UUID> {
    @Query(
        """
        SELECT c FROM Case c
        WHERE (:reference IS NULL OR c.reference ILIKE %:reference%)
        AND (CAST(:courtId as uuid) IS NULL OR c.court.id = :courtId)
        AND (:includeDeleted = TRUE OR c.deletedAt IS NULL)
        AND (CAST(:authCourtId as uuid) IS NULL OR c.court.id = :authCourtId)
        """
    )
    Page<Case> searchCasesBy(
        @Param("reference") String reference,
        @Param("courtId") UUID courtId,
        @Param("includeDeleted") boolean includeDeleted,
        @Param("authCourtId") UUID authCourtId,
        Pageable pageable
    );

    Optional<Case> findByIdAndDeletedAtIsNull(UUID id);

    @SuppressWarnings("PMD.MethodNamingConventions")
    List<Case> findAllByReferenceAndCourt_Id(String caseReference, UUID courtId);

    List<Case> findAllByReference(String reference);

    List<Case> findAllByStateAndClosedAtBefore(CaseState state, Timestamp date);
}
