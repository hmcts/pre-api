package uk.gov.hmcts.reform.preapi.batch.application.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.batch.util.RecordingUtils;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MigrationRecordService {

    private final MigrationRecordRepository migrationRecordRepository;

    @Autowired
    public MigrationRecordService(final MigrationRecordRepository migrationRecordRepository) {
        this.migrationRecordRepository = migrationRecordRepository;
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
    public void insertPendingFromXml(
        String archiveId,
        String archiveName,
        String createTimeEpoch,
        String duration,
        String mp4FileName,
        String fileSizeMb
    ) {
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
    }
    
    @Transactional
    public void insertPending(CSVArchiveListData archiveItem) {
        upsert(
            archiveItem.getArchiveId(),
            archiveItem.getArchiveName(),
            Optional.ofNullable(archiveItem.getCreateTimeAsLocalDateTime())
                .map(Timestamp::valueOf)
                .orElse(null),
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
            record.setCourtReference(extracted.getCourtReference());
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

    @Transactional
    public void markNonMp4AsNotPreferred(String currentArchiveName) {
    
        String mp4Name = currentArchiveName.replaceAll("\\.[^.]+$", ".mp4");

        if (migrationRecordRepository.findByArchiveName(mp4Name).isPresent()) {
            migrationRecordRepository.findByArchiveName(currentArchiveName)
                .ifPresent(nonPreferred -> {
                    nonPreferred.setIsPreferred(false);
                    migrationRecordRepository.save(nonPreferred);
                });
        }
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
    
}