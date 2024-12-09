package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.util.batch.RegexPatterns;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataExtractionService {

    private final Map<String, Pattern> namedPatterns = new LinkedHashMap<>();

    public DataExtractionService() {
        
        namedPatterns.put("11", RegexPatterns.PATTERN_11);
        namedPatterns.put("10", RegexPatterns.PATTERN_10);
        // namedPatterns.put("9", RegexPatterns.PATTERN_9);
        // namedPatterns.put("7", RegexPatterns.PATTERN_7);
        // namedPatterns.put("8", RegexPatterns.PATTERN_8);
        namedPatterns.put("1", RegexPatterns.PATTERN_1); //GENERIC_NAME_PATTERN_Tref
        namedPatterns.put("2", RegexPatterns.PATTERN_2); //GENERIC_NAME_PATTERN
        // namedPatterns.put("3", RegexPatterns.PATTERN_3);
        namedPatterns.put("4", RegexPatterns.PATTERN_4);
        namedPatterns.put("5", RegexPatterns.PATTERN_5);
        // namedPatterns.put("6", RegexPatterns.PATTERN_6);
        // namedPatterns.put("12", RegexPatterns.PATTERN_12);
        
    }

    public Map.Entry<String, Matcher> matchPattern(CSVArchiveListData archiveItem) {
        for (Map.Entry<String, Pattern> entry : namedPatterns.entrySet()) {
            Matcher matcher = entry.getValue().matcher(archiveItem.getArchiveName());
            if (matcher.matches()) {
                return Map.entry(entry.getKey(), matcher);
            } 
        }
        return null;
    }

    private String extractField(CSVArchiveListData archiveItem, String groupName) {
        Map.Entry<String, Matcher> patternMatch = matchPattern(archiveItem);
        if (patternMatch != null) {
            return patternMatch.getValue().group(groupName);
        }
        return "";
    }

    public String extractCourtReference(CSVArchiveListData archiveItem) {
        return extractField(archiveItem, "court");
    }

    public String extractDate(CSVArchiveListData archiveItem) {
        return extractField(archiveItem, "date");
    }

    public String extractURN(CSVArchiveListData archiveItem) {
        return extractField(archiveItem, "urn");
    }

    public String extractExhibitReference(CSVArchiveListData archiveItem) {
        return extractField(archiveItem, "exhibitRef");
    }

    public String extractDefendantLastName(CSVArchiveListData archiveItem) {
        return extractField(archiveItem, "defendantLastName");
    }

    public String extractWitnessFirstName(CSVArchiveListData archiveItem) {
        return extractField(archiveItem, "witnessFirstName");
    }

    public String extractRecordingVersion(CSVArchiveListData archiveItem) {
        return extractField(archiveItem, "versionType");
    }

    public String extractRecordingVersionNumber(CSVArchiveListData archiveItem) {
        return extractField(archiveItem, "versionNumber");
    }

    public String extractFileExtension(CSVArchiveListData archiveItem) {
        return extractField(archiveItem, "ext");
    }
    
}
