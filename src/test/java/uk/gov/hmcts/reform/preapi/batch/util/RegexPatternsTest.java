package uk.gov.hmcts.reform.preapi.batch.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegexPatternsTest {

    @Test
    void testDigitOnlyPattern() {
        assertTrue(RegexPatterns.DIGIT_ONLY_PATTERN.matcher("12345").matches());
        assertTrue(RegexPatterns.DIGIT_ONLY_PATTERN.matcher("12345_234325").matches());
        assertTrue(RegexPatterns.DIGIT_ONLY_PATTERN.matcher("12345_234325_234325").matches());
        assertFalse(RegexPatterns.DIGIT_ONLY_PATTERN.matcher("123a45").matches());
    }

    @Test
    void testS28Pattern() {
        assertTrue(RegexPatterns.S28_PATTERN.matcher("S28_ABC_123456789012345.mp4").matches());
        assertFalse(RegexPatterns.S28_PATTERN.matcher("S28_ABC_12345.mp4").matches());
    }

    @Test
    void testUuidFilenamePattern() {
        assertTrue(RegexPatterns.UUID_FILENAME_PATTERN
                       .matcher("abc_123456789012345_123_123e4567e89b12d3a456426655440000.mp4").matches());
        assertFalse(RegexPatterns.UUID_FILENAME_PATTERN
                        .matcher("abc_123456789012345_123_123e4567e89b12d3a45642665544000.mp4").matches());
    }

    @Test
    void testFilenamePattern() {
        assertTrue(RegexPatterns.FILENAME_PATTERN.matcher("0x1A2B3C_ABC_123_456_1A2B3C.mp4").matches());
        assertFalse(RegexPatterns.FILENAME_PATTERN.matcher("1A2B3C_ABC_123_456_1A2B3C.mp4").matches());
    }

    @Test
    void testTestKeywordsPattern() {
        assertTrue(RegexPatterns.TEST_KEYWORDS_PATTERN.matcher("This is a test keyword").matches());
        assertFalse(RegexPatterns.TEST_KEYWORDS_PATTERN.matcher("This is not a keyword").matches());
    }

    @Test
    void testTestPatterns() {
        for (Map.Entry<String, Pattern> entry : RegexPatterns.TEST_PATTERNS.entrySet()) {
            assertNotNull(entry.getKey());
            assertNotNull(entry.getValue());
        }
    }

    /*
        @Test
        void testStandardPattern() {
            assertTrue(RegexPatterns.STANDARD_PATTERN.matcher("Court 01-01-2020 URN Exhibit Defendant Witness ORIG.mp4").matches());
            assertFalse(RegexPatterns.STANDARD_PATTERN.matcher("Court 01-01-2020 URN Exhibit Defendant Witness.mp4").matches());
        }

        @Test
        void testStandardPatternWithNumbersPrefix() {
            assertTrue(RegexPatterns.STANDARD_PATTERN_WITH_NUMBERS_PREFIX.matcher("Court 01-01-2020 12345 URN Exhibit Defendant Witness ORIG.mp4").matches());
            assertFalse(RegexPatterns.STANDARD_PATTERN_WITH_NUMBERS_PREFIX.matcher("Court 01-01-2020 URN Exhibit Defendant Witness.mp4").matches());
        }

        @Test
        void testSpecificTPattern() {
            assertTrue(RegexPatterns.SPECIFIC_T_PATTERN.matcher("Court 01-01-2020 URN Exhibit Defendant Witness ORIG.mp4").matches());
            assertFalse(RegexPatterns.SPECIFIC_T_PATTERN.matcher("Court 01-01-2020 URN Defendant Witness ORIG.mp4").matches());
        }

        @Test
        void testSpecialCasePattern() {
            assertTrue(RegexPatterns.SPECIAL_CASE_PATTERN.matcher("Court 12345 URN Exhibit Defendant Witness ORIG_QC.mp4").matches());
            assertFalse(RegexPatterns.SPECIAL_CASE_PATTERN.matcher("Court 12345 URN Exhibit Defendant Witness ORIG.mp4").matches());
        }

        @Test
        void testDoubleUrnNoExhibitPattern() {
            assertTrue(RegexPatterns.DOUBLE_URN_NO_EXHIBIT_PATTERN.matcher("Court 01-01-2020 URN1 URN2 Defendant Witness ORIG.mp4").matches());
            assertFalse(RegexPatterns.DOUBLE_URN_NO_EXHIBIT_PATTERN.matcher("Court 01-01-2020 URN1 Defendant Witness ORIG.mp4").matches());
        }

        @Test
        void testDoubleExhibitNoUrnPattern() {
            assertTrue(RegexPatterns.DOUBLE_EXHIBIT_NO_URN_PATTERN.matcher("Court 01-01-2020 Exhibit1 Exhibit2 Defendant Witness ORIG.mp4").matches());
            assertFalse(RegexPatterns.DOUBLE_EXHIBIT_NO_URN_PATTERN.matcher("Court 01-01-2020 Exhibit1 Defendant Witness ORIG.mp4").matches());
        }

        @Test
        void testPrefixPattern() {
            assertTrue(RegexPatterns.PREFIX_PATTERN.matcher("S28 Court 01-01-2020 URN Exhibit Defendant Witness ORIG.mp4").matches());
            assertFalse(RegexPatterns.PREFIX_PATTERN.matcher("Court 01-01-2020 URN Exhibit Defendant Witness ORIG.mp4").matches());
        }

        @Test
        void testFlexiblePattern() {
            assertTrue(RegexPatterns.FLEXIBLE_PATTERN.matcher("Court 01-01-2020 URN Exhibit Defendant Witness ORIG.mp4").matches());
            assertFalse(RegexPatterns.FLEXIBLE_PATTERN.matcher("Court 01-01-2020 URN Defendant Witness ORIG.mp4").matches());
        }
    */
    @Test
    void testDateTimePattern() {
        assertTrue(RegexPatterns.DATE_TIME_PATTERN.matcher("01-01-2020-1200 PostType Witness Defendant.mp4").matches());
        assertFalse(RegexPatterns.DATE_TIME_PATTERN.matcher("01-01-2020 PostType Witness Defendant.mp4").matches());
    }
}
