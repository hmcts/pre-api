package uk.gov.hmcts.reform.preapi.batch.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordingUtilsTest {

    @Test
    @DisplayName("normalizeVersionType returns ORIG for null or unknown")
    void normalizeVersionTypeFallbacksToOrig() {
        assertThat(RecordingUtils.normalizeVersionType(null)).isEqualTo("ORIG");
        assertThat(RecordingUtils.normalizeVersionType("unknown"))
            .isEqualTo("ORIG");
    }

    @Test
    @DisplayName("normalizeVersionType returns ORIG or COPY for valid types")
    void normalizeVersionTypeHandlesValidTypes() {
        assertThat(RecordingUtils.normalizeVersionType("orig"))
            .isEqualTo("ORIG");
        assertThat(RecordingUtils.normalizeVersionType("Original"))
            .isEqualTo("ORIG");
        assertThat(RecordingUtils.normalizeVersionType("copy"))
            .isEqualTo("COPY");
    }

    @Test
    @DisplayName("getStandardizedVersionNumberFromType returns correct int")
    void getStandardizedVersionNumberFromType() {
        assertThat(RecordingUtils.getStandardizedVersionNumberFromType("orig")).isEqualTo(1);
        assertThat(RecordingUtils.getStandardizedVersionNumberFromType("copy")).isEqualTo(2);
    }

    @Test
    @DisplayName("getValidVersionNumber returns cleaned string or default")
    void getValidVersionNumber() {
        assertThat(RecordingUtils.getValidVersionNumber(null)).isEqualTo("1");
        assertThat(RecordingUtils.getValidVersionNumber(" ")).isEqualTo("1");
        assertThat(RecordingUtils.getValidVersionNumber(" 2 ")).isEqualTo("2");
    }

    @Test
    @DisplayName("compareVersionStrings handles equal, less, greater cases")
    void compareVersionStringsCases() {
        assertThat(RecordingUtils.compareVersionStrings("1", "1")).isEqualTo(0);
        assertThat(RecordingUtils.compareVersionStrings("1.2", "1.3")).isLessThan(0);
        assertThat(RecordingUtils.compareVersionStrings("2.0", "1.9")).isGreaterThan(0);
    }

    @Test
    @DisplayName("compareVersionStrings handles null and malformed")
    void compareVersionStringsFallbacksToZero() {
        assertThat(RecordingUtils.compareVersionStrings(null, "1")).isLessThan(0);
        assertThat(RecordingUtils.compareVersionStrings("junk", "1")).isLessThan(0);
        assertThat(RecordingUtils.compareVersionStrings("1.a", "1.0")).isEqualTo(0);
    }
}