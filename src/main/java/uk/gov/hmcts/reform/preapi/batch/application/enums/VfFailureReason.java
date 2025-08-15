package uk.gov.hmcts.reform.preapi.batch.application.enums;

public enum VfFailureReason {
    INCOMPLETE_DATA("Incomplete_Data"),
    INVALID_FORMAT("Invalid_Format"),
    NOT_MOST_RECENT("Not_Most_Recent"),
    RAW_FILES("Raw_Files"),
    PRE_GO_LIVE("Pre_Go_Live"),
    PRE_EXISTING("Pre_Existing"),
    VALIDATION_FAILED("Validation_Failed"),
    ALTERNATIVE_AVAILABLE("Alternative_Available"),
    GENERAL_ERROR("General_Error"),
    TEST("Test");

    private final String value;

    VfFailureReason(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
