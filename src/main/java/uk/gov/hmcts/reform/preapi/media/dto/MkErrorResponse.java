package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkErrorResponse {
    private MkError error;
    private String ref;
    private int status;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MkError {
        private String code;
        private String detail;
    }
}
