package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.util.RegexPatterns;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PatternMatcherService {
    private final LoggingService loggingService;

    public PatternMatcherService(LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    public Optional<Map.Entry<String, Matcher>> findMatchingPattern(String archiveName) {
        if (archiveName == null || archiveName.isBlank()) {
            loggingService.logWarning("Invalid or empty archive name received.");
            return Optional.empty();
        }

        // String sanitizedFiledName = cleanArchiveName(archiveName);
        loggingService.logDebug("Checking patterns for: %s", archiveName);

        // Check test patterns first
        for (Map.Entry<String, Pattern> testPattern : RegexPatterns.TEST_PATTERNS.entrySet()) {
            Matcher matcher = testPattern.getValue().matcher(archiveName);
            if (matcher.matches()) {
                loggingService.logDebug("Matched TEST pattern: %s", testPattern.getKey());
                return Optional.of(Map.entry(testPattern.getKey(), matcher));
            }
        }

        // Check named patterns
        for (Map.Entry<String, Pattern> validPattern : RegexPatterns.LEGITAMITE_PATTERNS.entrySet()) {
            Matcher matcher = validPattern.getValue().matcher(archiveName);
            if (matcher.matches()) {
                loggingService.logDebug("Matched VALID pattern: %s for file: %s", validPattern.getKey(), archiveName);
                return Optional.of(Map.entry(validPattern.getKey(), matcher));
            }
        }

        loggingService.logWarning("No pattern match found for file: %s", archiveName);
        return Optional.empty();
    }


    // private static String cleanArchiveName(String archiveName) {
    //     return archiveName
    //         .replaceAll("^QC[_\\d]?", "")
    //         .replaceAll("^QC(?![A-Za-z])", "")
    //         .replaceAll("[-_\\s]QC\\d*(?=\\.[a-zA-Z0-9]+$|$)", "")
    //         .replaceAll("[-_\\s]?(?:CP-Case|AS URN)[-_\\s]?$", "")
    //         .replaceAll("_(?=\\.[^.]+$)", "")
    //         .replaceAll("[-_\\s]{2,}", "-")
    //         .trim();
    // }
}
