package uk.gov.hmcts.reform.preapi.batch.repositories;

// import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MigrationRecordRepository extends JpaRepository<MigrationRecord, UUID> {
    Optional<MigrationRecord> findByArchiveId(String archiveId);

    List<MigrationRecord> findAllByArchiveName(String archiveName);

    List<MigrationRecord> findAllByStatus(VfMigrationStatus status);

    List<MigrationRecord> findByRecordingGroupKey(String recordingGroupKey);

    List<MigrationRecord> findByRecordingGroupKeyStartingWith(String baseGroupKey);

    @Query(value = """
        select is_most_recent
        from vf_migration_records
        where archive_id = :archiveId
        order by created_at desc
        limit 1
        """, nativeQuery = true)
    Optional<Boolean> getIsMostRecent(@Param("archiveId") String archiveId);
    

    @Query("""
        SELECT mr FROM MigrationRecord mr
        WHERE (CAST(:status as text)IS NULL OR mr.status = :status)
        AND (:witnessName IS NULL OR mr.witnessName ILIKE %:witnessName%)
        AND (:defendantName IS NULL OR mr.defendantName ILIKE %:defendantName%)
        AND (:caseReference IS NULL
            OR mr.exhibitReference ILIKE %:caseReference%
            OR mr.urn ILIKE %:caseReference%)
        AND (CAST(:createDateFrom as Timestamp) IS NULL OR mr.createTime >= :createDateFrom)
        AND (CAST(:createDateTo as Timestamp) IS NULL OR mr.createTime <= :createDateTo)
        AND (:courtId IS NULL OR mr.courtId = :courtId)
        AND (:reasonIn IS NULL OR mr.reason IN :reasonIn)
        AND (:reasonNotIn IS NULL OR mr.reason NOT IN :reasonNotIn)
        """)
    Page<MigrationRecord> findAllBy(@Param("status") VfMigrationStatus status,
                                    @Param("witnessName") String witnessName,
                                    @Param("defendantName") String defendantName,
                                    @Param("caseReference") String caseReference,
                                    @Param("createDateFrom") Timestamp createDateFrom,
                                    @Param("createDateTo") Timestamp createDateTo,
                                    @Param("courtId") UUID courtId,
                                    @Param("reasonIn") List<String> reasonIn,
                                    @Param("reasonNotIn") List<String> reasonNotIn,
                                    Pageable pageable);
}

