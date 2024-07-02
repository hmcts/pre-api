package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Data;

@Data
public class MkErrorResponse {
    private MkError error;
    private String ref;
    private int status;

    @Data
    public static class MkError {
        private String code;
        private String detail;
    }
}
