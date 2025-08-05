package uk.gov.hmcts.reform.preapi.batch.util;

import lombok.experimental.UtilityClass;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@UtilityClass
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public final class RegexPatterns {
    public static final Pattern NO_DIGIT_PATTERN = Pattern.compile("^[^\\d]+\\.(mp4)$",
        Pattern.CASE_INSENSITIVE);

    public static final Pattern DIGIT_ONLY_EXT_PATTERN = Pattern.compile("^\\d+(?>_\\d+)*\\.mp4$");

    public static final Pattern DIGIT_ONLY_NO_EXT_PATTERN =
            Pattern.compile("^\\d+(?:_\\d+)++$", Pattern.CASE_INSENSITIVE);

    public static final Pattern S28_PATTERN = Pattern.compile(
        "^S?28.*?(VMR\\d+)?[_\\s-]*\\d{9,20}.*\\.(mp4|raw|mov|avi|mkv)$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern VMR_TIMESTAMP_PATTERN = Pattern.compile(
        "^[A-Z\\s]+VMR_\\d{15,21}$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern UUID_FILENAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9]+_\\d{15}_\\d+_[0-9a-f]{8}[0-9a-f]{4}[0-9a-f]{4}[0-9a-f]{4}[0-9a-f]{12}(?:\\.(mp4|mov|avi|mkv))?$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern FILENAME_PATTERN = Pattern.compile(
        "^0x[A-Fa-f0-9]+_[A-Za-z0-9]+_\\d+_\\d+_[A-Fa-f0-9]+(?:\\.(?:mp4|raw|mov|avi|mkv))?$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern S28_VMR_TEST_PATTERN = Pattern.compile(
        "^S?28.*?(VMR\\d+)?[_\\s-]*\\d{9,20}.*(?:\\.(mp4|raw|mov|avi|mkv))?$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern SIMPLE_VMR_TEST_PATTERN = Pattern.compile(
        "^vmr\\.[a-z]+_\\d{18}(?:\\.(mp4|raw|mov|avi|mkv))?$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern SIMPLE_BATCH_PATTERN = Pattern.compile(
        "^\\s*batch\\s*\\d+_\\d{17,20}\\s*$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern POSTRMX_PATTERN = Pattern.compile(
        "^\\d{2}-\\d{2}-\\d{4}-\\d{4}[-_](Post[A-Za-z0-9_\\-]+)\\.(mp4|raw|mov|avi|mkv)$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern VMR_WITH_DATE_PATTERN = Pattern.compile(
        "^CR#(?<caseId>\\d+)[-_]VMR(?<vmrNumber>\\d+)[-_](?<date>\\d{2}\\.\\d{2}\\.\\d{4})(?:\\.(?<ext>mp4|raw))?$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern UUID_STYLE_R_PREFIX_PATTERN = Pattern.compile(
        "^R[a-f0-9]{32}$", Pattern.CASE_INSENSITIVE
    );


    public static final Pattern SNOW_MORNING_CHECKS_PATTERN = Pattern.compile(
        "^SNOW\\s*Morning\\s*Checks\\s*\\d{4}\\s*\\d{2}\\s*\\d{2}\\s*VMR\\d+(?:\\.mp4)?$",
        Pattern.CASE_INSENSITIVE 
    );

    public static final Pattern S28_MORNING_CHECKS_DDMMYYYY_PATTERN = Pattern.compile(
        "^\\s*S?28\\s+Morning\\s+Checks\\s+(?:\\d{8}|\\d{2}[-/ ]\\d{2}[-/ ]\\d{4})(?:\\.mp4)?\\s*$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern QC_FILENAME_PATTERN = Pattern.compile(".*QC.*", Pattern.CASE_INSENSITIVE);

    public static final Pattern TEST_KEYWORDS_PATTERN = buildTestKeywordsPattern();


    public static final Map<String, Pattern> TEST_PATTERNS = Map.ofEntries(
        Map.entry("Digit Only Extension", DIGIT_ONLY_EXT_PATTERN),
        Map.entry("Digit Only No Ext", DIGIT_ONLY_NO_EXT_PATTERN),
        Map.entry("Test Keyword", TEST_KEYWORDS_PATTERN),
        Map.entry("S28 Pattern", S28_PATTERN),
        Map.entry("VMR Test Pattern", S28_VMR_TEST_PATTERN),
        Map.entry("S28 Morning checks",S28_MORNING_CHECKS_DDMMYYYY_PATTERN),
        Map.entry("UUID Pattern", UUID_FILENAME_PATTERN),
        Map.entry("Filename Pattern", FILENAME_PATTERN),
        Map.entry("QC Filename Pattern", QC_FILENAME_PATTERN),
        Map.entry("No Digit Pattern", NO_DIGIT_PATTERN),
        Map.entry("Batch Pattern", SIMPLE_BATCH_PATTERN),
        Map.entry("VMR Timestamp Pattern",VMR_TIMESTAMP_PATTERN),
        Map.entry("VMR Simple Pattern", SIMPLE_VMR_TEST_PATTERN),
        Map.entry("Postrmx Pattern", POSTRMX_PATTERN),
        Map.entry("UUID R Prefix Pattern",UUID_STYLE_R_PREFIX_PATTERN),
        Map.entry("VMR with date pattern", VMR_WITH_DATE_PATTERN),
        Map.entry("Snow morning pattern",SNOW_MORNING_CHECKS_PATTERN)
    );

    private static Pattern buildTestKeywordsPattern() {
        Set<String> keywords = Constants.TEST_KEYWORDS;

        String keywordRegex = keywords.stream()
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));

        return Pattern.compile(".*(" + keywordRegex + ").*", Pattern.CASE_INSENSITIVE);
    }

    // =========================
    // Common Pattern Components
    // =========================
    private static final String IGNORED_WORDS = "(?:QC|CP-Case|CP CASE|-CP-|AS URN)";
    private static final String SEPARATOR_ONE = "[-_\\s]+";
    private static final String SEPARATOR_ZERO = "[-_\\s]?";
    private static final String OPTIONAL_PREFIX = "(?:\\d{1,5}[-_\\s])?";

    private static final String DATE_PATTERN =
        "(?<date>\\d{6}|\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{2}-\\d{2}-\\d{4}-\\d{4})";
    private static final String COURT_PATTERN = "(?<court>[A-Za-z]+(?:d|fd)?)";
    private static final String URN_PATTERN = "(?<urn>[A-Za-z0-9]{2,14})";

    private static final String EXHIBIT_PATTERN = "(?<exhibitRef>[A-Za-z][A-Za-z0-9]{6,9})";
    private static final String VERSION_PATTERN =
        "(?:(?<versionType>ORIG|COPY|CPY|ORG|ORI|OR|CO|COP)(?:[-_\\s]*(?<versionNumber>\\d+(?:\\.\\d+)?))?)?";
    private static final String EXTENSION_PATTERN = "(?i)(?:\\.(?<ext>mp4|raw))?";

    private static final String NAMES_PATTERN = "(?<defendantLastName>(?>[A-Za-z']+)(?>[-\\s][A-Za-z0-9&]+)*)"
                                                + SEPARATOR_ONE
                                                + "(?<witnessFirstName>[?>A-Za-z0-9&']+(?>[-'\\s][A-Za-z]+)*)";
    /**
     * Standard pattern for most common recording names.
     * Format: Court Date URN [Exhibit] Defendant Witness Version [.ext]
     */
    public static final Pattern STANDARD_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + URN_PATTERN + SEPARATOR_ONE
        + "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR_ONE + ")?"
        + NAMES_PATTERN + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$"
    );

    /**
     * Standard pattern with optional numeric prefix.
     * Format: [Number-]Court Date URN [Exhibit] Defendant Witness Version [.ext]
     */
    public static final Pattern STANDARD_PATTERN_WITH_NUMBERS_PREFIX = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + OPTIONAL_PREFIX
        + URN_PATTERN + SEPARATOR_ONE
        + "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR_ONE + ")?"
        + NAMES_PATTERN + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$"
    );

    /**
     * Pattern for T-prefixed exhibit references.
     * Format: Court Date URN T-Exhibit Defendant Witness Version [.ext]
     */
    public static final Pattern SPECIFIC_T_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + URN_PATTERN + SEPARATOR_ONE
        + EXHIBIT_PATTERN + SEPARATOR_ONE
        + NAMES_PATTERN + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$"
    );

    /**
     * Pattern for 5-digit date format (legacy).
     * Format: Court Date URN T-Exhibit Defendant Witness Version [_QC] [.ext]
     */
    public static final Pattern SPECIAL_CASE_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + "(?<date>\\d{5})" + SEPARATOR_ONE
        + URN_PATTERN + SEPARATOR_ONE
        + EXHIBIT_PATTERN + SEPARATOR_ONE
        + NAMES_PATTERN + SEPARATOR_ONE
        + VERSION_PATTERN
        + "(?:_QC)?"
        + EXTENSION_PATTERN + "$"
    );

    /**
     * Pattern for cases with two URNs.
     * Format: Court Date URN1 URN2 Defendant Witness Version [.ext]
     */
    public static final Pattern DOUBLE_URN_NO_EXHIBIT_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + URN_PATTERN + SEPARATOR_ONE
        + "(?<urn2>\\d+[A-Za-z]{1,2}\\d+)" + SEPARATOR_ONE
        + NAMES_PATTERN + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$"
    );

    /**
     * Pattern for cases with two exhibits.
     * Format: Court Date [URN] Exhibit1 Exhibit2 Defendant Witness Version [.ext]
     */
    public static final Pattern DOUBLE_EXHIBIT_NO_URN_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + "(?!\\d+[A-Za-z]+\\d+)"
        + EXHIBIT_PATTERN + SEPARATOR_ONE
        + "(?<exhibitRef2>[A-Za-z]*\\d+)" + SEPARATOR_ONE
        + NAMES_PATTERN + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$"
    );


    /**
     * Pattern for files with S28/NEW/QC prefixes.
     * Format: [S28/NEW/QC] Court Date URN [Exhibit] Defendant Witness Version [.ext]
     */
    public static final Pattern PREFIX_PATTERN = Pattern.compile(
        "^(?:(?:S28|NEW|QC)[_\\s-]+)?"
        + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + URN_PATTERN + SEPARATOR_ONE
        + "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR_ONE + ")?"
        + NAMES_PATTERN + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$"
    );

    public static final Pattern POST_URN_PREFIX_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + URN_PATTERN + SEPARATOR_ONE
        + "(?:S28[-_\\s]+)?"
        + NAMES_PATTERN + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$"
    );

    /**
     * Combined flexible pattern that handles both standard and legacy formats.
     * This pattern combines the functionality of the previous FLEXIBLE_PATTERN and MATCH_ALL_PATTERN.
     * Format: Court Date [URN] [URN2] [Exhibit] Defendant Witness Version [.ext]
     */
    public static final Pattern FLEXIBLE_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ZERO
        + URN_PATTERN
        + "(?<urn2>[A-Za-z0-9]{11})" + SEPARATOR_ZERO
        + "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR_ZERO + ")?"
        + NAMES_PATTERN + SEPARATOR_ZERO
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$"
    );

    /**
     * Pattern for date-time format recordings.
     * Format: DD-MM-YYYY-HHMM PostType Witness Defendant [.ext]
     */
    public static final Pattern DATE_TIME_PATTERN = Pattern.compile(
        "^(?<date>\\d{2}-\\d{2}-\\d{4}-\\d{4})" + SEPARATOR_ONE
        + "(?<exhibitRef>Post[A-Za-z]+)" + SEPARATOR_ONE
        + "(?<witnessFirstName>[A-Za-z0-9]+)" + SEPARATOR_ONE
        + "(?<defendantLastName>[A-Za-z0-9]+(?>[-\\s][A-Za-z0-9]+)*)"
        + EXTENSION_PATTERN + "$"
    );

    public static final Pattern URN_EXTRA_ID_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + URN_PATTERN + SEPARATOR_ONE
        + "(?<extraId>\\d{6,})" + SEPARATOR_ONE
        + NAMES_PATTERN + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$"
    );

    public static final Pattern DOTS_IN_NAME_PATTERN = Pattern.compile(
        "^(?<court>[A-Za-z]+)" + SEPARATOR_ONE
        + "(?<date>\\d{6})" + SEPARATOR_ONE
        + "(?<urn>[A-Za-z0-9]+)" + SEPARATOR_ONE
        + "(?<exhibitRef>[A-Za-z]\\d{6,9})" + SEPARATOR_ONE
        + "(?<defendantLastName>[A-Za-z'\\.\\-\\s]+)" + SEPARATOR_ONE
        + "(?<witnessFirstName>[A-Za-z'\\.\\-\\s]+)" + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern DOUBLE_URN_DOT_WITNESS_PATTERN = Pattern.compile(
        "^"
        + COURT_PATTERN + SEPARATOR_ONE                 // e.g., Aylsby
        + "(?<date>\\d{6})" + SEPARATOR_ONE             // e.g., 220826
        + URN_PATTERN + SEPARATOR_ONE                   // URN1 e.g., 43SS02412222
        + "(?<urn2>\\d+[A-Za-z]{1,2}\\d+)" + SEPARATOR_ONE  // URN2 e.g., 43SS02412
        + "(?<defendantLastName>(?>[A-Za-z']+)(?>[-\\s][A-Za-z0-9&]+)*)" + SEPARATOR_ONE
        + "\\." + SEPARATOR_ONE                         // witness is a single dot
        + VERSION_PATTERN                               // ORIG / COPY / etc (+ optional number)
        + EXTENSION_PATTERN + "$",                      // optional .mp4/.raw (per your def)
        Pattern.CASE_INSENSITIVE
        );

    public static final Pattern PLUS_IN_NAME_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + URN_PATTERN + SEPARATOR_ONE
        + EXHIBIT_PATTERN + SEPARATOR_ONE
        + "(?<defendantLastName>[A-Za-z0-9&+'\\s-]+)" + SEPARATOR_ONE
        + "(?<witnessFirstName>[A-Za-z0-9&+'\\s-]+)" + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern NO_URN_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + "\\." + SEPARATOR_ONE
        + EXHIBIT_PATTERN + SEPARATOR_ONE
        + NAMES_PATTERN + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern NO_EXHIBIT_DOT_SEPARATOR_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + URN_PATTERN + "[-_\\s\\.]+"
        + "(?<defendantLastName>[A-Za-z'\\-\\s]+)" + SEPARATOR_ONE
        + "(?<witnessFirstName>[A-Za-z'\\-\\s]+)" + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern PREFIX_IN_EXHIBIT_POSITION_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + URN_PATTERN + SEPARATOR_ONE
        + "(?:S?28|NEW|QC)" + SEPARATOR_ONE
        + "(?<defendantLastName>[A-Za-z'\\-\\s]+)" + SEPARATOR_ONE
        + "(?<witnessFirstName>[A-Za-z'\\-\\s]+)" + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern DOUBLE_DATE_PATTERN = Pattern.compile(
        "^"
        + "(?<court>[A-Za-z]+(?:d|fd)?)" + SEPARATOR_ONE
        + "(?<date>\\d{6}|\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{2}-\\d{2}-\\d{4}-\\d{4})" + SEPARATOR_ONE
        + "(?<court2>[A-Za-z]+(?:d|fd)?)" + SEPARATOR_ONE
        +  "(?<date2>\\d{6}|\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{2}-\\d{2}-\\d{4}-\\d{4})" + SEPARATOR_ONE
        + URN_PATTERN + SEPARATOR_ONE
        + "(?<defendantLastName>[A-Za-z]+)" + SEPARATOR_ONE
        + "(?<witnessFirstName>Witness\\d+)" + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern STANDARD_WITNESS_PARENS_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE
        + DATE_PATTERN + SEPARATOR_ONE
        + URN_PATTERN + SEPARATOR_ONE
        + "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR_ONE + ")?"
        + "(?<defendantLastName>(?>[A-Za-z']++)(?>[-\\s][A-Za-z0-9&]++)*)" + SEPARATOR_ONE
        + "(?<witnessFirstName>[A-Za-z0-9&']++(?:[-'\\s][A-Za-z]++)*(?:\\s*\\([A-Za-z0-9&+'\\-]{1,6}\\))?)"
        + SEPARATOR_ONE
        + VERSION_PATTERN
        + EXTENSION_PATTERN + "$",
        Pattern.CASE_INSENSITIVE
    );

    public static final Map<String, Pattern> LEGITAMITE_PATTERNS = Map.ofEntries(
        Map.entry("Standard", RegexPatterns.STANDARD_PATTERN),
        Map.entry("StandardWithNumbers", RegexPatterns.STANDARD_PATTERN_WITH_NUMBERS_PREFIX),
        Map.entry("SpecificT", RegexPatterns.SPECIFIC_T_PATTERN),
        Map.entry("SpecialCase", RegexPatterns.SPECIAL_CASE_PATTERN),
        Map.entry("DoubleURN", RegexPatterns.DOUBLE_URN_NO_EXHIBIT_PATTERN),
        Map.entry("DoubleExhibit", RegexPatterns.DOUBLE_EXHIBIT_NO_URN_PATTERN),
        Map.entry("Prefix", RegexPatterns.PREFIX_PATTERN),
        Map.entry("Post", RegexPatterns.POST_URN_PREFIX_PATTERN),
        Map.entry("Flexible", RegexPatterns.FLEXIBLE_PATTERN),
        Map.entry("ExtraId", RegexPatterns.URN_EXTRA_ID_PATTERN),
        Map.entry("DotsInName", RegexPatterns.DOTS_IN_NAME_PATTERN),
        Map.entry("DotWitness", RegexPatterns.DOUBLE_URN_DOT_WITNESS_PATTERN),
        Map.entry("PlusInName", RegexPatterns.PLUS_IN_NAME_PATTERN),
        Map.entry("NoUrnPattern", RegexPatterns.NO_URN_PATTERN),
        Map.entry("NoExhibitPattern", RegexPatterns.NO_EXHIBIT_DOT_SEPARATOR_PATTERN),
        Map.entry("PrefixInExhibit", RegexPatterns.PREFIX_IN_EXHIBIT_POSITION_PATTERN),
        Map.entry("DoubeDatePattern", RegexPatterns.DOUBLE_DATE_PATTERN),
        Map.entry("Participants Paren",RegexPatterns.STANDARD_WITNESS_PARENS_PATTERN)
    );
}
