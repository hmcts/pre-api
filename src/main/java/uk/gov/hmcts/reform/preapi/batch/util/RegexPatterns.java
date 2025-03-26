package uk.gov.hmcts.reform.preapi.batch.util;

import uk.gov.hmcts.reform.preapi.batch.config.Constants;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RegexPatterns {

    private RegexPatterns() {
    }

    public static final Pattern DIGIT_ONLY_PATTERN = Pattern.compile("^\\d+(_\\d+)*$");
    public static final Pattern S28_PATTERN = Pattern.compile(
        "^(?:S28[_\\s])[A-Za-z0-9_]+_\\d{15,18}(?:\\.(mp4|raw|mov|avi|mkv))?$",
        Pattern.CASE_INSENSITIVE
    );
    public static final Pattern UUID_FILENAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9]+_\\d{15}_\\d+_[0-9a-f]{8}[0-9a-f]{4}[0-9a-f]{4}[0-9a-f]{4}[0-9a-f]{12}(?:\\.(mp4|mov|avi|mkv))?$",
        Pattern.CASE_INSENSITIVE
    );
    public static final Pattern TEST_KEYWORDS_PATTERN = buildTestKeywordsPattern();


    public static final Map<String, Pattern> TEST_PATTERNS = Map.of(
        "Digit Only", DIGIT_ONLY_PATTERN,
        "Test Keyword", TEST_KEYWORDS_PATTERN,
        "S28 Pattern", S28_PATTERN,
        "UUID Pattern", UUID_FILENAME_PATTERN
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
    private static final String IGNORED_WORDS = "(?:QC|CP-Case|AS URN)";
    private static final String SEPARATOR_ONE = "[-_\\s]+";
    private static final String SEPARATOR_ZERO = "[-_\\s]?";
    private static final String OPTIONAL_PREFIX = "(?:\\d{1,5}[-_]?)?";

    private static final String DATE_PATTERN =
        "(?<date>\\d{6}|\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{2}-\\d{2}-\\d{4}-\\d{4})";
    private static final String COURT_PATTERN = "(?<court>[A-Za-z]+(?:d|fd)?)";
    // private static final String URN_PATTERN = "(?<urn>\\d+[A-Za-z]+\\d+)";
    // private static final String EXHIBIT_PATTERN = "(?<exhibitRef>(?:[A-Za-z]+\\d+|\\d{2}[A-Z]{2}\\d+|\\d+))?";

    private static final String URN_PATTERN = "(?<urn>[A-Za-z0-9]{11})";
    private static final String EXHIBIT_PATTERN = "(?<exhibitRef>[A-Za-z][A-Za-z0-9]{8})";
    private static final String VERSION_PATTERN =
        "(?<versionType>ORIG|COPY|CPY|ORG|ORI)(?:[-_\\s]*(?<versionNumber>\\d+(?:\\.\\d+)?))?";
    private static final String EXTENSION_PATTERN = "(?:\\.(?<ext>mp4|raw|RAW))?";
    private static final String NAMES_PATTERN = "(?<defendantLastName>[A-Za-z]+(?:[-\\s][A-Za-z]+)*)"
                                                + SEPARATOR_ONE + "(?<witnessFirstName>[A-Za-z]+)";


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
        + "(?:" + EXTENSION_PATTERN + ")?$"
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
        + "(?:" + EXTENSION_PATTERN + ")?$"
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
        + "(?:" + EXTENSION_PATTERN + ")?$"
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
        + "(?:" + EXTENSION_PATTERN + ")?$"
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
        + "(?:" + EXTENSION_PATTERN + ")?$"
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
        + "(?:" + EXTENSION_PATTERN + ")?$"
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
        + "(?:" + EXTENSION_PATTERN + ")?$"
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
        + "(?:[-_\\s](?<urn2>[A-Za-z0-9]+))?" + SEPARATOR_ZERO
        + "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR_ZERO + ")?"
        + NAMES_PATTERN + SEPARATOR_ZERO
        + VERSION_PATTERN
        + "(?:" + EXTENSION_PATTERN + ")?$"
    );

    /**
     * Pattern for date-time format recordings.
     * Format: DD-MM-YYYY-HHMM PostType Witness Defendant [.ext]
     */
    public static final Pattern DATE_TIME_PATTERN = Pattern.compile(
        "^(?<date>\\d{2}-\\d{2}-\\d{4}-\\d{4})" + SEPARATOR_ONE
        + "(?<exhibitRef>Post[A-Za-z]+)" + SEPARATOR_ONE
        + "(?<witnessFirstName>[A-Za-z0-9]+)" + SEPARATOR_ONE
        + "(?<defendantLastName>[A-Za-z0-9]+(?:[-\\s][A-Za-z0-9]+)*)"
        + "(?:" + EXTENSION_PATTERN + ")?$"
    );


    public static final Map<String, Pattern> LEGITAMITE_PATTERNS = Map.of(
        "Standard", RegexPatterns.STANDARD_PATTERN,
        "StandardWithNumbers", RegexPatterns.STANDARD_PATTERN_WITH_NUMBERS_PREFIX,
        "SpecificT", RegexPatterns.SPECIFIC_T_PATTERN,
        "SpecialCase", RegexPatterns.SPECIAL_CASE_PATTERN,
        "DoubleURN", RegexPatterns.DOUBLE_URN_NO_EXHIBIT_PATTERN,
        "DoubleExhibit", RegexPatterns.DOUBLE_EXHIBIT_NO_URN_PATTERN,
        "Prefix", RegexPatterns.PREFIX_PATTERN,
        "Flexible", RegexPatterns.FLEXIBLE_PATTERN
    );
}
