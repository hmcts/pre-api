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
import java.util.List;
import java.util.Optional;
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

    // =========================================
    // ============ FIND =======================
    // =========================================
    @Transactional(readOnly = true)
    public Optional<MigrationRecord> findByArchiveId(String archiveId) {
        return migrationRecordRepository.findByArchiveId(archiveId);
    }

    @Transactional(readOnly = true)
    public Optional<MigrationRecord> getOrigFromCopy(MigrationRecord copy) {
        if (copy.getParentTempId() == null) {
            return Optional.empty();
        }
        return migrationRecordRepository.findById(copy.getParentTempId());
    }

    @Transactional(readOnly = true)
    public boolean isMostRecentVersion(String archiveId) {
        return migrationRecordRepository.findByArchiveId(archiveId)
            .map(MigrationRecord::getIsMostRecent)
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<MigrationRecord> getMostRecentCopyWithParentInfo(String recordingGroupKey) {
        List<MigrationRecord> group = migrationRecordRepository.findByRecordingGroupKey(recordingGroupKey);

        return group.stream()
            .filter(r -> "COPY".equalsIgnoreCase(r.getRecordingVersion()))
            .filter(MigrationRecord::getIsMostRecent)
            .findFirst();
    }

    // =========================================
    // ============== UPSERT ===================
    // =========================================
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

    // =========================================
    // ============== UPDATE ===================
    // =========================================
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

            String groupKey = String.join("|",
                nullToEmpty(extracted.getUrn()),
                nullToEmpty(extracted.getExhibitReference()),
                nullToEmpty(extracted.getWitnessFirstName()),
                nullToEmpty(extracted.getDefendantLastName())
            ).toLowerCase().trim();

            record.setRecordingGroupKey(groupKey);
            migrationRecordRepository.save(record);
            setMostRecentFlag(record.getRecordingGroupKey());
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
    public void updateToSuccess(String archiveId) {
        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(record -> {
            record.setStatus(VfMigrationStatus.SUCCESS);
            migrationRecordRepository.save(record);
        });
    }

    @Transactional
    public void updateBookingId(String archiveId, UUID bookingId) {
        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(record -> {
            record.setBookingId(bookingId);
            migrationRecordRepository.save(record);
        });
    }

    @Transactional
    public void updateRecordingId(String archiveId, UUID recordingId) {
        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(record -> {
            record.setRecordingId(recordingId);
            migrationRecordRepository.save(record);
        });
    }

    @Transactional
    public void updateCaptureSessionId(String archiveId, UUID captureSessionId) {
        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(record -> {
            record.setCaptureSessionId(captureSessionId);
            migrationRecordRepository.save(record);
        });
    }

    @Transactional
    public void updateParentTempIdIfCopy(String archiveId, String recordingGroupKey, String version) {
        if (!"COPY".equalsIgnoreCase(version)) {
            return;
        }

        Optional<MigrationRecord> maybeOrig = migrationRecordRepository
            .findByRecordingGroupKey(recordingGroupKey)
            .stream()
            .filter(r -> "ORIG".equalsIgnoreCase(r.getRecordingVersion()))
            .findFirst();

        if (maybeOrig.isEmpty()) {
            return;
        }

        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(copy -> {
            copy.setParentTempId(maybeOrig.get().getId());
            migrationRecordRepository.save(copy);
        });
    }

    // =========================================
    // ============ HELPERS ====================
    // =========================================
    private static String nullToEmpty(String input) {
        return input == null ? "" : input;
    }

    public static String generateRecordingGroupKey(
        String urn, String exhibitRef, String witnessName, String defendantName) {
        return String.join("|",
            nullToEmpty(urn),
            nullToEmpty(exhibitRef),
            nullToEmpty(witnessName),
            nullToEmpty(defendantName)
        ).toLowerCase().trim();
    }

    private void setMostRecentFlag(String groupKey) {
        var groupRecords = migrationRecordRepository.findByRecordingGroupKey(groupKey);

        if (groupRecords.isEmpty()) {
            return;
        }

        groupRecords.stream()
            .filter(r -> "ORIG".equalsIgnoreCase(r.getRecordingVersion()))
            .forEach(r -> r.setIsMostRecent(true));

        var copyRecords = groupRecords.stream()
            .filter(r -> "COPY".equalsIgnoreCase(r.getRecordingVersion()))
            .filter(r -> r.getRecordingVersionNumber() != null && r.getRecordingVersionNumber().matches("\\d+"))
            .toList();

        if (copyRecords.size() == 1) {
            copyRecords.get(0).setIsMostRecent(true);
        } else {
            MigrationRecord mostRecentCopy = copyRecords.stream()
                .max((a, b) -> Integer.compare(
                    Integer.parseInt(a.getRecordingVersionNumber()),
                    Integer.parseInt(b.getRecordingVersionNumber())
                ))
                .orElse(null);

            for (MigrationRecord copy : copyRecords) {
                copy.setIsMostRecent(copy.equals(mostRecentCopy));
            }
        }

        migrationRecordRepository.saveAll(groupRecords);
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

    @Transactional
    public void markReadyRecordsAsSubmitted() {
        migrationRecordRepository.findAllByStatus(VfMigrationStatus.READY)
            .forEach(record -> {
                record.setStatus(VfMigrationStatus.SUBMITTED);
                migrationRecordRepository.saveAndFlush(record);
            });
    }
}
