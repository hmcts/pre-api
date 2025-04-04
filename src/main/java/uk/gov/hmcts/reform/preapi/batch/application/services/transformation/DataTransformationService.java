package uk.gov.hmcts.reform.preapi.batch.application.services.transformation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ReferenceDataProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.RecordingUtils;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DataTransformationService {
    private static final String UNKNOWN_COURT = "Unknown Court";

    private final InMemoryCacheService cacheService;
    private final CourtRepository courtRepository;
    private final ReferenceDataProcessor referenceDataProcessor;
    private final LoggingService loggingService;

    @Autowired
    public DataTransformationService(
        InMemoryCacheService cacheService,
        CourtRepository courtRepository,
        ReferenceDataProcessor referenceDataProcessor,
        LoggingService loggingService
    ) {
        this.cacheService = cacheService;
        this.courtRepository = courtRepository;
        this.referenceDataProcessor = referenceDataProcessor;
        this.loggingService = loggingService;
    }

    public ServiceResult<ProcessedRecording> transformData(ExtractedMetadata extracted) {
        if (extracted == null) {
            loggingService.logError("Extracted item is null");
            return ServiceResultUtil.failure("Extracted item cannot be null", Constants.Reports.FILE_MISSING_DATA);
        }

        try {
            loggingService.logDebug(
                "Extracted data : %s",
                extracted
            );

            loggingService.logDebug(
                "Starting data transformation for archive: %s",
                extracted.getSanitizedArchiveName()
            );

            Map<String, Object> sitesDataMap = getSitesData();
            ProcessedRecording cleansedData = buildProcessedRecording(extracted, sitesDataMap);
            return ServiceResultUtil.success(cleansedData);
        } catch (Exception e) {
            loggingService.logError("Data transformation failed for archive: %s - %s", extracted.getArchiveName(), e);
            return ServiceResultUtil.failure(e.getMessage(), Constants.Reports.FILE_ERROR);
        }
    }

    protected ProcessedRecording buildProcessedRecording(
        ExtractedMetadata extracted, Map<String, Object> sitesDataMap) {
        loggingService.logDebug("Building cleansed data for archive: %s", extracted.getSanitizedArchiveName());

        List<Map<String, String>> shareBookingContacts = buildShareBookingContacts(extracted);

        String key = RecordingUtils.buildMetadataPreprocessKey(
            extracted.getUrn(), extracted.getDefendantLastName(), extracted.getWitnessFirstName()
        );

        Map<String, Object> existingData = cacheService.getHashAll(key);
        RecordingUtils.VersionDetails versionDetails = RecordingUtils.processVersioning(
            extracted.getRecordingVersion(), extracted.getRecordingVersionNumber(),
            extracted.getUrn(), extracted.getDefendantLastName(), extracted.getWitnessFirstName(), existingData
        );

        Court court = fetchCourtFromDB(extracted, sitesDataMap);
        if (court == null) {
            loggingService.logWarning("Court not found for reference: %s", extracted.getCourtReference());
        }

        return ProcessedRecording.builder()
            .urn(extracted.getUrn())
            .exhibitReference(extracted.getExhibitReference())
            .caseReference(extracted.createCaseReference())
            .defendantLastName(extracted.getDefendantLastName())
            .witnessFirstName(extracted.getWitnessFirstName())
            .courtReference(extracted.getCourtReference())
            .court(court)
            .recordingTimestamp(Timestamp.valueOf(extracted.getCreateTime()))
            .duration(Duration.ofSeconds(extracted.getDuration()))
            .state(determineState(shareBookingContacts))
            .shareBookingContacts(shareBookingContacts)
            .fileExtension(extracted.getFileExtension())
            .fileName(extracted.getFileName())
            .recordingVersion(versionDetails.getVersionType())
            .recordingVersionNumberStr(versionDetails.getVersionNumberStr())
            .recordingVersionNumber(versionDetails.getVersionNumber())
            .isMostRecentVersion(versionDetails.isMostRecent())
            .build();
    }

    /**
     * Fetches the court entity from the database using the extracted court reference.
     *
     * @param extracted    The extracted metadata containing the court reference
     * @param sitesDataMap The sites data map from Cache
     * @return The Court entity or null if not found
     */
    protected Court fetchCourtFromDB(ExtractedMetadata extracted, Map<String, Object> sitesDataMap) {
        String courtReference = extracted.getCourtReference();
        if (courtReference == null || courtReference.isEmpty()) {
            loggingService.logError("Court reference is null or empty");
            throw new IllegalArgumentException("Court reference cannot be null or empty");
        }

        Object fullCourtName = sitesDataMap.getOrDefault(courtReference, UNKNOWN_COURT);
        Map<String, Object> courtsData = cacheService.getHashAll(
            Constants.CacheKeys.COURTS_PREFIX);

        if (courtsData == null || courtsData.isEmpty()) {
            loggingService.logError("Courts data not found in Cache");
            throw new IllegalStateException("Courts data not found in Cache");
        }

        String courtIdString = (String) courtsData.get(fullCourtName);
        if (courtIdString != null) {
            try {
                UUID courtId = UUID.fromString(courtIdString);
                return courtRepository.findById(courtId).orElse(null);
            } catch (IllegalArgumentException e) {
                loggingService.logError("Invalid court ID format: $s - $s", courtIdString, e);
                throw new IllegalArgumentException("Court ID parsing failed for: " + courtIdString, e);
            }
        }

        loggingService.logWarning("Court ID not found for court name: %s", fullCourtName);
        return null;
    }

    // TODO use object instead of Map<String, String>
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
        Map<String, List<String[]>> channelUserDataMap = referenceDataProcessor.fetchChannelUserDataMap();
        if (channelUserDataMap == null) {
            loggingService.logWarning("Channel user data map is null");
            return new ArrayList<>();
        }
        return channelUserDataMap.getOrDefault(key, new ArrayList<>());
    }

    /**
     * Retrieves sites data from Cache.
     *
     * @return A map of site data
     * @throws IllegalStateException if sites data is not found in Cache
     */
    protected Map<String, Object> getSitesData() {
        Map<String, Object> sitesDataMap = cacheService.getHashAll(
            Constants.CacheKeys.SITES_DATA
        );

        if (sitesDataMap == null || sitesDataMap.isEmpty()) {
            loggingService.logError("Sites data not found in Cache");
            throw new IllegalStateException("Sites data not found in Cache");
        }
        return sitesDataMap;
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
