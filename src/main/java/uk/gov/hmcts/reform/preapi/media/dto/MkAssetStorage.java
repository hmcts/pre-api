package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkAssetStorage {
    private MkAssetStorageMetadata metadata;
    private MkAssetStorageSpec spec;

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MkAssetStorageMetadata {
        private String id;
        private String name;
        private Date created;
        private String createdBy;
        private Date updated;
        private String updatedBy;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MkAssetStorageSpec {
        private String container;
        private String formatHint;

        // MK provides period info as dynamic object
        private JsonNode periods;
    }
}
