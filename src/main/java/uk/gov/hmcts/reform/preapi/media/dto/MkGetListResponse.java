package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkGetListResponse<E> {
    private List<E> value;

    private Supplemental supplemental;

    @JsonProperty("@odata.nextLink")
    private String nextLink;

    @Data
    @Builder
    public static class Supplemental {
        private MkPagination pagination;
    }

    @Data
    @Builder
    public static class MkPagination {
        private int start;
        private int end;
        private int records;
        private int total;
    }
}
