package uk.gov.hmcts.reform.preapi.batch.application.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationRecordingVersion;
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
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@SuppressWarnings("PMD.TooManyMethods")
public class MigrationRecordService {
    private final MigrationRecordRepository migrationRecordRepository;
    private final LoggingService loggingService;
    private final CourtRepository courtRepository;
    private final boolean dryRun;

    private static final long EPOCH_THRESHOLD_SECONDS_TO_MILLIS = 100_000_000_000L;

    @Autowired
    public MigrationRecordService(final MigrationRecordRepository migrationRecordRepository,
                                  final CourtRepository courtRepository,
                                  final LoggingService loggingService,
                                  @Value("${migration.dry-run:false}") boolean dryRun) {
        this.migrationRecordRepository = migrationRecordRepository;
        this.courtRepository = courtRepository;
        this.loggingService = loggingService;
        this.dryRun = dryRun;
    }

    @Transactional(readOnly = true)
    public Optional<MigrationRecord> findByArchiveId(String archiveId) {
        return migrationRecordRepository.findByArchiveId(archiveId);
    }

    @Transactional(readOnly = true)
    public Optional<MigrationRecord> getOrigFromCopy(MigrationRecord copy) {
        if (copy.getParentTempId() != null) {
            Optional<MigrationRecord> result = migrationRecordRepository.findById(copy.getParentTempId());
            if (result.isPresent()) {
                log.warn("Found ORIG via parent_temp_id: {}", result.get().getArchiveId());
                return result;
            }
        }

        if (copy.getRecordingGroupKey() != null && !copy.getRecordingGroupKey().isEmpty()) {
            List<MigrationRecord> groupRecords = migrationRecordRepository
                .findByRecordingGroupKey(copy.getRecordingGroupKey());

            Optional<MigrationRecord> origRecord = groupRecords.stream()
                .filter(r -> "ORIG".equalsIgnoreCase(r.getRecordingVersion()))
                .filter(r -> r.getStatus() == VfMigrationStatus.SUCCESS)
                .filter(r -> r.getRecordingId() != null)
                .sorted((a, b) -> {
                    int preferredComparison = Boolean.compare(b.getIsPreferred(), a.getIsPreferred());
                    if (preferredComparison != 0) {
                        return preferredComparison;
                    }
                    boolean aIsMp4 = a.getArchiveName().toLowerCase().endsWith(".mp4");
                    boolean bIsMp4 = b.getArchiveName().toLowerCase().endsWith(".mp4");
                    if (aIsMp4 != bIsMp4) {
                        return bIsMp4 ? 1 : -1;
                    }
                    return a.getCreatedAt().compareTo(b.getCreatedAt());
                })
                .findFirst();

            if (origRecord.isPresent()) {
                log.warn("Found ORIG via group key: {}", origRecord.get().getArchiveId());
                return origRecord;
            }
        }

        return Optional.empty();
    }

    public List<MigrationRecord> getPendingMigrationRecords() {
        return migrationRecordRepository.findAllByStatus(VfMigrationStatus.PENDING);
    }

