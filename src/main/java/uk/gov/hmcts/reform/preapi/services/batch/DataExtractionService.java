package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.util.batch.RegexPatterns;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataExtractionService {

    private final Map<String, Pattern> namedPatterns = new LinkedHashMap<>();

    public DataExtractionService() {
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

}
