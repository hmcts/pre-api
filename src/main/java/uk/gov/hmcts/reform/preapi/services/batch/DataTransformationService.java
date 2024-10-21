package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.entities.batch.TestItem;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;


@Service
public class DataTransformationService {

    private final DataExtractionService extractionService;
    private final CourtRepository courtRepository;
    
    @Autowired
    public DataTransformationService(DataExtractionService extractionService,CourtRepository courtRepository) {
        this.extractionService = extractionService;
        this.courtRepository = courtRepository;
    }
    
    public CleansedData transformArchiveListData(
            CSVArchiveListData archiveItem,
            Map<String, String> sitesDataMap,
            Map<String, UUID> courtCache) {
        
        String courtReference = extractionService.extractCourtReference(archiveItem);
        String fullCourtName = sitesDataMap.getOrDefault(courtReference, "Unknown Court");

        Court court = courtCache.containsKey(fullCourtName) 
                ? courtRepository.findById(courtCache.get(fullCourtName)).orElse(null)
                : null;

        String recordingDate = archiveItem.getCreateTime();
        Timestamp recordingTimestamp = convertToTimestamp(recordingDate);

        int durationInSeconds = archiveItem.getDuration();
        Duration duration = Duration.ofSeconds(durationInSeconds);

        CleansedData cleansedData = new CleansedData();
        cleansedData.setCourt(court);
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

        cleansedData.setRecordingVersion(extractionService.extractRecordingVersion(archiveItem));
        String recordingVersionNumber = extractionService.extractRecordingVersionNumber(archiveItem);
        if (recordingVersionNumber != null && !recordingVersionNumber.isEmpty()) {
            cleansedData.setRecordingVersionNumber(Integer.parseInt(recordingVersionNumber));
        } else {
            cleansedData.setRecordingVersionNumber(0); 
        }
        return cleansedData;
    }

    private boolean containsTestKeyWord(String value, String criteria) {
        return value != null && value.toLowerCase().contains(criteria);
    }

    public TestItem checkIsTest(CSVArchiveListData archiveItem) {
        StringBuilder reasons = new StringBuilder();

        if (containsTestKeyWord(archiveItem.getArchiveName(), "test")) {
            reasons.append("Archive name contains 'test'. ");
        } else if (containsTestKeyWord(archiveItem.getArchiveName(), "demo")) {
            reasons.append("Archive name contains 'demo'. ");
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
            reasons.append("Duration is less than 10 minutes. ");
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
            Logger.getAnonymousLogger().severe("Failed to parse date: "
                + recordingDate + e.getMessage());
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
   
}

   
