package uk.gov.hmcts.reform.preapi.batch.config;

public enum MigrationType {
    FIRST,
    SECOND;

    public static MigrationType fromString(String migrationType) {
        return MigrationType.valueOf(migrationType.toUpperCase());
    }
}
