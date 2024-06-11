package uk.gov.hmcts.reform.preapi.media;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import uk.gov.hmcts.reform.preapi.config.MediaKindClientConfiguration;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveEvent;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveOutput;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocator;

@FeignClient(name = "mediaKindClient", url = "${mediakind.api}", configuration = MediaKindClientConfiguration.class)
public interface MediaKindClient {
    @GetMapping("/assets")
    MkGetListResponse<MkAsset> getAssets(@RequestParam("$skipToken") int skipToken);

    @GetMapping("/assets/{assetName}")
    MkAsset getAsset(@PathVariable("assetName") String assetName);

    @PutMapping("/assets/{assetName}")
    MkAsset putAsset(@PathVariable("assetName") String assetName, @RequestBody MkAsset mkAsset);

    @PutMapping("/liveEvents/{liveEventName}")
    MkLiveEvent putLiveEvent(@PathVariable String liveEventName, @RequestBody MkLiveEvent mkLiveEvent);

    @GetMapping("/liveEvents/{liveEventName}")
    MkLiveEvent getLiveEvent(@PathVariable("liveEventName") String liveEventName);

    @PostMapping("/liveEvents/{liveEventName}/start")
    void startLiveEvent(@PathVariable String liveEventName);

    @PutMapping("/liveEvents/{liveEventName}/liveOutputs/{liveOutputName}")
    MkLiveOutput putLiveOutput(@PathVariable String liveEventName, @PathVariable String liveOutputName, @RequestBody MkLiveOutput mkLiveOutput);

    @PutMapping("/streamingLocators/{locatorName}")
    MkStreamingLocator putStreamingLocator(@PathVariable String locatorName, @RequestBody MkStreamingLocator mkStreamingLocator);
}
