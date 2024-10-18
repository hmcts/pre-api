package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkStorageAccount {

    private MkStorageAccountSpec spec;
    private MkStorageAccountStatus status;

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MkStorageAccountSpec {
        private String name;
        private String description;
        private String location;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MkStorageAccountStatus {
        private UUID activeCredentialId;
        private String privateLinkServiceConnectionStatus;
    }
}
