package uk.gov.hmcts.reform.preapi.media.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MkGetListResponse<E> {
    private List<E> value;

    private Supplemental supplemental;

    @SerializedName("@odata.nextLink")
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
