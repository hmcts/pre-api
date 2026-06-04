package uk.gov.hmcts.reform.preapi.entities;

public enum AppAccessCourtType {
    PRIMARY("Primary"),
    SECONDARY("Secondary");

    public final String accessType;

    AppAccessCourtType(final String accessType) {
        this.accessType = accessType;
    }
}
