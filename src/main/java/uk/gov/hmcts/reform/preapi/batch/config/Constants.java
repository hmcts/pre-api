package uk.gov.hmcts.reform.preapi.batch.config;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.Set;

/**
 * Constants used throughout the batch processing.
 */
public final class Constants {
    private Constants() {
    }

    // Global  constants
    public static final LocalDate GO_LIVE_DATE = LocalDate.of(2019, 5, 23);
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
        "rmx005", "recording", "rpms", "rmx-load", "snoc morning check",
        "s28 rpcs room", "rpp1 user"
    );

    public static final class Environment {
        private Environment() {
        }

        public static final String SOURCE_ENVIRONMENT = "dev";
        public static final String SOURCE_CONTAINER = "pre-vodafone-spike";
        public static final String INGEST_CONTAINER_STG = "stagingIngest";
        public static final String RECORDING_INPUT_CONTAINER_SUFFIX = "-input";

    }

    // Redis cache keys
    public static final class RedisKeys {
        private RedisKeys() {
        }

        public static final String NAMESPACE = "vf:";
        public static final String COURTS_PREFIX = NAMESPACE + "court:";
        public static final String CASES_PREFIX = NAMESPACE + "case:";
        public static final String USERS_PREFIX = NAMESPACE + "user:";
        public static final String RECORDING_METADATA_FORMAT = NAMESPACE + "pre-process:%s-%s-%s";
        public static final String SITES_DATA = "sites_data";
        public static final String CHANNEL_DATA = "channel_data";

        public static String formatRecordingMetadataKey(String param1, String param2, String param3) {
            return String.format(RECORDING_METADATA_FORMAT, param1, param2, param3);
        }
    }

    // Validation error messages
    public static final class ErrorMessages {
        private ErrorMessages() {
        }

        // Test related errors
        public static final String TEST_ITEM_NAME =
            "Test keywords in archive name";
        public static final String TEST_DURATION =
            "Duration is less than 10 seconds.";

        // File validation errors
        public static final String INVALID_FILE_EXTENSION =
            "Only .mp4 files are allowed.";

        // Court validation errors
        public static final String MISSING_COURT =
            "No valid court is associated with this recording.";

        // Version validation errors
        public static final String NOT_MOST_RECENT_VERSION =
            "The recording is not the most recent version.";

        // Case reference validation errors
        public static final String MISSING_CASE_REFERENCE =
            "Missing or invalid case reference.";
        public static final String CASE_REFERENCE_TOO_LONG =
            "Case reference exceeds the 24-character limit.";

        // Archive validation errors
        public static final String INVALID_ARCHIVE_NAME =
            "Archive item or archive name cannot be null.";

        // Date validation errors
        public static final String PREDATES_GO_LIVE =
            "Recording date is before the go-live date ("
                + GO_LIVE_DATE.getDayOfMonth() + "/"
                + GO_LIVE_DATE.getMonthValue() + "/"
                + GO_LIVE_DATE.getYear() + ").";

        // Pattern match errors
        public static final String PATTERN_MATCH =
            "Failed to match any recording pattern.";

    }

    // Validation error messages
    public static final class Reports {
        private Reports() {
        }

        public static final String FILE_MISSING_DATA = "Missing_Data";
        public static final String FILE_ERROR = "Error";
        public static final String FILE_INVALID_FORMAT = "Invalid_File_Format";
        public static final String FILE_NOT_RECENT = "Not_Most_Recent";
        public static final String FILE_PRE_GO_LIVE = "Pre_Go_Live";
        public static final String FILE_TEST = "Test";
        public static final String FILE_REGEX = "Regex_Matching_Errors";
        

    }

}