    @Transactional
    @SuppressWarnings("PMD.ExcessiveParameterList")
    public UpsertResult upsert(String archiveId,
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
                               UUID recordingId) {
        Optional<MigrationRecord> existing = migrationRecordRepository.findByArchiveId(archiveId);

        MigrationRecord recording = existing.orElse(new MigrationRecord());

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

        boolean isUpdate = existing.isPresent();

        if (!isUpdate) {
            recording.setId(UUID.randomUUID());
        }

        migrationRecordRepository.saveAndFlush(recording);
        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    public boolean insertPendingFromXml(String archiveId,
                                        String archiveName,
                                        String createTimeEpoch,
                                        String duration,
                                        String mp4FileName,
                                        String fileSizeMb) {
        if (migrationRecordRepository.findByArchiveId(archiveId).isPresent()) {
            loggingService.logInfo("Already processed: %s", archiveName);
            return false;
        }

        Timestamp createTime = null;
        long epoch = Long.parseLong(createTimeEpoch);
        if (epoch > 0) {
            if (epoch < EPOCH_THRESHOLD_SECONDS_TO_MILLIS) {
                epoch *= 1000;
            }
            createTime = new Timestamp(epoch);
        }

        Integer parsedDuration = null;
        if (duration != null) {
            parsedDuration = Integer.valueOf(duration);
        }

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

            String groupKey = generateRecordingGroupKey(
                extracted.getUrn(),
                extracted.getExhibitReference(),
                extracted.getWitnessFirstName(),
                extracted.getDefendantLastName(),
                extracted.getDatePattern(),
                extracted.getCreateTimeAsLocalDateTime()
            );

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
        if (skipDryRun("updateToFailed(" + archiveId + ")")) {
            return;
        }
        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(record -> {
            record.setStatus(VfMigrationStatus.FAILED);
            record.setReason(reason);
            record.setErrorMessage(errorMessage);
            migrationRecordRepository.save(record);
        });
    }

    @Transactional
    public void updateToSuccess(String archiveId) {
        if (skipDryRun("updateToSuccess(" + archiveId + ")")) {
            return;
        }
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
        log.warn("updateParentTempIdIfCopy called for archiveId: {}, groupKey: '{}', origVersionStr: '{}'",
                 archiveId, recordingGroupKey, origVersionStr);

        List<MigrationRecord> allGroupRecords = migrationRecordRepository.findByRecordingGroupKey(recordingGroupKey);
        log.warn("Found {} total records with group key: '{}'", allGroupRecords.size(), recordingGroupKey);

        for (MigrationRecord record : allGroupRecords) {
            log.warn("Group record: archiveId={}, version={}, preferred={}, versionNumber={}, archiveName={}",
                     record.getArchiveId(), record.getRecordingVersion(), record.getIsPreferred(),
                     record.getRecordingVersionNumber(), record.getArchiveName());
        }

        Optional<MigrationRecord> maybeOrig = allGroupRecords
            .stream()
            .filter(r -> !r.getArchiveName().toLowerCase(Locale.UK).endsWith(".raw"))
            .filter(r -> "ORIG".equalsIgnoreCase(r.getRecordingVersion()))
            .filter(MigrationRecord::getIsPreferred)
            .filter(r -> {
                String recVersion = r.getRecordingVersionNumber();
                boolean matches = recVersion != null && recVersion.split("\\.")[0].equals(origVersionStr);
                log.warn("Version filter: recVersion='{}', origVersionStr='{}', matches={}",
                         recVersion, origVersionStr, matches);
                return matches;
            })
            .sorted((a, b) -> {
                boolean aIsMp4 = a.getArchiveName().toLowerCase(Locale.UK).endsWith(".mp4");
                boolean bIsMp4 = b.getArchiveName().toLowerCase(Locale.UK).endsWith(".mp4");
                if (aIsMp4 != bIsMp4) {
                    return bIsMp4 ? 1 : -1;
                }
                return Boolean.compare(b.getIsPreferred(), a.getIsPreferred());
            })
            .findFirst();

        if (maybeOrig.isEmpty()) {
            log.warn("No suitable ORIG found for COPY archiveId: {}", archiveId);
            return;
        }

        log.warn("Found ORIG for COPY: {} -> {}", archiveId, maybeOrig.get().getArchiveId());
        migrationRecordRepository.findByArchiveId(archiveId).ifPresent(copy -> {
            copy.setParentTempId(maybeOrig.get().getId());
            migrationRecordRepository.save(copy);
            log.warn("Set parent_temp_id for COPY {} to ORIG {}", archiveId, maybeOrig.get().getId());
        });
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

    public boolean markNonMp4AsNotPreferred(String archiveId) {
        Optional<MigrationRecord> maybeRecord = migrationRecordRepository.findByArchiveId(archiveId);
        if (maybeRecord.isEmpty()) {
            return false;
        }

        MigrationRecord record = maybeRecord.get();
        String groupKey = record.getRecordingGroupKey();
        String version = record.getRecordingVersionNumber();

        if (groupKey == null || version == null) {
            return false;
        }

        List<MigrationRecord> group = migrationRecordRepository.findByRecordingGroupKey(groupKey);

        List<MigrationRecord> matchingVersion = group.stream()
            .filter(r -> version.equals(r.getRecordingVersionNumber()))
            .filter(r -> "ORIG".equalsIgnoreCase(r.getRecordingVersion()))
            .toList();

        boolean updated = false;
        boolean mp4Exists = matchingVersion.stream()
            .anyMatch(r -> r.getArchiveName() != null && r.getArchiveName().toLowerCase(Locale.UK).endsWith(".mp4"));

        if (mp4Exists) {

            for (MigrationRecord r : matchingVersion) {
                boolean isMp4 = r.getFileName() != null && r.getFileName().toLowerCase(Locale.UK).endsWith(".mp4");
                r.setIsPreferred(isMp4);
                migrationRecordRepository.save(r);
                updated = true;
            }
        }

        return updated;
    }

    @Transactional(readOnly = true)
    public List<MigrationRecord> findShareableOrigs() {
        return migrationRecordRepository.findShareableOrigs();
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
        return migrationRecordRepository.findByRecordingGroupKey(groupKey)
            .stream()
            .map(MigrationRecord::getRecordingVersionNumber)
            .filter(version -> version != null && !version.isBlank())
            .max(RecordingUtils::compareVersionStrings);
    }

    public static String generateRecordingGroupKey(
        String urn,
        String exhibitRef,
        String witnessName,
        String defendantName,
        String datePattern,
        LocalDateTime createTime
    ) {

        String datePart = normaliseDate(datePattern);

        if (datePart == null || datePart.isEmpty()) {
            if (createTime != null) {
                datePart = createTime.toLocalDate().toString();
            } else {
                datePart = "";
            }
        }

        return Stream.of(urn, exhibitRef, witnessName, defendantName, datePart)
            .map(MigrationRecordService::nullToEmpty)
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining("|"));

    }

    private static String normaliseDate(String in) {
        if (in == null || in.isBlank()) {
            return "";
        }
        if (in.matches("\\d{6}")) {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyMMdd");
            return LocalDate.parse(in, f).toString();
        }
        return in.trim();
    }

    private void setMostRecentFlag(String groupKey) {
        List<MigrationRecord> groupRecords = migrationRecordRepository.findByRecordingGroupKey(groupKey);
        if (groupRecords.isEmpty()) {
            return;
        }

        groupRecords.stream()
            .filter(r -> "ORIG".equalsIgnoreCase(r.getRecordingVersion()))
            .forEach(r -> r.setIsMostRecent(true));

        Map<UUID, List<MigrationRecord>> copiesGroupedByParent = groupRecords.stream()
            .filter(r -> "COPY".equalsIgnoreCase(r.getRecordingVersion()))
            .filter(r -> r.getRecordingVersionNumber() != null)
            .filter(r -> r.getParentTempId() != null)
            .collect(Collectors.groupingBy(MigrationRecord::getParentTempId));

        for (List<MigrationRecord> copies : copiesGroupedByParent.values()) {
            List<MigrationRecord> copiesPreferred = copies.stream()
                .filter(MigrationRecord::getIsPreferred)
                .toList();

            List<MigrationRecord> candidates = copiesPreferred.isEmpty() ? copies : copiesPreferred;

            MigrationRecord mostRecent = candidates.stream()
                .max((r1, r2) -> RecordingUtils.compareVersionStrings(
                    r1.getRecordingVersionNumber(),
                    r2.getRecordingVersionNumber()
                ))
                .orElse(null);

            if (mostRecent != null) {
                String mostRecentArchiveId = mostRecent.getArchiveId();
                for (MigrationRecord copy : copies) {
                    copy.setIsMostRecent(mostRecentArchiveId.equals(copy.getArchiveId()));
                }
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
                params.getCourtReference(),
                params.getReasonIn(),
                params.getReasonNotIn(),
                pageable)
            .map(VfMigrationRecordDTO::new);
    }

    @Transactional
    public UpsertResult update(final CreateVfMigrationRecordDTO dto) {
        MigrationRecord entity = migrationRecordRepository.findById(dto.getId())
            .orElseThrow(() -> new NotFoundException("Migration Record: " + dto.getId()));

        if (entity.getStatus() == VfMigrationStatus.SUCCESS
            || entity.getStatus() == VfMigrationStatus.SUBMITTED) {
            throw new ResourceInWrongStateException(
                "MigrationRecord",
                dto.getId().toString(),
                entity.getStatus().toString(),
                "PENDING, FAILED, IGNORED or READY"
            );
        }

        // Only FAILED records can be marked as IGNORED
        if (dto.getStatus() == VfMigrationStatus.IGNORED
            && entity.getStatus() != VfMigrationStatus.FAILED) {
            throw new ResourceInWrongStateException(
                "MigrationRecord",
                dto.getId().toString(),
                entity.getStatus().toString(),
                "FAILED (only FAILED records can be marked as IGNORED)"
            );
        }

        boolean isIgnoredRecord = entity.getStatus() == VfMigrationStatus.IGNORED;
        boolean isMarkingAsIgnored = dto.getStatus() == VfMigrationStatus.IGNORED;

        // Validate required fields for all updates EXCEPT when record is IGNORED or being marked as IGNORED
        // (IGNORED records can have blank values when unignoring or when being marked as ignored)
        if (!isIgnoredRecord && !isMarkingAsIgnored) {
            if (dto.getCourtId() == null) {
                throw new BadRequestException("Court ID is required");
            }
            if (dto.getUrn() == null || dto.getUrn().trim().isEmpty()) {
                throw new BadRequestException("URN is required");
            }
            if (dto.getDefendantName() == null || dto.getDefendantName().trim().isEmpty()) {
                throw new BadRequestException("Defendant name is required");
            }
            if (dto.getWitnessName() == null || dto.getWitnessName().trim().isEmpty()) {
                throw new BadRequestException("Witness name is required");
            }
            if (dto.getRecordingVersion() == null) {
                throw new BadRequestException("Recording version is required");
            }
        }

        // For IGNORED records: use DTO values if provided, otherwise use existing (even if blank)
        UUID courtId = dto.getCourtId() != null ? dto.getCourtId() : entity.getCourtId();

        VfMigrationRecordingVersion recordingVersion = dto.getRecordingVersion();
        if (recordingVersion == null && entity.getRecordingVersion() != null) {
            try {
                recordingVersion = VfMigrationRecordingVersion.valueOf(entity.getRecordingVersion());
            } catch (IllegalArgumentException e) {
                // Ignore if can't parse existing recording version
                loggingService.logInfo("Error parsing recording version: " + e.getMessage());
            }
        }

        String courtName = null;
        if (courtId != null) {
            courtName = courtRepository.findById(courtId)
                .map(Court::getName)
                .orElseThrow(() -> new NotFoundException("Court: " + courtId));
        } else if (isIgnoredRecord) {
            courtName = entity.getCourtReference();
        }

        if (courtName != null) {
            entity.setCourtReference(courtName);
        }
        if (courtId != null) {
            entity.setCourtId(courtId);
        }
        entity.setUrn((dto.getUrn() != null && !dto.getUrn().trim().isEmpty())
            ? dto.getUrn()
            : entity.getUrn());
        entity.setExhibitReference(dto.getExhibitReference() != null
            && !dto.getExhibitReference().trim().isEmpty()
            ? dto.getExhibitReference()
            : null);
        entity.setDefendantName((dto.getDefendantName() != null && !dto.getDefendantName().trim().isEmpty())
            ? dto.getDefendantName()
            : entity.getDefendantName());
        entity.setWitnessName((dto.getWitnessName() != null && !dto.getWitnessName().trim().isEmpty())
            ? dto.getWitnessName()
            : entity.getWitnessName());
        if (recordingVersion != null) {
            entity.setRecordingVersion(recordingVersion.toString());
        }
        entity.setRecordingVersionNumber(dto.getRecordingVersionNumber() != null
                                             ? dto.getRecordingVersionNumber().toString()
                                             : null);
        entity.setCreateTime(dto.getRecordingDate());
        entity.setStatus(dto.getStatus());
        entity.setResolvedAt(dto.getResolvedAt());
        migrationRecordRepository.saveAndFlush(entity);

        return UpsertResult.UPDATED;
    }

    @Transactional
    public boolean markReadyAsSubmitted() {
        if (skipDryRun("markReadyAsSubmitted()")) {
            return false;
        }
        List<MigrationRecord> readyRecords = migrationRecordRepository.findAllByStatus(VfMigrationStatus.READY);

        if (readyRecords.isEmpty()) {
            return false;
        }

        readyRecords.forEach(r -> r.setStatus(VfMigrationStatus.SUBMITTED));
        migrationRecordRepository.saveAllAndFlush(readyRecords);

        return true;
    }

    private static String nullToEmpty(String input) {
        return input == null ? "" : input;
    }

    private boolean skipDryRun(String action) {
        if (dryRun) {
            loggingService.logInfo("[DRY-RUN] Skipping %s", action);
            return true;
        }
        return false;
    }

}
