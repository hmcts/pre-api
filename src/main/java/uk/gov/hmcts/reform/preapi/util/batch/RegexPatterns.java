package uk.gov.hmcts.reform.preapi.util.batch;

import java.util.regex.Pattern;

public final class RegexPatterns {

    private RegexPatterns() {
    }
    
    private static final String IGNORED_WORDS = "(?:QC|CP-Case|AS URN)";
    private static final String SEPARATOR_ONE = "[-_\\s]+";
    private static final String SEPARATOR_ZERO = "[-_\\s]?";
    private static final String OPTIONAL_PREFIX = "(?:\\d{1,5}[-_]?)?";
     private static final String DATE_PATTERN = "(?<date>\\d{6}|\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{2}-\\d{2}-\\d{4}-\\d{4})";
    private static final String COURT_PATTERN = "(?<court>[A-Za-z]+(?:d|fd)?)";
    private static final String URN_PATTERN = "(?<urn>\\d+[A-Za-z]+\\d+)";
    private static final String OPTIONAL_SECOND_URN_PATTERN = "(?:[-_\\s]+(?<urn2>\\d+[A-Za-z]{1,2}\\d+))?";
    private static final String EXHIBIT_PATTERN = "(?<exhibitRef>(?:[A-Za-z]+\\d+|\\d{2}[A-Z]{2}\\d+|\\d+))?";
    private static final String VERSION_PATTERN = "(?<versionType>ORIG|COPY|CPY|ORG|ORI)(?:[-_\\s]*(?<versionNumber>\\d+(?:\\.\\d+)?))?";
    private static final String EXTENSION_PATTERN = "(?:\\.(?<ext>mp4|raw|RAW))?";
    private static final String NAMES_PATTERN = "(?<defendantLastName>[A-Za-z]+(?:[-\\s][A-Za-z]+)*)" 
                                                + SEPARATOR_ONE + "(?<witnessFirstName>[A-Za-z]+)";

    
    public static final Pattern DATE_TIME_PATTERN = Pattern.compile(
        "^(?<date>\\d{2}-\\d{2}-\\d{4}-\\d{4})" + SEPARATOR_ONE +
        "(?<exhibitRef>Post[A-Za-z]+)" + SEPARATOR_ONE +
        "(?<witnessFirstName>[A-Za-z0-9]+)" + SEPARATOR_ONE +
        "(?<defendantLastName>[A-Za-z0-9]+(?:[-\\s][A-Za-z0-9]+)*)" +
        "(?:" + EXTENSION_PATTERN + ")?$"
    );


    public static final Pattern SPECIFIC_T_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE 
        + DATE_PATTERN + SEPARATOR_ONE 
        + "(?<urn>\\d+[A-Za-z]+\\d+)" + SEPARATOR_ONE 
        + "(?<exhibitRef>T\\d+)" + SEPARATOR_ONE 
        + NAMES_PATTERN + SEPARATOR_ONE 
        + VERSION_PATTERN 
        + "(?:" + EXTENSION_PATTERN + ")?$"
    );                                            

    public static final Pattern STANDARD_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE 
        + DATE_PATTERN + SEPARATOR_ONE 
        + URN_PATTERN + SEPARATOR_ONE 
        + "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR_ONE + ")?" 
        + NAMES_PATTERN + SEPARATOR_ONE 
        + VERSION_PATTERN 
        + "(?:" + EXTENSION_PATTERN + ")?$"
    );

    public static final Pattern STANDARD_PATTERN_WITH_NUMBERS = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE +
        DATE_PATTERN + SEPARATOR_ONE +
        OPTIONAL_PREFIX + 
        URN_PATTERN + SEPARATOR_ONE +
        "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR_ONE + ")?" +
        NAMES_PATTERN + SEPARATOR_ONE +
        VERSION_PATTERN +
        "(?:" + EXTENSION_PATTERN + ")?$"
    );

    public static final Pattern FLEXIBLE_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE 
        + DATE_PATTERN + SEPARATOR_ONE 
        + URN_PATTERN 
        + OPTIONAL_SECOND_URN_PATTERN + SEPARATOR_ONE 
        + "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR_ONE + ")?" 
        + NAMES_PATTERN + SEPARATOR_ONE 
        + VERSION_PATTERN 
        + "(?:" + EXTENSION_PATTERN + ")?$"
    );

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

    public static final Pattern DOUBLE_URN_NO_EXHIBIT_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE 
        + DATE_PATTERN + SEPARATOR_ONE 
        + URN_PATTERN + SEPARATOR_ONE 
        + "(?<urn2>\\d+[A-Za-z]{1,2}\\d+)" + SEPARATOR_ONE  
        + NAMES_PATTERN + SEPARATOR_ONE 
        + VERSION_PATTERN 
        + "(?:" + EXTENSION_PATTERN + ")?$"
    );

    public static final Pattern DOUBLE_EXHIBIT_NO_URN_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE 
        + DATE_PATTERN + SEPARATOR_ONE 
        + "(?<urn>\\d+[A-Za-z]+\\d+)?" + SEPARATOR_ZERO 
        + "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR_ONE + ")?" 
        + "(?<exhibitRef2>(?:[TU]\\d+|\\d{2}[A-Z]{2}\\d+|\\d+))" + SEPARATOR_ONE   
        + NAMES_PATTERN + SEPARATOR_ONE 
        + VERSION_PATTERN 
        + "(?:" + EXTENSION_PATTERN + ")?$"
    );

   public static final Pattern SPECIAL_CASE_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR_ONE 
        + "(?<date>\\d{5})" + SEPARATOR_ONE 
        + "(?<urn>\\d+)" + SEPARATOR_ONE 
        + "(?<exhibitRef>T\\d+)" + SEPARATOR_ONE 
        + NAMES_PATTERN + SEPARATOR_ONE 
        + VERSION_PATTERN 
        + "(?:_QC)?" 
        + "(?:" + EXTENSION_PATTERN + ")?$"
    );

    public static final Pattern MATCH_ALL_PATTERN = Pattern.compile(
        COURT_PATTERN + SEPARATOR_ONE 
        + DATE_PATTERN + SEPARATOR_ZERO 
        + "(?<urn>[A-Za-z0-9]+)" 
        + "(?:[-_\\s](?<urn2>[A-Za-z0-9]+))?" + SEPARATOR_ZERO 
        + EXHIBIT_PATTERN + SEPARATOR_ZERO 
        + NAMES_PATTERN +  SEPARATOR_ZERO
        + VERSION_PATTERN 
        + EXTENSION_PATTERN 
        + "$"
    );
}
