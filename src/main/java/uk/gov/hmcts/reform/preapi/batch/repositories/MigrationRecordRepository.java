package uk.gov.hmcts.reform.preapi.batch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MigrationRecordRepository extends JpaRepository<MigrationRecord, UUID> {
    Optional<MigrationRecord> findByArchiveId(String archiveId);

    Optional<MigrationRecord> findByArchiveName(String archiveName);

    List<MigrationRecord> findAllByArchiveName(String archiveName);
    
    List<MigrationRecord> findByStatus(VfMigrationStatus status);

    List<MigrationRecord> findByRecordingGroupKey(String recordingGroupKey);

    List<MigrationRecord> findByRecordingGroupKeyStartingWith(String baseGroupKey);

}

