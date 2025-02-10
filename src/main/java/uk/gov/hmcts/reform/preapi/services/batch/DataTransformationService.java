package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
import java.util.logging.Logger;

@Service
public class DataTransformationService {

    private static final List<String> TEST_KEYWORDS = Arrays.asList("test", "demo", "unknown");
    private final RedisService redisService;
    private final DataExtractionService extractionService;
    private final CourtRepository courtRepository;
    
    @Autowired
    public DataTransformationService(
        RedisService redisService,
        DataExtractionService extractionService,
        CourtRepository courtRepository
    ) {
        this.redisService = redisService;
        this.extractionService = extractionService;
        this.courtRepository = courtRepository;
    }
    
    /**
     * Main method for transforming archive list data.
     * @param archiveItem The archive list data to transform.
     * @param channelUserDataMap Mapping of archive names to user data.
     * @return A map containing cleansed data or an error message.
     */
    public TransformationResult transformArchiveListData(
        CSVArchiveListData archiveItem,
        Map<String, List<String[]>> channelUserDataMap
    ) {
        try {
            Map<String, String> sitesDataMap = redisService.getHashAll("sites_data", String.class, String.class);
            if (sitesDataMap == null || sitesDataMap.isEmpty()) {
                throw new IllegalStateException("Sites data not found in Redis");
            }

            UUID courtId = extractCourtId(archiveItem, sitesDataMap);
            Court fullCourt = fetchCourt(courtId);
            Timestamp recordingTimestamp = getRecordingTimestamp(archiveItem);
            CleansedData cleansedData = buildCleansedData(archiveItem, fullCourt, 
                channelUserDataMap, recordingTimestamp);
            
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
        Map<String, List<String[]>> channelUserDataMap,
        Timestamp recordingTimestamp
    ) {

        Map<String, String> extracted = extractCommonFields(archiveItem);

        List<Map<String, String>> shareBookingContacts = buildShareBookingContacts(archiveItem, channelUserDataMap);

        String versionType = extracted.get("recordingVersion");
        String currentVersionNumber = RecordingUtils.getCurrentVersionNumber(
            extractionService.extractRecordingVersionNumber(archiveItem));
        currentVersionNumber = (
            currentVersionNumber == null || currentVersionNumber.isEmpty()) 
                ? "1" : currentVersionNumber;

        CaseState caseState = (!shareBookingContacts.isEmpty()) ? CaseState.OPEN : CaseState.CLOSED;
        TestItem testCheckResult = checkIsTest(archiveItem);

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
            .setIsTest(testCheckResult.isTest())
            .setTestCheckResult(testCheckResult)
            .setIsMostRecentVersion(isMostRecentVersion(archiveItem, versionType, currentVersionNumber))
            .setState(caseState)
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

        for (String keyword : TEST_KEYWORDS) {
            if (lowerName.contains(keyword)) {
                reasons.append("Archive name contains '").append(keyword).append("'. ");
            }
        }

        if (archiveItem.getDuration() < 10) {
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
            Logger.getAnonymousLogger().warning("Error in isMostRecentVersion: " + e.getMessage());
        }
        return false;
    }

    

    private List<String[]> getUsersAndEmails(
        CSVArchiveListData archiveItem, 
        Map<String, List<String[]>> 
        channelUserDataMap
    ) {
        String key = archiveItem.getArchiveNameNoExt(); 
        List<String[]> channelDataList = channelUserDataMap.get(key);
        List<String[]> userEmailList = new ArrayList<>(); 

        if (channelDataList != null) {
            for (String[] channelDataArray : channelDataList) {
                String user = channelDataArray[0];
                String email = channelDataArray[1];
                userEmailList.add(new String[]{user, email}); 
            }
        }
        return userEmailList; 
    }

    private List<Map<String, String>> populateShareBookingContacts(List<String[]> usersAndEmails) {
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

    private List<Map<String,String>> buildShareBookingContacts(
        CSVArchiveListData archiveItem,
        Map<String, List<String[]>> channelUserDataMap
    ) {
        List<String[]> userEmailList = getUsersAndEmails(archiveItem, channelUserDataMap);
        return populateShareBookingContacts(userEmailList);
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
        return String.format("metadataPreprocess:%s-%s-%s",
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


