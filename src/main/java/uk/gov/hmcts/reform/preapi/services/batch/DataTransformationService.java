package uk.gov.hmcts.reform.preapi.services.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uk.gov.hmcts.reform.preapi.batch.processor.ReferenceDataProcessor;
import uk.gov.hmcts.reform.preapi.config.batch.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.entities.batch.TestItem;
import uk.gov.hmcts.reform.preapi.entities.batch.TransformationResult;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.util.batch.RecordingUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DataTransformationService {
    private static final class Constants {
        public static final List<String> TEST_KEYWORDS = Arrays.asList("test", "demo", "unknown");
        public static final String REDIS_COURTS_KEY = "vf:court:";
        public static final String REDIS_RECORDING_METADATA_KEY = "vf:metadataPreprocess:%s-%s-%s";
        public static final int MIN_RECORDING_DURATION = 10;
    }

    private final RedisService redisService;
    private final DataExtractionService extractionService;
    private final CourtRepository courtRepository;
    private final ReferenceDataProcessor referenceDataProcessor;
    private static final Logger logger = LoggerFactory.getLogger(BatchConfiguration.class);

    @Autowired
    public DataTransformationService(
        RedisService redisService,
        DataExtractionService extractionService,
        CourtRepository courtRepository,
        ReferenceDataProcessor referenceDataProcessor
    ) {
        this.redisService = redisService;
        this.extractionService = extractionService;
        this.courtRepository = courtRepository;
        this.referenceDataProcessor = referenceDataProcessor;
    }
    
    /**
     * Main method for transforming archive list data.
     * @param archiveItem The archive list data to transform.
     * @return A map containing cleansed data or an error message.
     */
    public TransformationResult transformData(CSVArchiveListData archiveItem) {
        try {
            Map<String, String> sitesDataMap = getSitesData();
            UUID courtId = extractCourtId(archiveItem, sitesDataMap);
            Court court = fetchCourt(courtId);

            Timestamp recordingTimestamp = getRecordingTimestamp(archiveItem);
            CleansedData cleansedData = buildCleansedData(archiveItem, court, recordingTimestamp);
            
            return createSuccessResponse(cleansedData);

        } catch (Exception e) {
            return createErrorResponse("General error: " + e.getMessage());
        }
    }


    // ==========================
    // Cleansed Data Construction
    // ==========================
    private CleansedData buildCleansedData(
        CSVArchiveListData archiveItem,
        Court court,
        Timestamp recordingTimestamp
    ) {

        Map<String, String> extracted = extractCommonFields(archiveItem);

        List<Map<String, String>> shareBookingContacts = buildShareBookingContacts(archiveItem);

        String versionType = extracted.get("recordingVersion");
        String currentVersionNumber = RecordingUtils.getCurrentVersionNumber(
            extractionService.extractRecordingVersionNumber(archiveItem));
        currentVersionNumber = (
            currentVersionNumber == null || currentVersionNumber.isEmpty()) 
                ? "1" : currentVersionNumber;

        return new CleansedData.Builder()
            .setCourt(court)
            .setRecordingTimestamp(recordingTimestamp)
            .setDuration(Duration.ofSeconds(archiveItem.getDuration()))
            .setCourtReference(extracted.get("courtReference"))
            .setUrn(extracted.get("urn"))
            .setExhibitReference(extracted.get("exhibitReference"))
            .setCaseReference(buildCaseReference(extracted.get("urn"),extracted.get("exhibitReference")))
            .setDefendantLastName(extracted.get("defendantLastName"))
            .setWitnessFirstName(extracted.get("witnessFirstName"))
            .setIsTest(checkIsTest(archiveItem).isTest())
            .setTestCheckResult(checkIsTest(archiveItem))
            .setIsMostRecentVersion(isMostRecentVersion(archiveItem, versionType, currentVersionNumber))
            .setState(determineState(shareBookingContacts))
            .setFileExtension(extracted.get("fileExtension"))
            .setFileName(archiveItem.getFileName())
            .setRecordingVersion(versionType)
            .setRecordingVersionNumberStr(currentVersionNumber)
            .setRecordingVersionNumber(RecordingUtils.determineRecordingVersionNumber(versionType))
            .setShareBookingContacts(shareBookingContacts)
            .build();
    }


    // ======================
    // Court Handling Methods
    // ======================
    
    private UUID extractCourtId(CSVArchiveListData archiveItem, Map<String, String> sitesDataMap) {
        String courtReference = extractionService.extractCourtReference(archiveItem);
        if (courtReference.isEmpty()) {
            throw new IllegalArgumentException("FAIL: Court extraction failed");
        }

        String fullCourtName = sitesDataMap.getOrDefault(courtReference, "Unknown Court");
        String courtIdString = (String) (redisService.getHashValue("vf:court:", fullCourtName, String.class));

        if (courtIdString != null) {
            try {
                return UUID.fromString(courtIdString);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("FAIL: Court ID parsing failed for " + courtIdString, e);
            }
        }
        return null;
    }

    private Court fetchCourt(UUID courtId) {
        return courtId != null ? courtRepository.findById(courtId).orElse(null) : null;
    }

    // ============================
    // Timestamp and Version Parsing
    // ============================
   
    private Timestamp getRecordingTimestamp(CSVArchiveListData archiveItem) {
        String createTime = String.valueOf(archiveItem.getCreateTime());
        try {
            if (createTime.matches("\\d+")) {
                long epochMilli = Long.parseLong(createTime);
                return new Timestamp(epochMilli);
            } else {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                LocalDateTime dateTime = LocalDateTime.parse(createTime, formatter);
                return Timestamp.valueOf(dateTime);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format for createTime: " + createTime, e);
        }
    }


    // ============================
    // Data Validation and Contacts
    // ============================

    public TestItem checkIsTest(CSVArchiveListData archiveItem) {
        String lowerName = archiveItem.getArchiveName().toLowerCase();
        StringBuilder reasons = new StringBuilder();

        for (String keyword : Constants.TEST_KEYWORDS) {
            if (lowerName.contains(keyword)) {
                reasons.append("Archive name contains '").append(keyword).append("'. ");
            }
        }

        if (archiveItem.getDuration() < Constants.MIN_RECORDING_DURATION) {
            reasons.append("Duration is less than 10 seconds. ");
        }

        return reasons.length() > 0 
            ? new TestItem(true, reasons.toString().trim())
            : new TestItem(false, "No test related criteria met.");
    }

    public boolean isMostRecentVersion(
        CSVArchiveListData archiveItem, 
        String versionType, 
        String currentVersionNumber
    ) {
        try {
            // Build the Redis key
            String redisKey = buildMetadataPreprocessKey(archiveItem);

            // Fetch existing metadata
            Map<String, String> existingData = redisService.getHashAll(redisKey, String.class, String.class);
            if (existingData.isEmpty()) {
                return false;
            }

            // Determine version recency
            return RecordingUtils.evaluateRecency(versionType, currentVersionNumber, existingData);

        } catch (Exception e) {
            logger.info("Error in isMostRecentVersion: {}" , e.getMessage());
        }
        return false;
    }

    private List<String[]> getUsersAndEmails(String key) {
        Map<String, List<String[]>> channelUserDataMap = referenceDataProcessor.fetchChannelUserDataMap();
        
        List<String[]> userEmailList = channelUserDataMap.get(key);
        if (userEmailList == null) {
            userEmailList = new ArrayList<>();
        }

        return userEmailList; 
    }

    private List<Map<String, String>> buildShareBookingContacts(CSVArchiveListData archiveItem) {
        String key = archiveItem.getArchiveNameNoExt(); 
        List<String[]> usersAndEmails = getUsersAndEmails(key);
        List<Map<String, String>> contactsList = new ArrayList<>();

        if (usersAndEmails != null) {
            for (String[] userInfo : usersAndEmails) {
                String[] nameParts = userInfo[0].split("\\.");
                Map<String, String> contact = new HashMap<>();
                contact.put("firstName", nameParts.length > 0 ? nameParts[0] : "");
                contact.put("lastName", nameParts.length > 1 ? nameParts[1] : "");
                contact.put("email", userInfo[1]);

                contactsList.add(contact);
            }
        } 

        return contactsList;
    }

    // =========================
    // Response Creation Methods
    // =========================

    private TransformationResult createErrorResponse(String errorMessage) {
        TransformationResult errorResponse = new TransformationResult(null, errorMessage);
        return errorResponse;
    }

    private TransformationResult createSuccessResponse(CleansedData cleansedData) {
        TransformationResult successResponse = new TransformationResult(cleansedData, null);
        return successResponse;
    }

    // =========================
    // Helper Methos
    // =========================    

    private Map<String, String> getSitesData() {
        Map<String, String> sitesDataMap = redisService.getHashAll("sites_data", String.class, String.class);
        if (sitesDataMap == null || sitesDataMap.isEmpty()) {
            throw new IllegalStateException("Sites data not found in Redis");
        }
        return sitesDataMap;
    }

    private CaseState determineState(List<Map<String, String>> contacts) {
        return (!contacts.isEmpty()) ? CaseState.OPEN : CaseState.CLOSED;
    }

    private Map<String, String> extractCommonFields(CSVArchiveListData archiveItem) {
        Map<String, String> fields = new HashMap<>();
        fields.put("courtReference", extractionService.extractCourtReference(archiveItem));
        fields.put("urn", extractionService.extractURN(archiveItem));
        fields.put("exhibitReference", extractionService.extractExhibitReference(archiveItem));
        fields.put("defendantLastName", extractionService.extractDefendantLastName(archiveItem));
        fields.put("witnessFirstName", extractionService.extractWitnessFirstName(archiveItem));
        fields.put("fileExtension", extractionService.extractFileExtension(archiveItem));
        fields.put("recordingVersion", extractionService.extractRecordingVersion(archiveItem));
        fields.put("recordingVersionNumber", extractionService.extractRecordingVersionNumber(archiveItem));
        return fields;
    }

    /* 
    * Extracts the recording version from the archive item.
    */
    private String buildMetadataPreprocessKey(CSVArchiveListData archiveItem) {
        return String.format(Constants.REDIS_RECORDING_METADATA_KEY,
            extractionService.extractURN(archiveItem),
            extractionService.extractDefendantLastName(archiveItem),
            extractionService.extractWitnessFirstName(archiveItem)
        );
    }

    // =========================
    // Utility Methods
    // =========================

    public String buildCaseReference(String urn, String exhibitRef) {
        StringBuilder referenceBuilder = new StringBuilder();
        
        if (urn != null && !urn.isEmpty()) {
            referenceBuilder.append(urn);
        }
        
        if (exhibitRef != null && !exhibitRef.isEmpty()) {
            if (referenceBuilder.length() > 0) {
                referenceBuilder.append("-");
            }
            referenceBuilder.append(exhibitRef);
        }
        
        return referenceBuilder.toString();
    }


}


