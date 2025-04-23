package uk.gov.hmcts.reform.preapi.batch.config;

import lombok.experimental.UtilityClass;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.Set;

/**
 * Constants used throughout the batch processing.
 */
@UtilityClass
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public final class Constants {
    // Global  constants
    public static final LocalDate GO_LIVE_DATE = LocalDate.of(2019, 5, 23);
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final int MIN_RECORDING_DURATION = 10;
    public static final Set<String> VALID_VERSION_TYPES = Set.of("ORIG", "COPY", "CPY", "ORG", "ORI", "COP");
    public static final Set<String> VALID_ORIG_TYPES = Set.of("ORIG", "ORG", "ORI");
    public static final Set<String> VALID_COPY_TYPES = Set.of("COPY", "CPY", "COP");
    public static final Set<String> VALID_EXTENSIONS = Set.of("mp4");
    public static final DecimalFormat FILE_SIZE_FORMAT = new DecimalFormat("0.00");
    public static final String DEFAULT_NAME = "Unknown";

    // Test data keywords
    public static final Set<String> TEST_KEYWORDS = Set.of(
        "test", "demo", "unknown", "training", "t35t",
        "sample", "mock", "dummy", "example", "playback", "predefined",
        "fig_room", "failover", "viw", "support", "wrong", "rmx006",
        "rmx005", "recording", "rpms", "rmx-load", "snoc",
        "s28 rpcs room", "rpp1 user", "qc"
    );

    public static final class Environment {
        public static final String SOURCE_CONTAINER = "pre-vodafone-spike";
    }

    // Cache keys
    public static final class CacheKeys {
        public static final String NAMESPACE = "vf:";
        public static final String USERS_PREFIX = NAMESPACE + "user:";
        public static final String RECORDING_METADATA_FORMAT = NAMESPACE + "pre-process:%s-%s-%s-%s";
        public static final String CHANNEL_DATA = "channel_data";
    }

    // Validation error messages
    public static final class ErrorMessages {
        // Test related errors
        public static final String TEST_ITEM_NAME = "Test keywords in archive name";
        public static final String TEST_DURATION = "Duration is less than 10 seconds.";

        // File validation errors
        public static final String INVALID_FILE_EXTENSION = "Only .mp4 files are allowed.";

        // Court validation errors
        public static final String MISSING_COURT = "No valid court is associated with this recording.";

        // Version validation errors
        public static final String NOT_MOST_RECENT_VERSION = "The recording is not the most recent version.";
        public static final String NO_PARENT_FOUND = "No parent recording found in cache, but version > 1";

        // Case reference validation errors
        public static final String CASE_REFERENCE_TOO_LONG = "Case reference exceeds the 24-character limit.";
        public static final String CASE_REFERENCE_TOO_SHORT = "Case reference is less than 9-characters.";

        // Date validation errors
        public static final String PREDATES_GO_LIVE = "Recording date is before the go-live date ("
            + GO_LIVE_DATE.getDayOfMonth() + "/"
            + GO_LIVE_DATE.getMonthValue() + "/"
            + GO_LIVE_DATE.getYear() + ").";

        // Pattern match errors
        public static final String PATTERN_MATCH = "Failed to match any recording pattern.";
    }

    // Validation error messages
    public static final class Reports {
        public static final String FILE_MISSING_DATA = "Missing_Data";
        public static final String FILE_ERROR = "Error";
        public static final String FILE_INVALID_FORMAT = "Invalid_File_Format";
        public static final String FILE_NOT_RECENT = "Not_Most_Recent";
        public static final String FILE_PRE_GO_LIVE = "Pre_Go_Live";
        public static final String FILE_REGEX = "Regex_Matching_Errors";
    }

    public static final class XmlFields {
        public static final String DISPLAY_NAME = "Display Name";
        public static final String FILE_SIZE = "File Size";
        public static final String CREATE_TIME = "Create Time";
    }
}
