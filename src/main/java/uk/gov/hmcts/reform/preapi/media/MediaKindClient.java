package uk.gov.hmcts.reform.preapi.media;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;

public interface MediaKindClient {

    @RequestLine("GET /assets?%24skiptoken={skipToken}")
    @Headers({
        "accept: application/json",
        "x-mkio-token: {token}"
    })
    MkGetListResponse<MkAsset> getAssets(@Param("skipToken") int skipToken, @Param("token") String mkToken);

    @RequestLine("GET /assets/{assetName}")
    @Headers({
        "accept: application/json",
        "x-mkio-token: {token}"
    })
    MkAsset getAsset(@Param("assetName") String assetName, @Param("token") String mkToken);
}
