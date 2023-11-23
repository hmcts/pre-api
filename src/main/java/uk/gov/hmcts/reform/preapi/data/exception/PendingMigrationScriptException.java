package uk.gov.hmcts.reform.preapi.data.exception;

public class PendingMigrationScriptException extends RuntimeException {

    private static final long serialVersionUID = 123432123421232L;

    public PendingMigrationScriptException(String script) {
        super("Found migration not yet applied: " + script);
    }
}

