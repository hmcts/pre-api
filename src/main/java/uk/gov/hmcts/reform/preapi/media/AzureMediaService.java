package uk.gov.hmcts.reform.preapi.media;

import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.PlaybackDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.util.List;

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
    public PlaybackDTO playAsset(String assetId) {
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

    /*
    @Override
    public String startLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String playLiveEvent(String liveEventId) {
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
