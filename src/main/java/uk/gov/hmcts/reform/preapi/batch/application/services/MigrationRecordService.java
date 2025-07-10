package uk.gov.hmcts.reform.preapi.batch.application.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class MigrationRecordService {

    private final MigrationRecordRepository migrationRecordRepository;

    @Autowired
    public MigrationRecordService(final MigrationRecordRepository migrationRecordRepository) {
        this.migrationRecordRepository = migrationRecordRepository;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #createBookingDTO)")
    public UpsertResult upsert(
        String archiveId,
        String archiveName,
        Timestamp createTime,
        Integer duration,
        String courtReference,
        String urn,
        String exhibitReference,
        String defendantName,
        String witnessName,
        String recordingVersion,
        String recordingVersionNumber,
        String mp4FileName,
        String fileSizeMb,
        VfMigrationStatus status,
        String reason,
        String errorMessage,
        Timestamp resolvedAt,
        UUID recordingId
    ) {
        var existing = migrationRecordRepository.findByArchiveId(archiveId);

        var recording = existing.orElse(new MigrationRecord()); 

        recording.setArchiveId(archiveId);
        recording.setArchiveName(archiveName);
        recording.setCreateTime(createTime);
        recording.setDuration(duration);
        recording.setCourtReference(courtReference);
        recording.setUrn(urn);
        recording.setExhibitReference(exhibitReference);
        recording.setDefendantName(defendantName);
        recording.setWitnessName(witnessName);
        recording.setRecordingVersion(recordingVersion);
        recording.setRecordingVersionNumber(recordingVersionNumber);
        recording.setMp4FileName(mp4FileName);
        recording.setFileSizeMb(fileSizeMb);
        recording.setStatus(status);
        recording.setReason(reason);
        recording.setErrorMessage(errorMessage);
        recording.setResolvedAt(resolvedAt);
        recording.setRecordingId(recordingId);

        var isUpdate = existing.isPresent();

        if (!isUpdate) {
            recording.setId(UUID.randomUUID());
            recording.setCreatedAt(Timestamp.from(Instant.now()));
        }

        migrationRecordRepository.save(recording);
        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    public void insertPending(CSVArchiveListData archiveItem) {
        upsert(
            archiveItem.getArchiveId(),
            archiveItem.getArchiveName(),
            archiveItem.getCreateTimeAsLocalDateTime() != null 
                ? Timestamp.valueOf(archiveItem.getCreateTimeAsLocalDateTime()) : null,
            null,
            null, 
            null,
            null, 
            null,
            null,
            null,
            null, 
            null,
            null,
            VfMigrationStatus.PENDING,
            null,
            null,
            null,
            null
        );
    }

    @Transactional
    public void updateMetadataFields(String archiveId, ExtractedMetadata extracted) {
        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(record -> {
            record.setCourtReference(extracted.getCourtReference());
            record.setUrn(extracted.getUrn());
            record.setExhibitReference(extracted.getExhibitReference());
            record.setDefendantName(extracted.getDefendantLastName());
            record.setWitnessName(extracted.getWitnessFirstName());
            record.setRecordingVersion(extracted.getRecordingVersion());
            record.setRecordingVersionNumber(extracted.getRecordingVersionNumber());
            record.setMp4FileName(extracted.getFileName());
            record.setFileSizeMb(extracted.getFileSize());
            migrationRecordRepository.save(record);
        });
    }

    @Transactional
    public void updateToFailed(String archiveId, String reason, String errorMessage) {
        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(record -> {
            record.setStatus(VfMigrationStatus.FAILED);
            record.setReason(reason);
            record.setErrorMessage(errorMessage);
            migrationRecordRepository.save(record);
        });
    }

    @Transactional
    public void updateToSuccess(String archiveId, UUID recordingId) {
        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(record -> {
            record.setStatus(VfMigrationStatus.SUCCESS);
            record.setRecordingId(recordingId);
            migrationRecordRepository.save(record);
        });
    }
}