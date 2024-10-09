package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkStorageAccounts {
    List<MkStorageAccount> items;
}
