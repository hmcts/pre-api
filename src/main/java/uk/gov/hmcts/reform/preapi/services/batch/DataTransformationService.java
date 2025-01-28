package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.entities.batch.TestItem;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class DataTransformationService {

    private final RedisTemplate<String, Object> redisTemplate; 
    private final DataExtractionService extractionService;
    private final CourtRepository courtRepository;
    
    @Autowired
    public DataTransformationService(
        RedisTemplate<String, Object> redisTemplate,
        DataExtractionService extractionService,
        CourtRepository courtRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.extractionService = extractionService;
        this.courtRepository = courtRepository;
    }
    
    /**
     * Main method for transforming archive list data.
     * @param archiveItem The archive list data to transform.
     * @param sitesDataMap Mapping of court references to full court names.
     * @param channelUserDataMap Mapping of archive names to user data.
     * @return A map containing cleansed data or an error message.
     */
    public Map<String, Object> transformArchiveListData(
        CSVArchiveListData archiveItem,
        Map<String, String> sitesDataMap,
        Map<String, List<String[]>> channelUserDataMap
    ) {
        
        try {
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
        List<String[]> userEmailList = getUsersAndEmails(archiveItem, channelUserDataMap);
        List<Map<String, String>> shareBookingContacts = populateShareBookingContacts(userEmailList);
  
        String recordingVersion = extractionService.extractRecordingVersion(archiveItem);
        int recordingVersionNumber = "ORIG".equalsIgnoreCase(recordingVersion) ? 1 : 2;
        String recordingVersionNumberStr = extractionService.extractRecordingVersionNumber(archiveItem);
        recordingVersionNumberStr = (
            recordingVersionNumberStr == null || recordingVersionNumberStr.isEmpty()) 
                ? "1" : recordingVersionNumberStr;

        CaseState caseState = (shareBookingContacts != null && !shareBookingContacts.isEmpty()) 
            ? CaseState.OPEN : CaseState.CLOSED;


        return new CleansedData.Builder()
            .setCourt(court)
            .setRecordingTimestamp(recordingTimestamp)
            .setDuration(Duration.ofSeconds(archiveItem.getDuration()))
            .setCourtReference(extractionService.extractCourtReference(archiveItem))
            .setUrn(extractionService.extractURN(archiveItem))
            .setExhibitReference(extractionService.extractExhibitReference(archiveItem))
            .setDefendantLastName(extractionService.extractDefendantLastName(archiveItem))
            .setWitnessFirstName(extractionService.extractWitnessFirstName(archiveItem))
            .setIsTest(checkIsTest(archiveItem).isTest())
            .setIsMostRecentVersion(isMostRecentVersion(archiveItem))
            .setTestCheckResult(checkIsTest(archiveItem))
            .setState(caseState)
            .setFileExtension(extractionService.extractFileExtension(archiveItem))
            .setRecordingVersion(recordingVersion)
            .setRecordingVersionNumberStr(recordingVersionNumberStr)
            .setRecordingVersionNumber(recordingVersionNumber)
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
        String courtIdString = (String) redisTemplate.opsForValue().get("batch-preprocessor:court:" + fullCourtName);

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
        StringBuilder reasons = new StringBuilder();

        if (containsTestKeyWord(archiveItem.getArchiveName(), "test")) {
            reasons.append("Archive name contains 'test'. ");
        } else if (containsTestKeyWord(archiveItem.getArchiveName(), "demo")) {
            reasons.append("Archive name contains 'demo'. ");
        } else if (containsTestKeyWord(archiveItem.getArchiveName(), "unknown")) {
            reasons.append("Archive name contains 'unknown'. ");
        } else if (archiveItem.getDuration() < 10) {
            reasons.append("Duration is less than 10 seconds. ");
        }

        return reasons.length() > 0 
            ? new TestItem(true, reasons.toString().trim())
            : new TestItem(false, "No test related criteria met.");
    }

    public boolean isMostRecentVersion(CSVArchiveListData archiveItem) {
        try {
            String key = String.format("metadataPreprocess:%s-%s-%s",
                extractionService.extractURN(archiveItem),
                extractionService.extractDefendantLastName(archiveItem),
                extractionService.extractWitnessFirstName(archiveItem));

            Map<Object, Object> rawMap = redisTemplate.opsForHash().entries(key);
            if (rawMap.isEmpty()) {
                return false;
            }

            Map<String, String> existingData = new HashMap<>();
            for (Map.Entry<Object, Object> entry : rawMap.entrySet()) {
                existingData.put((String) entry.getKey(), (String) entry.getValue());
            }

            String versionType = extractionService.extractRecordingVersion(archiveItem);
            String currentVersionNumberStr = extractionService.extractRecordingVersionNumber(archiveItem);
            currentVersionNumberStr = (currentVersionNumberStr == null 
                || currentVersionNumberStr.isEmpty()) ? "1" : currentVersionNumberStr;

            if ("ORIG".equalsIgnoreCase(versionType)) {
                String highestOrigVersionStr = existingData.get("origVersionNumber");
                return highestOrigVersionStr == null 
                    || compareVersionStrings(currentVersionNumberStr, highestOrigVersionStr) >= 0;
            } else if ("COPY".equalsIgnoreCase(versionType)) {
                String highestCopyVersionStr = existingData.get("copyVersionNumber");
                return highestCopyVersionStr == null 
                    || compareVersionStrings(currentVersionNumberStr, highestCopyVersionStr) >= 0;
            } else {
                Logger.getAnonymousLogger().warning("Unsupported version type: " + versionType);
            }
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
        List<String[]> userEmailList = new ArrayList<>(); // Initialize the list to return

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

    private boolean containsTestKeyWord(String value, String criteria) {
        return value != null && value.toLowerCase().contains(criteria);
    }

    // =========================
    // Response Creation Methods
    // =========================

    private Map<String, Object> createErrorResponse(String errorMessage) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("cleansedData", null);
        errorResponse.put("errorMessage", errorMessage);
        return errorResponse;
    }

    private Map<String, Object> createSuccessResponse(CleansedData cleansedData) {
        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("cleansedData", cleansedData);
        successResponse.put("errorMessage", null);
        return successResponse;
    }

    
    // =========================
    // Utility Methods
    // =========================

    public boolean isOriginalVersion(CleansedData cleansedItem) {
        return "ORIG".equalsIgnoreCase(cleansedItem.getRecordingVersion());
    }

    public String buildCaseReference(CleansedData cleansedItem) {
        StringBuilder referenceBuilder = new StringBuilder();
        
        if (cleansedItem.getUrn() != null && !cleansedItem.getUrn().isEmpty()) {
            referenceBuilder.append(cleansedItem.getUrn());
        }
        
        if (cleansedItem.getExhibitReference() != null && !cleansedItem.getExhibitReference().isEmpty()) {
            if (referenceBuilder.length() > 0) {
                referenceBuilder.append("-");
            }
            referenceBuilder.append(cleansedItem.getExhibitReference());
        }
        
        return referenceBuilder.toString();
    }

    public static int compareVersionStrings(String v1, String v2) {
        String[] v1Parts = v1.split("\\.");
        String[] v2Parts = v2.split("\\.");

        int length = Math.max(v1Parts.length, v2Parts.length);
        for (int i = 0; i < length; i++) {
            int v1Part = i < v1Parts.length ? Integer.parseInt(v1Parts[i]) : 0;
            int v2Part = i < v2Parts.length ? Integer.parseInt(v2Parts[i]) : 0;

            if (v1Part < v2Part) {
                return -1;
            }
            if (v1Part > v2Part) {
                return 1;
            }
        }
        return 0;
    }

}


