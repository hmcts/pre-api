package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;

public class UnauditableTableException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 6519941826346533854L;

    public UnauditableTableException(String className) {
        super("Unable to find @Table(name = \"table_name\") annotation in class: " + className);
    }

    public UnauditableTableException(Exception exception) {
        super(exception);
    }
}
