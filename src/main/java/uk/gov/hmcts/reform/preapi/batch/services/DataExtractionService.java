package uk.gov.hmcts.reform.preapi.batch.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.util.RegexPatterns;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(DataExtractionService.class);
    private final Map<String, Pattern> namedPatterns;

    public DataExtractionService() {
        this.namedPatterns = initializePatterns();
    }

    // =========================
    // Pattern Management
    // =========================

    /**
     * Returns a map of named regex patterns used for extracting data from archive file names.
     * The patterns are ordered by priority (e.g., more specific patterns come first).
     * @return A map of pattern names to compiled regex patterns.
     */
    private Map<String, Pattern> initializePatterns() {
        Map<String, Pattern> namedPatterns = new LinkedHashMap<>();
        namedPatterns.put("Specific", RegexPatterns.SPECIFIC_T_PATTERN);
        namedPatterns.put("Special case",RegexPatterns.SPECIAL_CASE_PATTERN);
        namedPatterns.put("Numbers", RegexPatterns.STANDARD_PATTERN_WITH_NUMBERS);
        namedPatterns.put("Standard", RegexPatterns.STANDARD_PATTERN);
        namedPatterns.put("Flexible", RegexPatterns.FLEXIBLE_PATTERN);
        namedPatterns.put("Prefix", RegexPatterns.PREFIX_PATTERN);
        namedPatterns.put("Double URN", RegexPatterns.DOUBLE_URN_NO_EXHIBIT_PATTERN);
        namedPatterns.put("Double Exhibit", RegexPatterns.DOUBLE_EXHIBIT_NO_URN_PATTERN);
        namedPatterns.put("Match All", RegexPatterns.MATCH_ALL_PATTERN);
        namedPatterns.put("DATE_TIME_PATTERN", RegexPatterns.DATE_TIME_PATTERN);

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
        String cleanedArchiveName = cleanArchiveName(archiveItem.getArchiveName());

        for (Map.Entry<String, Pattern> entry : namedPatterns.entrySet()) {
            Matcher matcher = entry.getValue().matcher(cleanedArchiveName);
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
                logger.warn("Group '{}' not found in pattern '{}' for file: {}", 
                groupName, patternMatch.getKey(), archiveItem.getArchiveName());
                return "";
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

    public ExtractedMetadata extractMetadata(CSVArchiveListData archiveItem) {
        return new ExtractedMetadata(
            extractField(archiveItem, "court"),
            extractField(archiveItem, "date"),
            extractField(archiveItem, "urn"),
            extractField(archiveItem, "exhibitRef"),
            extractField(archiveItem, "defendantLastName"),
            extractField(archiveItem, "witnessFirstName"),
            extractField(archiveItem, "versionType"),
            extractField(archiveItem, "versionNumber"),
            extractField(archiveItem, "ext"),
            archiveItem.getCreateTimeAsLocalDateTime(),  
            archiveItem.getDuration(),  
            archiveItem.getFileName(),  
            archiveItem.getFileSize()
        );
    }

    private static String cleanArchiveName(String archiveName) {
        return archiveName
            .replaceAll("^QC[_\\d]?", "") 
            .replaceAll("^QC(?![A-Za-z])", "")  
            .replaceAll("[-_\\s]QC\\d*(?=\\.[a-zA-Z0-9]+$|$)", "")  
            .replaceAll("[-_\\s]?(?:CP-Case|AS URN)[-_\\s]?$", "")
            .replaceAll("_(?=\\.[^.]+$)", "")  
            .replaceAll("[-_\\s]{2,}", "-")  
            .trim();
    }
}
