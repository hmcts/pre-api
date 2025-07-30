package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {PatternMatcherService.class})
public class PatternMatcherServiceTest {
    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private PatternMatcherService patternMatcherService;

    @Test
    void shouldMatchTestDigitOnlyPattern() {
        String fileName = "123456789.mp4";

        Optional<Map.Entry<String, Matcher>> result = patternMatcherService.findMatchingPattern(fileName);

        assertThat(result).isPresent();
        assertThat(result.get().getKey()).isEqualTo("Digit Only Extension");
    }

    // @Test
    // void shouldMatchTestS28Pattern() {
    //     String fileName = "S28_R112_123456789.mp4";

    //     Optional<Map.Entry<String, Matcher>> result = patternMatcherService.findMatchingPattern(fileName);

    //     assertThat(result).isPresent();
    //     assertThat(result.get().getKey()).isEqualTo("S28 Pattern");
    // }

    @Test
    void shouldMatchFilenamePattern() {
        String fileName = "0xABC123_FileName_123_456_DEF456.mp4";

        Optional<Map.Entry<String, Matcher>> result = patternMatcherService.findMatchingPattern(fileName);

        assertThat(result).isPresent();
        assertThat(result.get().getKey()).isEqualTo("Filename Pattern");
    }

    @Test
    void shouldReturnEmptyWhenNoMatch() {
        String fileName = "not_a_valid_file_name.mov";

        Optional<Map.Entry<String, Matcher>> result = patternMatcherService.findMatchingPattern(fileName);

        assertThat(result).isEmpty();
    }
}
