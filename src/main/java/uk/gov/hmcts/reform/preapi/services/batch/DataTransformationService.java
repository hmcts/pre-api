package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.entities.batch.TestItem;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.util.batch.RegexPatterns;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class DataTransformationService {

    private final CourtRepository courtRepository;
    
    @Autowired
    public DataTransformationService(CourtRepository courtRepository) {
        this.courtRepository = courtRepository;
    }

    private static final Map<String, Pattern> namedPatterns = new LinkedHashMap<>();

    static {
        namedPatterns.put("1", RegexPatterns.PATTERN_1);
        namedPatterns.put("4", RegexPatterns.PATTERN_4);
        namedPatterns.put("8", RegexPatterns.PATTERN_8);
    }

    public Map.Entry<String, Matcher> matchPattern(String fileName) {
        for (Map.Entry<String, Pattern> entry : namedPatterns.entrySet()) {
            Matcher matcher = entry.getValue().matcher(fileName);
            if (matcher.matches()) {
                return Map.entry(entry.getKey(), matcher); 
            }
        }
        return null; 
    }

    private boolean containsTestKeyWord(String value, String criteria) {
        return value != null && value.toLowerCase().contains(criteria);
    }

    public TestItem checkIsTest(CSVArchiveListData archiveItem) {
        if (containsTestKeyWord(archiveItem.getArchiveName(), "test") 
            || containsTestKeyWord(archiveItem.getArchiveName(), "demo") 
            || containsTestKeyWord(archiveItem.getFarEndAddress(), "test")  
            || containsTestKeyWord(archiveItem.getDescription(), "test")  
            || containsTestKeyWord(archiveItem.getVideoType(), "test") 
            || containsTestKeyWord(archiveItem.getContentType(), "test") 
            || containsTestKeyWord(archiveItem.getOwner(), "test") 
            || archiveItem.getDuration() < 10) {
            return new TestItem(true, "TEST - Test related criteria met.");
        }
        return new TestItem(false, "No test related criteria met.");
    }


    private String extractField(String fileName, String groupName) {
        Map.Entry<String, Matcher> patternMatch = matchPattern(fileName);
        if (patternMatch != null) {
            return patternMatch.getValue().group(groupName);
        }
        return "";
    }

    public String extractCourtReference(String fileName) {
        return extractField(fileName, "court");
    }

    public String extractDate(String fileName) {
        return extractField(fileName, "date");
    }

    public String extractURN(String fileName) {
        return extractField(fileName, "urn");
    }

    public String extractExhibitReference(String fileName) {
        return extractField(fileName, "exhibitRef");
    }

    public String extractDefendantLastName(String fileName) {
        return extractField(fileName, "defendantLastName");
    }

    public String extractWitnessFirstName(String fileName) {
        return extractField(fileName, "witnessFirstName");
    }

    public String extractRecordingVersion(String fileName) {
        return extractField(fileName, "versionType");
    }

    public String extractRecordingVersionNumber(String fileName) {
        return extractField(fileName, "versionNumber");
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
            Logger.getAnonymousLogger().severe("Failed to parse date: "
                + recordingDate + e.getMessage());
            return null; 
        }
    }

    public boolean isOriginalVersion(CleansedData cleansedItem) {
        return "ORIG".equalsIgnoreCase(cleansedItem.getRecordingVersion());
    }

    public CleansedData transformArchiveListData(
            CSVArchiveListData archiveItem,
            Map<String, String> sitesDataMap,
            Map<String, UUID> courtCache) {
        
        String archiveName = archiveItem.getArchiveName();

        String courtReference = extractCourtReference(archiveName);
        String fullCourtName = sitesDataMap.getOrDefault(courtReference, "Unknown Court");

        Court court = courtCache.containsKey(fullCourtName) 
                ? courtRepository.findById(courtCache.get(fullCourtName)).orElse(null)
                : null;

        String recordingDate = archiveItem.getCreateTime();
        Timestamp recordingTimestamp = convertToTimestamp(recordingDate);


        CleansedData cleansedData = new CleansedData();
        cleansedData.setCourt(court);
        cleansedData.setCourtReference(courtReference);
        cleansedData.setFullCourtName(fullCourtName);
        cleansedData.setRecordingTimestamp(recordingTimestamp);
        cleansedData.setUrn(extractURN(archiveName));
        cleansedData.setExhibitReference(extractExhibitReference(archiveName));
        cleansedData.setDefendantLastName(extractDefendantLastName(archiveName));
        cleansedData.setWitnessFirstName(extractWitnessFirstName(archiveName));
        cleansedData.setTest(checkIsTest(archiveItem).isTest());
        cleansedData.setTestCheckResult(checkIsTest(archiveItem));

        cleansedData.setRecordingVersion(extractRecordingVersion(archiveName));
        String recordingVersionNumber = extractRecordingVersionNumber(archiveName);
        if (recordingVersionNumber != null && !recordingVersionNumber.isEmpty()) {
            cleansedData.setRecordingVersionNumber(Integer.parseInt(recordingVersionNumber));
        } else {
            cleansedData.setRecordingVersionNumber(0); 
        }
        return cleansedData;
    }


    public String buildCaseReference(CleansedData cleansedItem) {
        String reference = "";
        if (cleansedItem.getUrn() != null && !cleansedItem.getUrn().isEmpty()) {
            reference = cleansedItem.getUrn();
        }
        if (cleansedItem.getExhibitReference() != null && !cleansedItem.getExhibitReference().isEmpty()) {
            if (!reference.isEmpty()) {
                reference += "-" + cleansedItem.getExhibitReference();  
            } else {
                reference = cleansedItem.getExhibitReference(); 
            }
        }
        return reference;
    }
}

   
