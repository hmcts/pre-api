package uk.gov.hmcts.reform.preapi.media;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;

public interface MediaKindClient {

    @RequestLine("GET /assets")
    @Headers({
        "accept: application/json",
        "x-mkio-token: {token}"
    })
    MkGetListResponse<MkAsset> getAssets(@Param("token") String mkToken);
}
