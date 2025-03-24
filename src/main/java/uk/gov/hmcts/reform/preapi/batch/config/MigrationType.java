package uk.gov.hmcts.reform.preapi.batch.config;

public enum MigrationType {
    FULL,
    DELTA;

    public static MigrationType fromString(String migrationType) {
        return MigrationType.valueOf(migrationType.toUpperCase());
    }
}
