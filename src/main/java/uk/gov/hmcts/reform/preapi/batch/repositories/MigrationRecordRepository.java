package uk.gov.hmcts.reform.preapi.batch.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchMigrationRecords;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MigrationRecordRepository extends JpaRepository<MigrationRecord, UUID> {
    Optional<MigrationRecord> findByArchiveId(String archiveId);

    Optional<MigrationRecord> findByArchiveName(String archiveName);

    List<MigrationRecord> findAllByStatus(VfMigrationStatus status);

    List<MigrationRecord> findByRecordingGroupKey(String recordingGroupKey);

    @Query("""
        SELECT mr FROM MigrationRecord mr
        WHERE (:#{#params.status} IS NULL OR mr.status = :#{#params.status})
        AND (:#{#params.witnessName} IS NULL OR mr.witnessName ILIKE %:#{#params.witnessName}%)
        AND (:#{#params.defendantName} IS NULL OR mr.defendantName ILIKE %:#{#params.defendantName}%)
        AND (:#{#params.caseReference} IS NULL OR mr.exhibitReference ILIKE %:#{#params.caseReference}%)
        AND (:#{#params.createDateFrom} IS NULL OR mr.createTime >= :#{#params.createDateFrom})
        AND (:#{#params.createDateTo} IS NULL OR mr.createTime <= :#{#params.createDateTo})
        AND (:#{#params.courtReference} IS NULL OR mr.courtReference = :#{#params.courtReference})
        """)
    Page<MigrationRecord> findAllBy(@Param("params") SearchMigrationRecords params, Pageable pageable);
}

