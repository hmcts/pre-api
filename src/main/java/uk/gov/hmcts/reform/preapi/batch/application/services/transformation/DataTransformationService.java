package uk.gov.hmcts.reform.preapi.batch.application.services.transformation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfFailureReason;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.RecordingUtils;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DataTransformationService {
    private static final String UNKNOWN_COURT = "Unknown Court";

    private final InMemoryCacheService cacheService;
    private final MigrationRecordService migrationRecordService;
    private final CourtRepository courtRepository;
    private final LoggingService loggingService;

    @Autowired
    public DataTransformationService(final InMemoryCacheService cacheService,
                                     final MigrationRecordService migrationRecordService,
                                     final CourtRepository courtRepository,
                                     final LoggingService loggingService) {
        this.cacheService = cacheService;
        this.migrationRecordService = migrationRecordService;
        this.courtRepository = courtRepository;
        this.loggingService = loggingService;
    }

    public ServiceResult<ProcessedRecording> transformData(ExtractedMetadata extracted) {
        if (extracted == null) {
            loggingService.logError("Extracted item is null");
            return ServiceResultUtil.failure("Extracted item cannot be null",
                                             VfFailureReason.INCOMPLETE_DATA.toString());
        }

        try {
            loggingService.logDebug(
                "Starting data transformation for archive: %s",
                extracted.getSanitizedArchiveName(), extracted
            );

            Map<String, String> sitesDataMap = getSitesData();
            ProcessedRecording cleansedData = buildProcessedRecording(extracted, sitesDataMap);
            return ServiceResultUtil.success(cleansedData);
        } catch (Exception e) {
            loggingService.logError("Data transformation failed for archive: %s - %s", extracted.getArchiveName(), e);
            return ServiceResultUtil.failure(e.getMessage(), VfFailureReason.GENERAL_ERROR.toString());
        }
    }

    protected ProcessedRecording buildProcessedRecording(ExtractedMetadata extracted,
                                                         Map<String, String> sitesDataMap) {

        loggingService.logDebug("Building cleansed data for archive: %s", extracted.getSanitizedArchiveName());

        // Normalize version type and number
        String rawVersionType = extracted.getRecordingVersion();
        String rawVersionNumber = extracted.getRecordingVersionNumber();

        String versionType = RecordingUtils.normalizeVersionType(rawVersionType);
        String versionNumber = RecordingUtils.getValidVersionNumber(rawVersionNumber);

        String origVersionStr = "1";
        String copyVersionStr = null;

        if ("COPY".equals(versionType)) {
            String baseGroupKey = MigrationRecordService.generateRecordingGroupKey(
                extracted.getUrn(),
                extracted.getExhibitReference(),
                extracted.getWitnessFirstName(),
                extracted.getDefendantLastName()
            );

            String versionPrefix = versionNumber.contains(".")
                ? versionNumber.split("\\.")[0]
                : versionNumber;

            List<String> availableOrigVersions = migrationRecordService
                .findOrigVersionsByBaseGroupKey(baseGroupKey);

            if (availableOrigVersions.contains(versionPrefix)) {
                origVersionStr = versionPrefix;
            } else if (!availableOrigVersions.isEmpty()) {
                origVersionStr = availableOrigVersions.stream().min(RecordingUtils::compareVersionStrings)
                    .orElse("1");
            } else {
                origVersionStr = "1";
            }

            if (versionNumber.contains(".")) {
                copyVersionStr = versionNumber.split("\\.")[1];
            }
        } else if (versionType.equals("ORIG")) {
            origVersionStr = versionNumber;
        }

        boolean isPreferred = true;
        if (!extracted.getArchiveName().toLowerCase().endsWith(".mp4") ) {
            boolean updated = migrationRecordService.markNonMp4AsNotPreferred(extracted.getArchiveId());
            if (updated) {
                loggingService.logInfo("Skipping non-preferred archive: %s", extracted.getArchiveName());
                isPreferred = false;
            }
        }
        // Deduplication check
        boolean isPreferredFromDeduplication = migrationRecordService.deduplicatePreferredByArchiveId(
            extracted.getArchiveId());
        if (!isPreferredFromDeduplication) {
            loggingService.logInfo("Skipping non-preferred archive: %s", extracted.getArchiveName());
            isPreferred = false;
        }
        
        if (!isPreferred) {
            loggingService.logInfo("Skipping non-preferred archive: %s", extracted.getArchiveName());
        }

        migrationRecordService.updateIsPreferred(extracted.getArchiveId(), isPreferred);

        String groupKey = MigrationRecordService.generateRecordingGroupKey(
            extracted.getUrn(),
            extracted.getExhibitReference(),
            extracted.getWitnessFirstName(),
            extracted.getDefendantLastName()
        );

        boolean isMostRecent = migrationRecordService.findMostRecentVersionNumberInGroup(groupKey)
            .map(mostRecent -> RecordingUtils.compareVersionStrings(rawVersionNumber, mostRecent) >= 0)
            .orElse(true);

        migrationRecordService.updateIsMostRecent(
            extracted.getArchiveId(),
            isMostRecent
        );

        // Court Resolution
        Court court = fetchCourtFromDB(extracted, sitesDataMap);
        if (court == null) {
            loggingService.logWarning("Court not found for reference: %s", extracted.getCourtReference());
        }

        List<Map<String, String>> shareBookingContacts = buildShareBookingContacts(extracted);

        // Version details holder
        RecordingUtils.VersionDetails versionDetails = new RecordingUtils.VersionDetails(
            versionType,
            versionNumber,
            origVersionStr,
            copyVersionStr,
            RecordingUtils.getStandardizedVersionNumberFromType(versionType),
            isMostRecent
        );
        // Build final recording
        return ProcessedRecording.builder()
            .archiveId(extracted.getArchiveId())
            .archiveName(extracted.getArchiveName())

            .courtReference(extracted.getCourtReference())
            .court(court)

            .state(determineState(shareBookingContacts))

            .recordingTimestamp(Timestamp.valueOf(extracted.getCreateTimeAsLocalDateTime()))
            .duration(Duration.ofSeconds(extracted.getDuration()))

            .urn(extracted.getUrn())
            .exhibitReference(extracted.getExhibitReference())
            .caseReference(extracted.createCaseReference())
            .defendantLastName(extracted.getDefendantLastName())
            .witnessFirstName(extracted.getWitnessFirstName())
            .origVersionNumberStr(origVersionStr)
            .copyVersionNumberStr(copyVersionStr)
            .extractedRecordingVersion(versionType)
            .extractedRecordingVersionNumberStr(versionNumber)
            .recordingVersionNumber(versionDetails.standardisedVersionNumber())

            .isMostRecentVersion(isMostRecent)

            .isPreferred(isPreferred)
            .fileExtension(extracted.getFileExtension())
            .fileName(extracted.getFileName())

            .shareBookingContacts(shareBookingContacts)

            .build();
    }

    /**
     * Fetches the court entity from the database using the extracted court reference.
     *
     * @param extracted    The extracted metadata containing the court reference
     * @param sitesDataMap The sites data map from Cache
     * @return The Court entity or null if not found
     */
    protected Court fetchCourtFromDB(ExtractedMetadata extracted, Map<String, String> sitesDataMap) {
        String courtReference = extracted.getCourtReference();
        if (courtReference == null || courtReference.isEmpty() || extracted.getCourtId() == null) {
            loggingService.logError("Court reference is null or empty");
        }

        String fullCourtName = sitesDataMap.getOrDefault(courtReference, UNKNOWN_COURT);

        return cacheService.getCourt(fullCourtName)
            .map(CourtDTO::getId)
            .flatMap(courtRepository::findById)
            .or(() -> {
                UUID extractedId = extracted.getCourtId();
                if (extractedId != null) {
                    return courtRepository.findById(extractedId);
                }
                loggingService.logWarning("Court not found in cache or DB for name: %s", fullCourtName);
                return Optional.empty();
            })
            .orElse(null);
    }

    protected List<Map<String, String>> buildShareBookingContacts(ExtractedMetadata extracted) {
        String archiveName = extracted.getArchiveNameNoExt();
        List<String[]> usersAndEmails = getUsersAndEmails(archiveName);
        List<Map<String, String>> contactsList = new ArrayList<>();

        for (String[] userInfo : usersAndEmails) {
            String[] nameParts = userInfo[0].split("\\.");
            Map<String, String> contact = new HashMap<>();
            contact.put("firstName", nameParts.length > 0 ? nameParts[0] : "");
            contact.put("lastName", nameParts.length > 1 ? nameParts[1] : "");
            contact.put("email", userInfo[1]);
            contactsList.add(contact);
        }

        loggingService.logDebug(
            "Built %d share booking contacts for archive: %s",
            contactsList.size(),
            archiveName
        );
        return contactsList;
    }

    /**
     * Retrieves user email data for a given key.
     *
     * @param key The key to look up in the channel user data map
     * @return A list of user email arrays
     */
    protected List<String[]> getUsersAndEmails(String key) {
        return cacheService.getChannelReference(key).orElse(new ArrayList<>());
    }

    /**
     * Retrieves sites data from Cache.
     *
     * @return A map of site data
     */
    protected Map<String, String> getSitesData() {
        Map<String, String> sites = cacheService.getAllSiteReferences();
        if (sites.isEmpty()) {
            loggingService.logError("Sites data not found in Cache");
        }
        return sites;
    }

    /**
     * Determines the case state based on the presence of share booking contacts.
     *
     * @param contacts The list of share booking contacts
     * @return The determined case state (OPEN if contacts exist, CLOSED otherwise)
     */
    protected CaseState determineState(List<Map<String, String>> contacts) {
        return contacts.isEmpty() ? CaseState.CLOSED : CaseState.OPEN;
    }
}
