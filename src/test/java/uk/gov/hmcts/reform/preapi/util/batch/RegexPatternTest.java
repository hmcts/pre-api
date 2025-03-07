// package uk.gov.hmcts.reform.preapi.util.batch;

// import org.junit.jupiter.api.Test;

// import uk.gov.hmcts.reform.preapi.batch.util.RegexPatterns;

// import java.util.regex.Matcher;

// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertTrue;

// class RegexPatternTest {

//     @Test
//     void testPattern1MatchesValidInput() {
//         String testString = "Livrpl-191014-05E42881516-T20197346-ABLEWHITE-KATRINA-ORIGQC.mp4";

//         Matcher matcher = RegexPatterns.PATTERN_1.matcher(testString);

//         assertTrue(matcher.matches(), "Pattern 2 should match the test string");

//         assertEquals("Livrpl", matcher.group("court"), "Court name mismatch");
//         assertEquals("191014", matcher.group("date"), "Date mismatch");
//         assertEquals("05E42881516", matcher.group("urn"), "URN mismatch");
//         assertEquals("T20197346", matcher.group("exhibitRef"), "Exhibit reference mismatch");
//         assertEquals("ABLEWHITE", matcher.group("witnessFirstName"), "Witness first name mismatch");
//         assertEquals("KATRINA", matcher.group("defendantLastName"), "Defendant last name mismatch");
//         assertEquals("ORIG", matcher.group("versionType"), "Version type mismatch");
//     }   

//     @Test
//     void testPattern2MatchesValidInput() {
//         String testString = "Bristl-210819-52SB0384220-U20210453-PADDOCK-ELENOR-ORIG";

//         Matcher matcher = RegexPatterns.PATTERN_2.matcher(testString);

//         assertTrue(matcher.matches(), "Pattern 2 should match the test string");

//         assertEquals("Bristl", matcher.group("court"), "Court name mismatch");
//         assertEquals("210819", matcher.group("date"), "Date mismatch");
//         assertEquals("52SB0384220", matcher.group("urn"), "URN mismatch");
//         assertEquals("U20210453", matcher.group("exhibitRef"), "Exhibit reference mismatch");
//         assertEquals("ELENOR", matcher.group("witnessFirstName"), "Witness first name mismatch");
//         assertEquals("PADDOCK", matcher.group("defendantLastName"), "Defendant last name mismatch");
//         assertEquals("ORIG", matcher.group("versionType"), "Version type mismatch");
//     }   

    
// }
