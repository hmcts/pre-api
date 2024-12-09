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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    
    public Map<String, Object> transformArchiveListData(
        CSVArchiveListData archiveItem,
        Map<String, String> sitesDataMap,
        Map<String, List<String[]>> channelUserDataMap
    ) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String courtReference = extractionService.extractCourtReference(archiveItem);
            if (courtReference.isEmpty()) {
                result.put("cleansedData", null);
                result.put("errorMessage", "FAIL: Court extraction failed");
                return result;
            }

            String fullCourtName = sitesDataMap.getOrDefault(courtReference, "Unknown Court");
            String courtIdString = (String) redisTemplate.opsForValue().get("court:" + fullCourtName);

            UUID courtId = null;
            if (courtIdString != null) {
                try {
                    courtId = UUID.fromString(courtIdString);
                } catch (IllegalArgumentException e) {
                    result.put("cleansedData", null);
                    result.put("errorMessage", "FAIL: Court ID parsing failed for " + courtIdString);
                    return result;
                }
            }

            Court fullcourt = courtId != null ? courtRepository.findById(courtId).orElse(null) : null;

            Timestamp recordingTimestamp;
            try {
                String recordingDate = archiveItem.getCreateTime();
                recordingTimestamp = convertToTimestamp(recordingDate);
                if (recordingTimestamp == null) {
                    result.put("cleansedData", null);
                    result.put("errorMessage", "FAIL: Unable to convert recording date to timestamp: " + recordingDate);
                    return result;
                }
            } catch (Exception e) {
                result.put("cleansedData", null);
                result.put("errorMessage", "FAIL: Exception while converting recording date to timestamp: " + e);
                return result;
            }

            int durationInSeconds = archiveItem.getDuration();
            Duration duration = Duration.ofSeconds(durationInSeconds);

            CleansedData cleansedData = new CleansedData();
            cleansedData.setCourt(fullcourt);
            cleansedData.setDuration(duration);
            cleansedData.setCourtReference(courtReference);
            cleansedData.setFullCourtName(fullCourtName);
            cleansedData.setRecordingTimestamp(recordingTimestamp);
            cleansedData.setUrn(extractionService.extractURN(archiveItem));
            cleansedData.setExhibitReference(extractionService.extractExhibitReference(archiveItem));
            cleansedData.setDefendantLastName(extractionService.extractDefendantLastName(archiveItem));
            cleansedData.setWitnessFirstName(extractionService.extractWitnessFirstName(archiveItem));
            cleansedData.setTest(checkIsTest(archiveItem).isTest());
            cleansedData.setTestCheckResult(checkIsTest(archiveItem));
            cleansedData.setState(CaseState.CLOSED);
            cleansedData.setFileExtension(extractionService.extractFileExtension(archiveItem));
            cleansedData.setRecordingVersion(extractionService.extractRecordingVersion(archiveItem));
            String extractedVersionStr = extractionService.extractRecordingVersionNumber(archiveItem);
            if (extractedVersionStr != null && !extractedVersionStr.isEmpty()) {
                cleansedData.setRecordingVersionNumber(convertToInt(extractedVersionStr));
            }

            List<String[]> usersAndEmails = channelUserDataMap.get(archiveItem.getArchiveNameNoExt());
            List<Map<String, String>> contactsList = populateShareBookingContacts(usersAndEmails);
            cleansedData.setShareBookingContacts(contactsList);

            result.put("cleansedData", cleansedData);
            result.put("errorMessage", null); 
            return result;

        } catch (Exception e) {
            result.put("cleansedData", null);
            result.put("errorMessage", "General error: " + e.getMessage());
            return result;
        }
    }


    public boolean isHighestOrigVersion(CleansedData cleansedItem, String redisKey, String caseReference) {
        Integer currentVersion = cleansedItem.getRecordingVersionNumber();

        String versionKey = redisKey + ":recordingVersion:" + caseReference + "-ORIG";
        Integer highestVersion = (Integer) redisTemplate.opsForValue().get(versionKey);

        if (highestVersion == null || currentVersion > highestVersion) {
            redisTemplate.opsForValue().set(versionKey, currentVersion);
            return true; 
        }

        return currentVersion.equals(highestVersion);
    }



    private boolean containsTestKeyWord(String value, String criteria) {
        return value != null && value.toLowerCase().contains(criteria);
    }

    private int convertToInt(String str) {
        return Integer.parseInt(str);
    }

    public TestItem checkIsTest(CSVArchiveListData archiveItem) {
        StringBuilder reasons = new StringBuilder();

        if (containsTestKeyWord(archiveItem.getArchiveName(), "test")) {
            reasons.append("Archive name contains 'test'. ");
        } else if (containsTestKeyWord(archiveItem.getArchiveName(), "demo")) {
            reasons.append("Archive name contains 'demo'. ");
        } else if (containsTestKeyWord(archiveItem.getArchiveName(), "unknown")) {
            reasons.append("Archive name contains 'unknown'. ");
        } else if (containsTestKeyWord(archiveItem.getFarEndAddress(), "test")) {
            reasons.append("Far end address contains 'test'. ");
        } else if (containsTestKeyWord(archiveItem.getDescription(), "test")) {
            reasons.append("Description contains 'test'. ");
        } else if (containsTestKeyWord(archiveItem.getVideoType(), "test")) {
            reasons.append("Video type contains 'test'. ");
        } else if (containsTestKeyWord(archiveItem.getContentType(), "test")) {
            reasons.append("Content type contains 'test'. ");
        } else if (containsTestKeyWord(archiveItem.getOwner(), "test")) {
            reasons.append("Owner contains 'test'. ");
        } else if (archiveItem.getDuration() < 10) {
            reasons.append("Duration is less than 10 seconds. ");
        }

        if (reasons.length() > 0) {
            String reasonString = "Test: " + reasons.toString().trim();
            return new TestItem(true, reasonString);
        }

        return new TestItem(false, "No test related criteria met.");
    }
    
    public LocalDateTime parseDate(String dateString) {
        String[] formats = {
            "dd/MM/yyyy HH:mm",
            "yyyy-MM-dd",
            "yyMMdd",
            "ddMMyy" 
        };

        for (String format : formats) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                return LocalDateTime.parse(dateString, formatter);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        throw new IllegalArgumentException("Date format not supported: " + dateString);
    }

    public Timestamp convertToTimestamp(String recordingDate) {
        try {
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            LocalDateTime dateTime = LocalDateTime.parse(recordingDate, inputFormatter);

            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedDate = dateTime.format(outputFormatter);

            return Timestamp.valueOf(formattedDate);
        } catch (DateTimeParseException e) {
            return null; 
        }
    }

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

    private List<Map<String, String>> populateShareBookingContacts(List<String[]> usersAndEmails) {
        List<Map<String, String>> contactsList = new ArrayList<>();
        
        if (usersAndEmails != null) {
            for (String[] userInfo : usersAndEmails) {
                String fullName = userInfo[0];
                String email = userInfo[1];
                
                String[] nameParts = fullName.split("\\.");
                String firstName = nameParts.length > 0 ? nameParts[0] : "";
                String lastName = nameParts.length > 1 ? nameParts[1] : "";
                
                Map<String, String> contact = new HashMap<>();
                contact.put("firstName", firstName);
                contact.put("lastName", lastName);
                contact.put("email", email);
                
                contactsList.add(contact);
            }
        }
        
        return contactsList;
    }

    
}


