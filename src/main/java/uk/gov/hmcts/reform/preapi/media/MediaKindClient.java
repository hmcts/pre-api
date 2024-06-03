package uk.gov.hmcts.reform.preapi.media;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import uk.gov.hmcts.reform.preapi.config.MediaKindClientConfiguration;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;

@FeignClient(name = "mediaKindClient", url = "${mediakind.api}", configuration = MediaKindClientConfiguration.class)
public interface MediaKindClient {
    @GetMapping("/assets")
    MkGetListResponse<MkAsset> getAssets(@RequestParam("$skipToken") int skipToken);

    @GetMapping("/assets/{assetName}")
    MkAsset getAsset(@PathVariable("assetName") String assetName);
}
