package uk.gov.hmcts.reform.preapi.batch.application.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchMigrationRecords;
import uk.gov.hmcts.reform.preapi.dto.migration.CreateVfMigrationRecordDTO;
import uk.gov.hmcts.reform.preapi.dto.migration.VfMigrationRecordDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class MigrationRecordService {

    private final MigrationRecordRepository migrationRecordRepository;
    private final CourtRepository courtRepository;

    @Autowired
    public MigrationRecordService(final MigrationRecordRepository migrationRecordRepository,
                                  final CourtRepository courtRepository) {
        this.migrationRecordRepository = migrationRecordRepository;
        this.courtRepository = courtRepository;
    }

    @Transactional
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
        // todo set court id
        // recording.setCourtReference(courtReference);
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
            // todo change this to court id
            // record.setCourtReference(extracted.getCourtReference());
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

    @Transactional(readOnly = true)
    public Page<VfMigrationRecordDTO> findAllBy(final SearchMigrationRecords params, final Pageable pageable) {
        return migrationRecordRepository.findAllBy(params, pageable)
            .map(VfMigrationRecordDTO::new);
    }

    @Transactional
    public UpsertResult update(final CreateVfMigrationRecordDTO dto) {
        MigrationRecord entity = migrationRecordRepository.findById(dto.getId())
            .orElseThrow(() -> new NotFoundException("Migration Record: " + dto.getId()));

        if (!courtRepository.existsById(dto.getCourtId())) {
            throw new NotFoundException("Court: " + dto.getCourtId());
        }

        entity.setCourtId(dto.getCourtId());
        entity.setUrn(dto.getUrn());
        entity.setExhibitReference(dto.getExhibitReference());
        entity.setDefendantName(dto.getDefendantName());
        entity.setWitnessName(dto.getWitnessName());
        entity.setRecordingVersion(dto.getRecordingVersion().toString());
        entity.setStatus(dto.getStatus());
        entity.setResolvedAt(dto.getResolvedAt());
        migrationRecordRepository.saveAndFlush(entity);

        return UpsertResult.UPDATED;
    }
}
