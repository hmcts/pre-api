package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
// import uk.gov.hmcts.reform.preapi.entities.batch.UnifiedArchiveData;
import uk.gov.hmcts.reform.preapi.util.batch.RegexPatterns;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataExtractionService {

    public DataExtractionService() {
    }

    // =========================
    // Pattern Management
    // =========================

    /**
     * Returns a map of named regex patterns used for extracting data from archive file names.
     * The patterns are ordered by priority (e.g., more specific patterns come first).
     * @return A map of pattern names to compiled regex patterns.
     */
    private Map<String, Pattern> getNamedPatterns() {
        Map<String, Pattern> namedPatterns = new LinkedHashMap<>();
        namedPatterns.put("11", RegexPatterns.PATTERN_11);
        namedPatterns.put("10", RegexPatterns.PATTERN_10);
        namedPatterns.put("1", RegexPatterns.PATTERN_1); // GENERIC_NAME_PATTERN_Tref
        namedPatterns.put("2", RegexPatterns.PATTERN_2); // GENERIC_NAME_PATTERN
        namedPatterns.put("4", RegexPatterns.PATTERN_4);
        namedPatterns.put("5", RegexPatterns.PATTERN_5);
        return namedPatterns;
    }

    // =========================
    // Pattern Matching
    // =========================

    /**
     * Matches the archive file name against regex patterns and returns the first match.
     * @param archiveItem The CSV archive data containing the file name to match.
     * @return A map entry containing the pattern name and the corresponding matcher, or null if no match is found.
     */
    public Map.Entry<String, Matcher> matchPattern(CSVArchiveListData archiveItem) {
        for (Map.Entry<String, Pattern> entry : getNamedPatterns().entrySet()) {
            Matcher matcher = entry.getValue().matcher(archiveItem.getArchiveName());
            if (matcher.matches()) {
                return Map.entry(entry.getKey(), matcher);
            } 
        }
        return null;
    }

    // =========================
    // Field Extraction
    // =========================

    /**
     * Extracts a specific field from the archive file name using the matched regex pattern.
     * @param archiveItem The CSV archive data containing the file name.
     * @param groupName   The name of the regex group to extract.
     * @return The extracted field value, or an empty string if no match is found.
     * @throws IllegalStateException If the specified group is not found in the matched pattern.
     */
    private String extractField(CSVArchiveListData archiveItem, String groupName) {
        Map.Entry<String, Matcher> patternMatch = matchPattern(archiveItem);
        if (patternMatch != null) {
            try {
                return patternMatch.getValue().group(groupName);
            } catch (Exception e) {
                throw new IllegalStateException("Group '" + groupName + "' not found in pattern '" 
                    + patternMatch.getKey() + "'", e);
            }
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
