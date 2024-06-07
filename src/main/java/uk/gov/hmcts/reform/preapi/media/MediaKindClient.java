package uk.gov.hmcts.reform.preapi.media;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.reform.preapi.config.MediaKindClientConfiguration;
import uk.gov.hmcts.reform.preapi.media.dto.*;

@FeignClient(name = "mediaKindClient", url = "${mediakind.api}", configuration = MediaKindClientConfiguration.class)
public interface MediaKindClient {
    @GetMapping("/assets")
    MkGetListResponse<MkAsset> getAssets(@RequestParam("$skipToken") int skipToken);

    @GetMapping("/assets/{assetName}")
    MkAsset getAsset(@PathVariable("assetName") String assetName);

    @PostMapping("/assets/{assetName}/listStreamingLocators")
    MkStreamingLocatorList getAssetStreamingLocators(@PathVariable String assetName);

    @PutMapping("/streamingLocators/{locatorName}")
    void putStreamingLocator(@PathVariable String locatorName, @RequestBody MkCreateStreamingLocator dto);

    @GetMapping("/streamingEndpoints/{endpointName}")
    MkStreamingEndpoint getStreamingEndpointByName(@PathVariable String endpointName);

    @PostMapping("/streamingLocators/{locatorName}/listPaths")
    MkStreamingLocatorPaths getStreamingLocatorPaths(@PathVariable String locatorName);
}
