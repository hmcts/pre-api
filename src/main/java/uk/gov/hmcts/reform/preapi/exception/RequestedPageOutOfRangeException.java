package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;

public class RequestedPageOutOfRangeException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1579941826346533851L;

    public RequestedPageOutOfRangeException(int requestedPage, int maxPage) {
        super("Requested page {" + requestedPage + "} is out of range. Max page is {" + maxPage + "}");
    }

    public RequestedPageOutOfRangeException(Exception exception) {
        super(exception);
    }
}
