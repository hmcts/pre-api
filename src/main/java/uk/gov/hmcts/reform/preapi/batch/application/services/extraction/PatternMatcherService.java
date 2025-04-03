package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public PatternMatcherService(final LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    public Optional<Map.Entry<String, Matcher>> findMatchingPattern(String archiveName) {
        Optional<Map.Entry<String, Matcher>> testMatch = findMatch(archiveName, RegexPatterns.TEST_PATTERNS, "TEST");
        if (testMatch.isPresent()) {
            return testMatch;
        }

        Optional<Map.Entry<String, Matcher>> validMatch =
            findMatch(archiveName, RegexPatterns.LEGITAMITE_PATTERNS, "VALID");
        if (validMatch.isEmpty()) {
            return Optional.empty();
        }

        return validMatch;
    }

    private Optional<Map.Entry<String, Matcher>> findMatch(
            String archiveName,
            Map<String, Pattern> patternMap,
            String patternType
    ) {
        for (Map.Entry<String, Pattern> entry : patternMap.entrySet()) {
            Matcher matcher = entry.getValue().matcher(archiveName);
            if (matcher.matches()) {
                String patternName = entry.getKey();
                loggingService.logDebug("Matched %s pattern: %s for file: %s",
                        patternType, patternName, archiveName);
                return Optional.of(Map.entry(patternName, matcher));
            }
        }

        return Optional.empty();
    }
}
