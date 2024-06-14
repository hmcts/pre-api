package uk.gov.hmcts.reform.preapi.media;

import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.models.ListPathsResponseInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingEndpointInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingLocatorInner;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.exception.AMSLiveEventNotFoundException;
import uk.gov.hmcts.reform.preapi.exception.AMSLiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class AzureMediaService implements IMediaService {
    private final String resourceGroup;
    private final String accountName;
    private final AzureMediaServices amsClient;

    @Autowired
    public AzureMediaService(
        @Value("${azure.resource-group}") String resourceGroup,
        @Value("${azure.account-name}") String accountName,
        AzureMediaServices amsClient
    ) {
        this.resourceGroup = resourceGroup;
        this.accountName = accountName;
        this.amsClient = amsClient;
    }

    @Override
    public String playAsset(String assetId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String importAsset(String assetPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AssetDTO getAsset(String assetName) {
        // assetName is an id without the '-'
        try {
            return new AssetDTO(amsClient.getAssets().get(resourceGroup, accountName, assetName));
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new NotFoundException("Asset with name: " + assetName);
            }
            throw e;
        }
    }

    @Override
    public List<AssetDTO> getAssets() {
        return amsClient
            .getAssets()
            .list(resourceGroup, accountName)
            .stream()
            .map(AssetDTO::new)
            .toList();
    }

    @Override
    public String playLiveEvent(@NotNull UUID liveEventId) {
        assertLiveEventExists(liveEventId);
        String hostname;
        try {
            hostname = getStreamingEndpointHostname(liveEventId);
            amsClient.getStreamingEndpoints().start(resourceGroup, accountName, getShortenedLiveEventId(liveEventId));
        } catch (Exception ex) {
            Logger.getAnonymousLogger().info("Error creating streaming endpoint: " + ex.getMessage());
            throw ex;
        }

        try {
            assertStreamingLocatorExists(liveEventId);
        } catch (Exception e) {
            Logger.getAnonymousLogger().info("Error creating streaming locator: " + e.getMessage());
            if (e instanceof ManagementException managementException) {
                if (managementException.getResponse().getStatusCode() == 409) {
                    Logger.getAnonymousLogger().info("Streaming locator already exists");
                } else {
                    throw e;
                }
            } else {
                throw e;
            }
        }

        var paths = amsClient.getStreamingLocators()
                             .listPaths(resourceGroup, accountName, getSanitisedLiveEventId(liveEventId));

        return parseLiveOutputUrlFromStreamingLocatorPaths(hostname, paths);
    }

    private String getSanitisedLiveEventId(UUID liveEventId) {
        return liveEventId.toString().replace("-", "");
    }

    private String getShortenedLiveEventId(UUID liveEventId) {
        return getSanitisedLiveEventId(liveEventId).substring(0, 23);
    }

    private String getStreamingEndpointHostname(@NotNull UUID liveEventId) {
        Logger.getAnonymousLogger().info("creating streaming endpoint");
        try {
            return createStreamingEndpoint(liveEventId).hostname();
        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("Error creating streaming endpoint: " + e.getMessage());
            throw e;
        }
    }

    private StreamingEndpointInner createStreamingEndpoint(@NotNull UUID liveEventId) {
        var streamingEndpointName = getShortenedLiveEventId(liveEventId);
        try {
            return amsClient.getStreamingEndpoints()
                            .create(
                                resourceGroup,
                                accountName,
                                streamingEndpointName,
                                new StreamingEndpointInner()
                                    .withLocation("UK South")
                                    .withTags(Map.of(
                                        "environment", "Staging", // @TODO populate this
                                        "application", "pre-recorded evidence",
                                        "businessArea", "cross-cutting",
                                        "builtFrom", "azure portal"
                                    ))
                                    .withDescription(
                                        "Streaming Endpoint for " + streamingEndpointName
                                    )
                            );
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 409) {
                return amsClient.getStreamingEndpoints()
                                .get(resourceGroup, accountName, streamingEndpointName);
            }
            throw e;
        }
    }

    private void assertStreamingLocatorExists(UUID liveEventId) {

        try {
            Logger.getAnonymousLogger().info("Creating Streaming locator");
            var sanitisedLiveEventId = getSanitisedLiveEventId(liveEventId);
            var streamingLocatorProperties = new StreamingLocatorInner()
                .withAssetName(sanitisedLiveEventId)
                .withStreamingPolicyName("Predefined_ClearStreamingOnly")
                .withStreamingLocatorId(liveEventId);

            amsClient.getStreamingLocators().create(resourceGroup,
                                                    accountName,
                                                    sanitisedLiveEventId,
                                                    streamingLocatorProperties);
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 409) {
                Logger.getAnonymousLogger().info("Streaming locator already exists");
                return;
            }
            throw e;
        }
    }

    private void assertLiveEventExists(@NotNull UUID liveEventId) {
        var sanitisedLiveEventId = getSanitisedLiveEventId(liveEventId);
        try {
            var liveEvent = amsClient.getLiveEvents().get(resourceGroup, accountName, sanitisedLiveEventId);
            if (!liveEvent.resourceState().equals(LiveEventResourceState.RUNNING)) {
                throw new AMSLiveEventNotRunningException(sanitisedLiveEventId);
            }
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new AMSLiveEventNotFoundException(sanitisedLiveEventId);
            }
            throw e;
        }
    }

    private String parseLiveOutputUrlFromStreamingLocatorPaths(String streamingEndpointHostname,
                                                               @NotNull ListPathsResponseInner paths) {
        Logger.getAnonymousLogger().info("parsing live output url from streaming locator paths");
        Logger.getAnonymousLogger().info(streamingEndpointHostname);
        paths.streamingPaths().forEach(p -> Logger.getAnonymousLogger().info(p.paths().toString()));
        return paths.streamingPaths().stream()
            .flatMap(path -> path.paths().stream())
            .map(p -> "https://" + streamingEndpointHostname + p)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Unable to create streaming locator"));
    }


    /*
    @Override
    public String startLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }
    @Override
    public String stopLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }
    */

    @Override
    public LiveEventDTO getLiveEvent(String liveEventName) {
        try {
            return new LiveEventDTO(amsClient.getLiveEvents().get(resourceGroup, accountName, liveEventName));
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new NotFoundException("Live event: " + liveEventName);
            }
            throw e;
        }
    }

    @Override
    public List<LiveEventDTO> getLiveEvents() {
        return amsClient
            .getLiveEvents()
            .list(resourceGroup, accountName)
            .stream()
            .map(LiveEventDTO::new)
            .toList();
    }
}
