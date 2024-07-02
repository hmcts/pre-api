package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkLiveEvent {
    private String id;
    private String location;
    private String name;
    private Map<String, String> tags;
    private MkLiveEventProperties properties;
}
