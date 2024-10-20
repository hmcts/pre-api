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

        String courtReference = extractionService.extractCourtReference(archiveName);
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
        cleansedData.setUrn(extractionService.extractURN(archiveName));
        cleansedData.setExhibitReference(extractionService.extractExhibitReference(archiveName));
        cleansedData.setDefendantLastName(extractionService.extractDefendantLastName(archiveName));
        cleansedData.setWitnessFirstName(extractionService.extractWitnessFirstName(archiveName));
        cleansedData.setTest(checkIsTest(archiveItem).isTest());
        cleansedData.setTestCheckResult(checkIsTest(archiveItem));
        cleansedData.setState(CaseState.CLOSED);

        cleansedData.setRecordingVersion(extractionService.extractRecordingVersion(archiveName));
        String recordingVersionNumber = extractionService.extractRecordingVersionNumber(archiveName);
        if (recordingVersionNumber != null && !recordingVersionNumber.isEmpty()) {
            cleansedData.setRecordingVersionNumber(Integer.parseInt(recordingVersionNumber));
        } else {
            cleansedData.setRecordingVersionNumber(0); 
        }
        return cleansedData;
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

   
