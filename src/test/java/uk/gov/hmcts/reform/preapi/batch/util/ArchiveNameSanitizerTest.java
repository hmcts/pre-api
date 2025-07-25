package uk.gov.hmcts.reform.preapi.batch.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchiveNameSanitizerTest {

    @Test
    @DisplayName("Should return empty string when archive name is null or empty")
    void testNullOrEmpty() {
        assertEquals("", ArchiveNameSanitizer.sanitize(null));
        assertEquals("", ArchiveNameSanitizer.sanitize(""));
    }

    @Test
    @DisplayName("Should remove OLD or NEW prefix regardless of case")
    void testOldNewPrefixRemoval() {
        assertEquals("Case-123", ArchiveNameSanitizer.sanitize("OLD-Case-123"));
        assertEquals("Case_123", ArchiveNameSanitizer.sanitize("new_Case_123"));
    }

    @Test
    @DisplayName("Should remove suffixes like CP-Case or AS URN")
    void testSuffixRemoval() {
        assertEquals("Case123", ArchiveNameSanitizer.sanitize("Case123 CP-Case"));
        assertEquals("Case123", ArchiveNameSanitizer.sanitize("Case123 AS URN"));
    }

    @Test
    @DisplayName("Should remove underscore before file extension")
    void testUnderscoreBeforeExtension() {
        assertEquals("Recording.mp4", ArchiveNameSanitizer.sanitize("Recording_.mp4"));
    }

    @Test
    @DisplayName("Should collapse multiple dashes/underscores/spaces into one dash")
    void testCollapseDelimiters() {
        assertEquals("Part1-Part2", ArchiveNameSanitizer.sanitize("Part1__--  Part2"));
    }

    @Test
    @DisplayName("Should clean up extra dots and surrounding characters")
    void testDotPatternCleanup() {
        assertEquals("abc-xyz", ArchiveNameSanitizer.sanitize("abc.._ .xyz"));
    }

    @Test
    @DisplayName("Integration test: combination of sanitizations")
    void testFullSanitization() {
        String raw = "OLD  Case_CP-Case_ .mp4";
        String expected = "Case_Case-.mp4";
        assertEquals(expected, ArchiveNameSanitizer.sanitize(raw));
    }
}