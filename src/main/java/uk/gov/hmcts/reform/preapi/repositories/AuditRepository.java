package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditRepository extends JpaRepository<Audit, UUID> {
    List<Audit> findBySourceAndFunctionalAreaAndActivity(AuditLogSource source, String functionalArea, String activity);

    @Query(
        value = """
        SELECT a FROM Audit a
        WHERE a.activity != 'Recording Playback ended'
        AND CAST(FUNCTION('jsonb_extract_path_text', a.auditDetails, 'description') as text) ILIKE '%playback%'
        """,
        nativeQuery = false
    )
    List<Audit> findAllAccessAttempts();

    List<Audit> findByTableRecordId(UUID tableRecordId);
}
