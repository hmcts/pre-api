package uk.gov.hmcts.reform.preapi.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.reform.preapi.utils.InputSanitizerUtils;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class InputSanitizerUtilsTest {

    //TODO: Add more unit tests if needed

    @ParameterizedTest
    @ValueSource(strings = {"<a>TEST</a>", "<b>TEST</b>", "<i>TEST</i>", "<img src='x' onerror='alert(1)'>TEST</img>",
        "<svg>TEST</svg>"})
    public void shouldSanitizeHtml(String input) {
        String expectedOutput = "TEST";
        String actualOutput = InputSanitizerUtils.sanitize(input);

        assertThat(actualOutput).isEqualTo(expectedOutput);
    }

    @Test
    public void shouldSanitizeScriptTags() {
        String input =
            """
            "><script>alert("TEST")</script>
            """;
        String expectedOutput = "\">";

        String actualOutput = InputSanitizerUtils.sanitize(input);

        assertThat(actualOutput).isEqualTo(expectedOutput);
    }
}
