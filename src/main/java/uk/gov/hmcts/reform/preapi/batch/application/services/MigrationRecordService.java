package uk.gov.hmcts.reform.preapi.batch.application.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.batch.util.RecordingUtils;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchMigrationRecords;
import uk.gov.hmcts.reform.preapi.dto.migration.CreateVfMigrationRecordDTO;
import uk.gov.hmcts.reform.preapi.dto.migration.VfMigrationRecordDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MigrationRecordService {

    private final MigrationRecordRepository migrationRecordRepository;
    private final LoggingService loggingService;
    private final CourtRepository courtRepository;

    @Autowired
    public MigrationRecordService(
        final MigrationRecordRepository migrationRecordRepository,
        final CourtRepository courtRepository,
        final LoggingService loggingService
    ) {
        this.migrationRecordRepository = migrationRecordRepository;
        this.courtRepository = courtRepository;
        this.loggingService = loggingService;
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

    public List<MigrationRecord> getPendingMigrationRecords() {
        return migrationRecordRepository.findAllByStatus(VfMigrationStatus.PENDING);
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
        recording.setCourtReference(courtReference);
        recording.setUrn(urn);
        recording.setExhibitReference(exhibitReference);
        recording.setDefendantName(defendantName);
        recording.setWitnessName(witnessName);
        recording.setRecordingVersion(recordingVersion);
        recording.setRecordingVersionNumber(recordingVersionNumber);
        recording.setFileName(mp4FileName);
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
    public boolean insertPendingFromXml(
        String archiveId,
        String archiveName,
        String createTimeEpoch,
        String duration,
        String mp4FileName,
        String fileSizeMb
    ) {
        if (migrationRecordRepository.findByArchiveId(archiveId).isPresent()) {
            loggingService.logInfo("Already processed: %s", archiveName);
            return false;
        }

        Timestamp createTime = null;
        long epoch = Long.parseLong(createTimeEpoch);
        if (epoch > 0) {
            if (epoch < 100_000_000_000L) {
                epoch *= 1000;
            }
            createTime = new Timestamp(epoch);
        }

        Integer parsedDuration = null;
        parsedDuration = duration != null ? Integer.valueOf(duration) : null;


        upsert(
            archiveId,
            archiveName,
            createTime,
            parsedDuration,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            mp4FileName,
            fileSizeMb,
            VfMigrationStatus.PENDING,
            null,
            null,
            null,
            null
        );

        return true;
    }

    @Transactional
    public boolean insertPending(CSVArchiveListData archiveItem) {
        if (migrationRecordRepository.findByArchiveId(archiveItem.getArchiveId()).isPresent()) {
            loggingService.logInfo("Already processed: %s", archiveItem.getArchiveName());
            return false;
        }

        upsert(
            archiveItem.getArchiveId(),
            archiveItem.getArchiveName(),
            Optional.ofNullable(archiveItem.getCreateTimeAsLocalDateTime())
                .map(Timestamp::valueOf)
                .orElse(null),
            archiveItem.getDuration(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            archiveItem.getFileName(),
            archiveItem.getFileSize(),
            VfMigrationStatus.PENDING,
            null,
            null,
            null,
            null
        );

        return true;
    }

    // =========================================
    // ============== UPDATE ===================
    // =========================================
    @Transactional
    public void updateMetadataFields(String archiveId, ExtractedMetadata extracted) {
        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(record -> {
            record.setCourtReference(extracted.getCourtReference());
            record.setCourtId(extracted.getCourtId());
            record.setUrn(extracted.getUrn());
            record.setExhibitReference(extracted.getExhibitReference());
            record.setDefendantName(extracted.getDefendantLastName());
            record.setWitnessName(extracted.getWitnessFirstName());
            record.setRecordingVersion(extracted.getRecordingVersion());
            record.setRecordingVersionNumber(extracted.getRecordingVersionNumber());
            record.setFileName(extracted.getFileName());
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
    public void updateIsPreferred(String archiveId, boolean isPreferred) {
        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(record -> {
            record.setIsPreferred(isPreferred);
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
    public void updateToSuccess(String archiveId) {
        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(record -> {
            record.setStatus(VfMigrationStatus.SUCCESS);
            record.setReason("");
            record.setErrorMessage("");
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
    public void updateParentTempIdIfCopy(String archiveId, String recordingGroupKey, String origVersionStr) {
        Optional<MigrationRecord> maybeOrig = migrationRecordRepository
            .findByRecordingGroupKey(recordingGroupKey)
            .stream()
            .filter(r -> "ORIG".equalsIgnoreCase(r.getRecordingVersion()))
            .filter(r -> r.getIsPreferred() != null && r.getIsPreferred())
            .filter(r -> {
                String recVersion = r.getRecordingVersionNumber();
                return recVersion != null && recVersion.split("\\.")[0].equals(origVersionStr);
            })
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
    @Transactional(readOnly = true)
    public Optional<MigrationRecord> findOrigByGroupKey(String groupKey) {
        return migrationRecordRepository.findByRecordingGroupKey(groupKey).stream()
            .filter(record -> "ORIG".equalsIgnoreCase(record.getRecordingVersion()))
            .findFirst();
    }

    private static String nullToEmpty(String input) {
        return input == null ? "" : input;
    }

    @Transactional
    public boolean deduplicatePreferredByArchiveId(String archiveId) {
        Optional<MigrationRecord> maybeCurrent = migrationRecordRepository.findByArchiveId(archiveId);
        if (maybeCurrent.isEmpty()) {
            return false;
        }

        MigrationRecord current = maybeCurrent.get();
        String archiveName = current.getArchiveName();

        List<MigrationRecord> duplicates = migrationRecordRepository.findAllByArchiveName(archiveName);
        if (duplicates.isEmpty()) {
            return false;
        }

        duplicates.sort(Comparator.comparing(MigrationRecord::getCreateTime).reversed());
        MigrationRecord preferredRecord = duplicates.getFirst();

        for (MigrationRecord record : duplicates) {
            record.setIsPreferred(record.getArchiveId().equals(preferredRecord.getArchiveId()));
            migrationRecordRepository.save(record);
        }

        loggingService.logInfo(
            "Deduplicated archiveName=%s: %d duplicates found, preferred archiveId=%s",
            archiveName, duplicates.size(), preferredRecord.getArchiveId()
        );

        return preferredRecord.getArchiveId().equals(archiveId);
    }

    @Transactional
    public boolean markNonMp4AsNotPreferred(String currentArchiveName) {
        String mp4Name = currentArchiveName.contains(".")
            ? currentArchiveName.replaceAll("\\.[^.]+$", ".mp4")
            : currentArchiveName + ".mp4";

        boolean mp4Exists = !migrationRecordRepository.findAllByArchiveName(mp4Name).isEmpty();
        boolean updated = false;

        if (mp4Exists) {
            List<MigrationRecord> nonPreferredRecords = migrationRecordRepository.findAllByArchiveName(
                currentArchiveName);
            for (MigrationRecord nonPreferred : nonPreferredRecords) {
                nonPreferred.setIsPreferred(false);
                migrationRecordRepository.save(nonPreferred);
                updated = true;
            }
        }



        loggingService.logDebug(
            "Marking as not preferred? archive=%s, mp4Exists=%s, updated=%s",
            currentArchiveName, mp4Exists, updated
        );

        return updated;
    }

    public List<String> findOrigVersionsByBaseGroupKey(String baseGroupKey) {
        return migrationRecordRepository.findByRecordingGroupKeyStartingWith(baseGroupKey).stream()
            .filter(r -> "ORIG".equalsIgnoreCase(r.getRecordingVersion()))
            .map(MigrationRecord::getRecordingVersionNumber)
            .map(v -> v.split("\\.")[0])
            .distinct()
            .toList();
    }

    public Optional<String> findMostRecentVersionNumberInGroup(String groupKey) {
        List<MigrationRecord> groupRecords = migrationRecordRepository.findByRecordingGroupKey(groupKey);

        return groupRecords.stream()
            .map(MigrationRecord::getRecordingVersionNumber)
            .filter(version -> version != null && !version.isBlank())
            .max(RecordingUtils::compareVersionStrings);
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
            copyRecords.getFirst().setIsMostRecent(true);
        } else {
            MigrationRecord mostRecentCopy = copyRecords.stream()
                .max(Comparator.comparingInt(a -> Integer.parseInt(a.getRecordingVersionNumber())))
                .orElse(null);

            for (MigrationRecord copy : copyRecords) {
                copy.setIsMostRecent(copy.equals(mostRecentCopy));
            }
        }

        migrationRecordRepository.saveAll(groupRecords);
    }

    @Transactional(readOnly = true)
    public Page<VfMigrationRecordDTO> findAllBy(final SearchMigrationRecords params, final Pageable pageable) {
        return migrationRecordRepository.findAllBy(
                params.getStatus(),
                params.getWitnessName(),
                params.getDefendantName(),
                params.getCaseReference(),
                params.getCreateDateFromTimestamp(),
                params.getCreateDateToTimestamp(),
                params.getCourtId(),
                pageable)
            .map(VfMigrationRecordDTO::new);
    }

    @Transactional
    public UpsertResult update(final CreateVfMigrationRecordDTO dto) {
        MigrationRecord entity = migrationRecordRepository.findById(dto.getId())
            .orElseThrow(() -> new NotFoundException("Migration Record: " + dto.getId()));

        if (entity.getStatus() == VfMigrationStatus.SUCCESS) {
            throw new ResourceInWrongStateException(
                "MigrationRecord",
                dto.getId().toString(),
                dto.getStatus().toString(),
                "PENDING, FAILED or RESOLVED"
            );
        }

        String courtName = courtRepository.findById(dto.getCourtId())
            .map(Court::getName)
            .orElseThrow(() -> new NotFoundException("Court: " + dto.getCourtId()));

        entity.setCourtReference(courtName);
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
