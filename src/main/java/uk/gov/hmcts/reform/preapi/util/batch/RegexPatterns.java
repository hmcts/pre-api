package uk.gov.hmcts.reform.preapi.util.batch;

import java.util.regex.Pattern;

public final class RegexPatterns {

    private RegexPatterns() {
    }

    private static final String SEPARATOR = "[-_\\s]+";
    private static final String OPTIONAL_PREFIX = "(?:\\d{1,5}[-_]?)?";
    private static final String DATE_PATTERN = "(?<date>\\d{6}|\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4})";
    private static final String COURT_PATTERN = "(?<court>[A-Za-z]+(?:d|fd)?)";
    private static final String URN_PATTERN = OPTIONAL_PREFIX + "(?<urn>\\d+(?:[A-Za-z]+\\d+)?)";
    private static final String OPTIONAL_SECOND_URN_PATTERN = "(?:[-_\\s]+(?<urn2>\\d+[A-Za-z]{1,2}\\d+))?";
    private static final String EXHIBIT_PATTERN = "(?<exhibitRef>(?:[A-Za-z]+\\d+|\\d{2}[A-Z]{2}\\d+|\\d+))?";
    private static final String VERSION_PATTERN = "(?<versionType>ORIG|COPY|CPY|ORG|ORI)(?:[-_\\s]*(?<versionNumber>\\d+(?:\\.\\d+)?))?";
    private static final String EXTENSION_PATTERN = "(?:\\.(?<ext>mp4|raw|RAW))?";
    private static final String NAMES_PATTERN = 
        "(?<defendantLastName>[A-Za-z]+(?:[-\\s][A-Za-z]+)*)" + 
        SEPARATOR + 
        "(?<witnessFirstName>[A-Za-z]+)";

    private static final String IGNORED_WORDS = "(?:QC|CP-Case|AS URN)";


    public static final Pattern STANDARD_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR +
        DATE_PATTERN + SEPARATOR +
        URN_PATTERN + SEPARATOR +
        "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR + ")?" +
        NAMES_PATTERN + SEPARATOR +
        VERSION_PATTERN +
        "(?:" + EXTENSION_PATTERN + ")?$"
    );

    public static final Pattern FLEXIBLE_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR +
        DATE_PATTERN + SEPARATOR +
        URN_PATTERN + 
        OPTIONAL_SECOND_URN_PATTERN + SEPARATOR + 
        "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR + ")?" +
        NAMES_PATTERN + SEPARATOR +
        VERSION_PATTERN +
        "(?:" + EXTENSION_PATTERN + ")?$"
    );

    public static final Pattern PREFIX_PATTERN = Pattern.compile(
        "^(?:(?:S28|NEW|QC)[_\\s-]+)?" +
        COURT_PATTERN + SEPARATOR +
        DATE_PATTERN + SEPARATOR +
        URN_PATTERN + SEPARATOR +
        "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR + ")?" +
        NAMES_PATTERN + SEPARATOR +
        VERSION_PATTERN +
        "(?:" + EXTENSION_PATTERN + ")?$"
    );

    public static final Pattern DOUBLE_URN_NO_EXHIBIT_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR +
        DATE_PATTERN + SEPARATOR +
        URN_PATTERN + SEPARATOR +
        "(?<urn2>\\d+[A-Za-z]{1,2}\\d+)" + SEPARATOR +  
        NAMES_PATTERN + SEPARATOR +
        VERSION_PATTERN +
        "(?:" + EXTENSION_PATTERN + ")?$"
    );

    public static final Pattern DOUBLE_EXHIBIT_NO_URN_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR +
        DATE_PATTERN + SEPARATOR +
        "(?:(?!" + IGNORED_WORDS + ")" + EXHIBIT_PATTERN + SEPARATOR + ")?" +
        "(?<exhibitRef2>(?:[TU]\\d+|\\d{2}[A-Z]{2}\\d+|\\d+))" + SEPARATOR +  
        NAMES_PATTERN + SEPARATOR +
        VERSION_PATTERN +
        "(?:" + EXTENSION_PATTERN + ")?$"
    );

   public static final Pattern SPECIAL_CASE_PATTERN = Pattern.compile(
        "^" + COURT_PATTERN + SEPARATOR +
        "(?<date>\\d{5})" + SEPARATOR +
        "(?<urn>\\d+)" + SEPARATOR +
        "(?<exhibitRef>T\\d+)" + SEPARATOR +
        NAMES_PATTERN + SEPARATOR +
        VERSION_PATTERN +
        "(?:_QC)?" +
        "(?:" + EXTENSION_PATTERN + ")?$"
    );
}
