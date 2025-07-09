package uk.gov.hmcts.reform.preapi.media;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import uk.gov.hmcts.reform.preapi.config.MediaKindClientConfiguration;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkContentKeyPolicy;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;
import uk.gov.hmcts.reform.preapi.media.dto.MkJob;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveEvent;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveOutput;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingEndpoint;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocator;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorList;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorUrlPaths;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingPolicy;
import uk.gov.hmcts.reform.preapi.media.dto.MkTransform;

@FeignClient(name = "mediaKindClient", url = "${mediakind.api}", configuration = MediaKindClientConfiguration.class)
public interface MediaKindClient {
    @GetMapping("/assets")
    MkGetListResponse<MkAsset> getAssets(@RequestParam("$skiptoken") int skipToken);

    @GetMapping("/assets/{assetName}")
    MkAsset getAsset(@PathVariable("assetName") String assetName);

    @PutMapping("/assets/{assetName}")
    MkAsset putAsset(@PathVariable("assetName") String assetName, @RequestBody MkAsset mkAsset);

    @PutMapping("/liveEvents/{liveEventName}")
    MkLiveEvent putLiveEvent(@PathVariable String liveEventName, @RequestBody MkLiveEvent mkLiveEvent);

    @PostMapping("/assets/{assetName}/listStreamingLocators")
    MkStreamingLocatorList getAssetStreamingLocators(@PathVariable String assetName);

    @GetMapping("/streamingEndpoints/{endpointName}")
    MkStreamingEndpoint getStreamingEndpointByName(@PathVariable String endpointName);

    @PostMapping("/streamingLocators/{locatorName}/listPaths")
    MkStreamingLocatorUrlPaths getStreamingLocatorPaths(@PathVariable String locatorName);

    @PutMapping("/streamingEndpoints/{streamingEndpointName}")
    MkStreamingEndpoint createStreamingEndpoint(@PathVariable("streamingEndpointName") String streamingEndpointName,
                                                @RequestBody MkStreamingEndpoint streamingEndpoint);

    @PostMapping("/streamingEndpoints/{streamingEndpointName}/start")
    void startStreamingEndpoint(@PathVariable("streamingEndpointName") String streamingEndpointName);

    @PutMapping("/streamingLocators/{streamingLocatorName}")
    MkStreamingLocator createStreamingLocator(@PathVariable("streamingLocatorName") String streamingLocatorName,
                                              @RequestBody MkStreamingLocator streamingLocator);

    @GetMapping("/streamingLocators/{streamingLocatorName}")
    MkStreamingLocator getStreamingLocator(@PathVariable("streamingLocatorName") String streamingLocatorName);

    @GetMapping("/streamingLocators")
    MkGetListResponse<MkStreamingLocator> getStreamingLocators(@RequestParam("$skiptoken") int skipToken);

    @DeleteMapping("/streamingLocators/{streamingLocatorName}")
    void deleteStreamingLocator(@PathVariable("streamingLocatorName") String streamingLocatorName);

    @PostMapping("/streamingLocators/{streamingLocatorName}/listPaths")
    MkStreamingLocatorUrlPaths listStreamingLocatorPaths(
        @PathVariable("streamingLocatorName") String streamingLocatorName
    );

    @GetMapping("/liveEvents")
    MkGetListResponse<MkLiveEvent> getLiveEvents(@RequestParam("$skiptoken") int skipToken);

    @GetMapping("/liveEvents/{liveEventName}")
    MkLiveEvent getLiveEvent(@PathVariable("liveEventName") String liveEventName);

    @PostMapping("/liveEvents/{liveEventName}/start")
    void startLiveEvent(@PathVariable String liveEventName);

    @PostMapping("/liveEvents/{liveEventName}/stop")
    void stopLiveEvent(@PathVariable String liveEventName);

    @DeleteMapping("/liveEvents/{liveEventName}")
    void deleteLiveEvent(@PathVariable String liveEventName);

    @PutMapping("/liveEvents/{liveEventName}/liveOutputs/{liveOutputName}")
    MkLiveOutput putLiveOutput(@PathVariable String liveEventName,
                               @PathVariable String liveOutputName,
                               @RequestBody MkLiveOutput mkLiveOutput);

    @DeleteMapping("/liveEvents/{liveEventName}/liveOutputs/{liveOutputName}")
    void deleteLiveOutput(@PathVariable String liveEventName, @PathVariable String liveOutputName);

    @PutMapping("/streamingLocators/{locatorName}")
    MkStreamingLocator putStreamingLocator(@PathVariable String locatorName,
                                           @RequestBody MkStreamingLocator mkStreamingLocator);

    @PutMapping("/transforms/{transformName}/jobs/{jobName}")
    MkJob putJob(@PathVariable String transformName, @PathVariable String jobName, @RequestBody MkJob mkJob);

    @GetMapping("/transforms/{transformName}/jobs/{jobName}")
    MkJob getJob(@PathVariable String transformName, @PathVariable String jobName);

    @PostMapping("/streamingEndpoints/{endpointName}/stop")
    void stopStreamingEndpoint(@PathVariable String endpointName);

    @DeleteMapping("/streamingEndpoints/{endpointName}")
    void deleteStreamingEndpoint(@PathVariable String endpointName);

    @GetMapping("/transforms/{transformName}")
    MkTransform getTransform(@PathVariable String transformName);

    @PutMapping("/transforms/{transformName}")
    MkTransform putTransform(@PathVariable String transformName, @RequestBody MkTransform mkTransform);

    @GetMapping("/contentKeyPolicies/{policyName}")
    MkContentKeyPolicy getContentKeyPolicy(@PathVariable String policyName);

    @PutMapping("/contentKeyPolicies/{policyName}")
    void putContentKeyPolicy(@PathVariable String policyName, @RequestBody MkContentKeyPolicy mkContentPolicy);

    @GetMapping("/contentKeyPolicies")
    MkGetListResponse<MkContentKeyPolicy> getContentKeyPolicies(@RequestParam("$skiptoken") int skipToken);

    @DeleteMapping("/contentKeyPolicies/{policyName}")
    void deleteContentKeyPolicy(@PathVariable String policyName);

    @GetMapping("/streamingPolicies/{policyName}")
    MkStreamingPolicy getStreamingPolicy(@PathVariable String policyName);

    @PutMapping("/streamingPolicies/{policyName}")
    MkStreamingPolicy putStreamingPolicy(@PathVariable String policyName,
                                         @RequestBody MkStreamingPolicy mkStreamingPolicy);
}
