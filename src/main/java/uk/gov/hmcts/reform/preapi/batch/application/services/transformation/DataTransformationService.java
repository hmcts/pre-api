package uk.gov.hmcts.reform.preapi.batch.application.services.transformation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ReferenceDataProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.RedisService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CleansedData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
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

    private final RedisService redisService;
    private final CourtRepository courtRepository;
    private final ReferenceDataProcessor referenceDataProcessor;
    private LoggingService loggingService;

    @Autowired
    public DataTransformationService(
        RedisService redisService,
        CourtRepository courtRepository,
        ReferenceDataProcessor referenceDataProcessor,
        LoggingService loggingService
    ) {
        this.redisService = redisService;
        this.courtRepository = courtRepository;
        this.referenceDataProcessor = referenceDataProcessor;
        this.loggingService = loggingService;
    }

    /**
     * Transforms CSV archive data into cleansed data for migration.
     * This method handles the extraction of metadata, validation of required fields,
     * and construction of the cleansed data object.
     *
     * @param archiveItem The CSV archive data to transform
     * @return A ServiceResult containing either the cleansed data or an error message
     */
    public ServiceResult<CleansedData> transformData(CSVArchiveListData archiveItem, ExtractedMetadata extracted) {
        if (archiveItem == null) {
            loggingService.logError("Archive item is null");
            return ServiceResultUtil.failure("Archive item cannot be null", Constants.Reports.FILE_MISSING_DATA);
        }

        try {
            loggingService.logDebug("Starting data transformation for archive: %s", archiveItem.getArchiveName());

            Map<String, String> sitesDataMap = getSitesData();

            CleansedData cleansedData = buildCleansedData(archiveItem, extracted, sitesDataMap);

            loggingService.logDebug(
                "Data transformation completed successfully for archive: %s",
                archiveItem.getArchiveName()
            );
            return ServiceResultUtil.success(cleansedData);

        } catch (Exception e) {
            loggingService.logError("Data transformation failed for archive: %s - %s", archiveItem.getArchiveName(), e);
            return ServiceResultUtil.failure(e.getMessage(), Constants.Reports.FILE_ERROR);
        }
    }

    /**
     * Builds a CleansedData object from the extracted metadata and archive item.
     * This method handles the construction of all required fields and relationships.
     *
     * @param archiveItem  The original archive item
     * @param extracted    The extracted metadata
     * @param sitesDataMap The sites data map from Redis
     * @return A fully constructed CleansedData object
     */
    private CleansedData buildCleansedData(
        CSVArchiveListData archiveItem, ExtractedMetadata extracted, Map<String, String> sitesDataMap) {
        loggingService.logDebug("Building cleansed data for archive: %s", archiveItem.getArchiveName());

        List<Map<String, String>> shareBookingContacts = buildShareBookingContacts(archiveItem);

        String redisKey = RecordingUtils.buildMetadataPreprocessKey(
            extracted.getUrn(), extracted.getDefendantLastName(), extracted.getWitnessFirstName()
        );

        Map<String, String> existingData = redisService.getHashAll(redisKey, String.class, String.class);
        RecordingUtils.VersionDetails versionDetails = RecordingUtils.processVersioning(
            extracted.getRecordingVersion(), extracted.getRecordingVersionNumber(),
            extracted.getUrn(), extracted.getDefendantLastName(), extracted.getWitnessFirstName(), existingData
        );

        Court court = fetchCourtFromDB(extracted, sitesDataMap);
        if (court == null) {
            loggingService.logWarning("Court not found for reference: %s", extracted.getCourtReference());
        }

        return new CleansedData.Builder()
            .setUrn(extracted.getUrn())
            .setExhibitReference(extracted.getExhibitReference())
            .setCaseReference(extracted.createCaseReference())
            .setDefendantLastName(extracted.getDefendantLastName())
            .setWitnessFirstName(extracted.getWitnessFirstName())
            .setCourtReference(extracted.getCourtReference())
            .setCourt(court)
            .setRecordingTimestamp(Timestamp.valueOf(extracted.getCreateTime()))
            .setDuration(Duration.ofSeconds(extracted.getDuration()))
            .setState(determineState(shareBookingContacts))
            .setShareBookingContacts(shareBookingContacts)
            .setFileExtension(extracted.getFileExtension())
            .setFileName(archiveItem.getFileName())
            .setRecordingVersion(versionDetails.getVersionType())
            .setRecordingVersionNumberStr(versionDetails.getVersionNumberStr())
            .setRecordingVersionNumber(versionDetails.getVersionNumber())
            .setIsMostRecentVersion(versionDetails.isMostRecent())
            .build();
    }

    /**
     * Fetches the court entity from the database using the extracted court reference.
     *
     * @param extracted    The extracted metadata containing the court reference
     * @param sitesDataMap The sites data map from Redis
     * @return The Court entity or null if not found
     */
    private Court fetchCourtFromDB(ExtractedMetadata extracted, Map<String, String> sitesDataMap) {
        String courtReference = extracted.getCourtReference();
        if (courtReference == null || courtReference.isEmpty()) {
            loggingService.logError("Court reference is null or empty");
            throw new IllegalArgumentException("Court reference cannot be null or empty");
        }

        String fullCourtName = sitesDataMap.getOrDefault(courtReference, UNKNOWN_COURT);
        Map<String, String> courtsData = redisService.getHashAll(
            Constants.RedisKeys.COURTS_PREFIX,
            String.class,
            String.class
        );

        if (courtsData == null || courtsData.isEmpty()) {
            loggingService.logError("Courts data not found in Redis");
            throw new IllegalStateException("Courts data not found in Redis");
        }

        String courtIdString = courtsData.get(fullCourtName);
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

    /**
     * Builds a list of share booking contacts from the archive item.
     *
     * @param archiveItem The archive item containing the contact information
     * @return A list of contact maps containing first name, last name, and email
     */
    private List<Map<String, String>> buildShareBookingContacts(CSVArchiveListData archiveItem) {
        String archiveName = archiveItem.getArchiveNameNoExt();
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
            "Built {} share booking contacts for archive: %d - %s",
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
    private List<String[]> getUsersAndEmails(String key) {
        Map<String, List<String[]>> channelUserDataMap = referenceDataProcessor.fetchChannelUserDataMap();
        if (channelUserDataMap == null) {
            loggingService.logWarning("Channel user data map is null");
            return new ArrayList<>();
        }
        return channelUserDataMap.getOrDefault(key, new ArrayList<>());
    }

    /**
     * Retrieves sites data from Redis.
     *
     * @return A map of site data
     * @throws IllegalStateException if sites data is not found in Redis
     */
    private Map<String, String> getSitesData() {
        Map<String, String> sitesDataMap = redisService.getHashAll(
            Constants.RedisKeys.SITES_DATA,
            String.class,
            String.class
        );
        if (sitesDataMap == null || sitesDataMap.isEmpty()) {
            loggingService.logError("Sites data not found in Redis");
            throw new IllegalStateException("Sites data not found in Redis");
        }
        return sitesDataMap;
    }

    /**
     * Determines the case state based on the presence of share booking contacts.
     *
     * @param contacts The list of share booking contacts
     * @return The determined case state (OPEN if contacts exist, CLOSED otherwise)
     */
    private CaseState determineState(List<Map<String, String>> contacts) {
        return contacts.isEmpty() ? CaseState.CLOSED : CaseState.OPEN;
    }
}



