package uk.gov.hmcts.reform.preapi.controllers.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StreamingLinkResponse {
    public String hls;
    public String dash;
}


