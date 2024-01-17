package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Case;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaseRepository extends SoftDeleteRepository<Case, UUID> {
    @Query(
        """
        SELECT c FROM Case c WHERE
        (
            CAST(:reference as text) IS NULL OR
            LOWER(CAST(c.reference as text)) LIKE CONCAT('%', LOWER(CAST(:reference as text)), '%')
        ) AND
        (CAST(:courtId as java.util.UUID) IS NULL OR c.court.id = :courtId) AND
        c.deletedAt IS NULL
        """
    )
    Page<Case> searchCasesBy(
        @Param("reference") String reference,
        @Param("courtId") UUID courtId,
        Pageable pageable
    );

    Optional<Case> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    List<Case> findAllByReference(String caseReference);
}
